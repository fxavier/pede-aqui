package com.delivery.common;

import org.testcontainers.containers.PostgreSQLContainer;

/** Shared PostgreSQL Testcontainer configuration for repository integration tests. */
public final class PostgresTestContainerConfig {
    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgis/postgis:16-3.4")
            .withDatabaseName("delivery_test")
            .withUsername("delivery")
            .withPassword("delivery");

    private PostgresTestContainerConfig() {
    }
}
