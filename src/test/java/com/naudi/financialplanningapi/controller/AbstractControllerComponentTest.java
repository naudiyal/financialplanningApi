package com.naudi.financialplanningapi.controller;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for controller component tests.
 *
 * Real: full Spring application context, all controllers, services, security filter chain,
 *       MVC routing, validation, JSON serialization, Flyway migrations, PostgreSQL.
 * Mocked: Google OAuth — simulated via oauth2Login() Spring Security test helpers.
 * External boundary replaced: PostgreSQL provided by Testcontainers; no live DB required.
 *
 * A single container is started once and reused across all subclasses within the same JVM,
 * relying on Spring's test context caching to avoid redundant startup overhead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
    "app.admin.email=admin@example.com"
})
abstract class AbstractControllerComponentTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
