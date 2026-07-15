package com.delivery.common;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Shared PostgreSQL Testcontainer configuration for repository integration tests. */
public final class PostgresTestContainerConfig {
    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("delivery_test")
            .withUsername("delivery")
            .withPassword("delivery");

    private PostgresTestContainerConfig() {
    }
}
