package com.invault.inventory.realtime;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import com.invault.inventory.batches.dto.BatchResponseDTO;

@SpringJUnitConfig(BatchChangedEventTransactionTests.TestConfiguration.class)
class BatchChangedEventTransactionTests {

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetMessages() {
        reset(messagingTemplate);
    }

    @Test
    void batchChangeIsSentOnlyAfterItsTransactionCommits() {
        InventoryEventDTO payload = batchPayload();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new BatchChangedEvent(payload));
            verifyNoInteractions(messagingTemplate);
        });

        verify(messagingTemplate).convertAndSend(
                InventoryEventWebSocketPublisher.INVENTORY_TOPIC, payload);
    }

    @Test
    void rolledBackBatchChangeIsNeverBroadcast() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new BatchChangedEvent(batchPayload()));
            status.setRollbackOnly();
        });

        verifyNoInteractions(messagingTemplate);
    }

    private InventoryEventDTO batchPayload() {
        BatchResponseDTO batch = new BatchResponseDTO();
        batch.setId(7L);
        batch.setProductId(1L);
        batch.setProductSku("RAW-001");
        batch.setBatchCode("LOT-001");
        batch.setQuantity(new BigDecimal("18.500"));
        return InventoryEventDTO.batchChanged(batch);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean(destroyMethod = "shutdown")
        EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .generateUniqueName(true)
                    .setType(EmbeddedDatabaseType.H2)
                    .build();
        }

        @Bean
        PlatformTransactionManager transactionManager(EmbeddedDatabase dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SimpMessagingTemplate messagingTemplate() {
            return mock(SimpMessagingTemplate.class);
        }

        @Bean
        InventoryEventWebSocketPublisher publisher(SimpMessagingTemplate messagingTemplate) {
            return new InventoryEventWebSocketPublisher(messagingTemplate);
        }
    }
}
