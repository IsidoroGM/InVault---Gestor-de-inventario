package com.invault.inventory.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class GlobalExceptionHandlerLoggingTests {
    @Test
    void untrustedPathsCannotForgeLogLinesAndExceptionPayloadsStayPrivate() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var request = new MockHttpServletRequest("POST", "/api/products\r\nFAKE_LOGIN\u2028record");
            var response = new GlobalExceptionHandler().handleGenericException(
                    new IllegalStateException("sensitive-test-value\nforged-entry"), request);
            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .contains("IllegalStateException", "GlobalExceptionHandlerLoggingTests")
                    .doesNotContain("\r", "\n", "\u2028", "/api/products", "FAKE_LOGIN",
                            "sensitive-test-value", "forged-entry");
            assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
