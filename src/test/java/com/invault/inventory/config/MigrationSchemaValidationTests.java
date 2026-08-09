package com.invault.inventory.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:invault_schema_validation;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class MigrationSchemaValidationTests {

    @Test
    void flywaySchemaMatchesTheJpaModel() {
        // Context startup applies V1 and Hibernate validates every mapped entity.
    }
}
