package org.openldes.ldio;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;

import java.time.Duration;

public class PostgresDbContainerExtension implements BeforeAllCallback, AfterAllCallback {
    private PostgreSQLContainer<?> postgresqlContainer;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        postgresqlContainer = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(5)));
        postgresqlContainer.start();

        System.setProperty("spring.datasource.url", postgresqlContainer.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgresqlContainer.getUsername());
        System.setProperty("spring.datasource.password", postgresqlContainer.getPassword());
        System.setProperty("spring.datasource.driver-class-name", postgresqlContainer.getDriverClassName());
    }


    @Override
    public void afterAll(ExtensionContext context) throws Exception {
    }
}
