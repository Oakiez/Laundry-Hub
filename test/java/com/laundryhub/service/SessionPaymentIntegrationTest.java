package com.laundryhub.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring wiring, security, checkout, processors and PostgreSQL in an isolated schema. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "LAUNDRY_DB_TESTS", matches = "true")
class SessionPaymentIntegrationTest {
    private static final String SCHEMA = "session_payment_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://localhost:5433/laundryhub");
    private static final String USER = System.getenv().getOrDefault("TEST_DB_USER", "laundry");
    private static final String PASSWORD = System.getenv().getOrDefault("TEST_DB_PASSWORD", "laundry");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws SQLException {
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.datasource.hikari.schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    @AfterAll
    static void removeTestSchema() throws SQLException {
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
             var statement = connection.createStatement()) {
            // Only this generated test schema is removed, never public data.
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private Long ownerId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        String hash = encoder.encode("test-password");
        ownerId = jdbc.queryForObject("""
                INSERT INTO users(username,email,password,role)
                VALUES ('provider_customer','provider@example.test',?,'CUSTOMER') RETURNING id
                """, Long.class, hash);
        jdbc.update("""
                INSERT INTO users(username,email,password,role)
                VALUES ('provider_other','provider-other@example.test',?,'CUSTOMER')
                """, hash);
        Long branchId = jdbc.queryForObject("INSERT INTO branches(name) VALUES ('Provider test branch') RETURNING id", Long.class);
        Long machineId = jdbc.queryForObject("""
                INSERT INTO machines(branch_id,name,machine_type)
                VALUES (?,'Provider washer','WASHER') RETURNING id
                """, Long.class, branchId);
        sessionId = jdbc.queryForObject("""
                INSERT INTO usage_sessions(machine_id,user_id,start_time,end_time,duration_minutes,amount)
                VALUES (?,?,'2030-01-01 10:00','2030-01-01 10:30',30,40.00) RETURNING id
                """, Long.class, machineId, ownerId);
    }

    private String request(Long id, String method) {
        return "{\"payableType\":\"USAGE_SESSION\",\"payableId\":" + id + ",\"method\":\"" + method + "\"}";
    }

    @Test
    void missingSessionReturns404InsteadOfUnsupportedType() throws Exception {
        mvc.perform(post("/api/v1/payments").with(httpBasic("provider_customer", "test-password"))
                .contentType(MediaType.APPLICATION_JSON).content(request(Long.MAX_VALUE, "COIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Session " + Long.MAX_VALUE + " not found"));
    }

    @Test
    void coinCreatesPaidPaymentAndNotificationAndRejectsDuplicate() throws Exception {
        mvc.perform(post("/api/v1/payments").with(httpBasic("provider_customer", "test-password"))
                .contentType(MediaType.APPLICATION_JSON).content(request(sessionId, "COIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payableType").value("USAGE_SESSION"))
                .andExpect(jsonPath("$.payableId").value(sessionId.intValue()))
                .andExpect(jsonPath("$.amount").value(40.0))
                .andExpect(jsonPath("$.status").value("PAID"));
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM payments WHERE session_id=?", String.class, sessionId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=?", Integer.class, ownerId));
        mvc.perform(post("/api/v1/payments").with(httpBasic("provider_customer", "test-password"))
                .contentType(MediaType.APPLICATION_JSON).content(request(sessionId, "COIN")))
                .andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payments WHERE session_id=?", Integer.class, sessionId));
    }

    @Test
    void cashCreatesPendingPaymentWithoutCompletedNotification() throws Exception {
        mvc.perform(post("/api/v1/payments").with(httpBasic("provider_customer", "test-password"))
                .contentType(MediaType.APPLICATION_JSON).content(request(sessionId, "CASH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM payments WHERE session_id=?", String.class, sessionId));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=?", Integer.class, ownerId));
    }

    @Test
    void anotherCustomerCannotPayForOwnersSession() throws Exception {
        mvc.perform(post("/api/v1/payments").with(httpBasic("provider_other", "test-password"))
                .contentType(MediaType.APPLICATION_JSON).content(request(sessionId, "COIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM payments WHERE session_id=?", Integer.class, sessionId));
    }

    @Test
    void anonymousRequestReturns401WithoutPayment() throws Exception {
        mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON)
                .content(request(sessionId, "COIN")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM payments WHERE session_id=?", Integer.class, sessionId));
    }
}
