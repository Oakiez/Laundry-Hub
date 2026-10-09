package com.laundryhub.service;

import com.laundryhub.domain.enums.*;
import com.laundryhub.dto.request.BookSessionRequest;
import com.laundryhub.dto.response.SessionResponse;
import com.laundryhub.exception.*;
import com.laundryhub.repository.MachineRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LAUNDRY_DB_TESTS", matches = "true")
class SessionLifecycleIntegrationTest {
    private static final String SCHEMA = "session_lifecycle_test_" + UUID.randomUUID().toString().replace("-", "");
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
            // Restricted to the generated schema owned by this test class.
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Autowired SessionService service;
    @Autowired MachineRepository machines;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    Long ownerId;
    Long machineId;
    LocalDateTime start;

    @BeforeEach
    void fixtures() {
        String suffix = UUID.randomUUID().toString();
        ownerId = jdbc.queryForObject("""
                INSERT INTO users(username,email,password,role) VALUES (?,?,'test','CUSTOMER') RETURNING id
                """, Long.class, suffix, suffix + "@example.test");
        Long branchId = jdbc.queryForObject("INSERT INTO branches(name) VALUES ('Lifecycle test branch') RETURNING id", Long.class);
        machineId = jdbc.queryForObject("""
                INSERT INTO machines(branch_id,name,machine_type,base_price,price_per_minute)
                VALUES (?,'Lifecycle washer','WASHER',20,1.50) RETURNING id
                """, Long.class, branchId);
        start = LocalDateTime.now().plusDays(1).withNano(0);
    }

    SessionResponse book(LocalDateTime time) {
        return service.book(machineId, ownerId, false, new BookSessionRequest(ownerId, time, 30));
    }

    String machineStatus() {
        return jdbc.queryForObject("SELECT status FROM machines WHERE id=?", String.class, machineId);
    }

    int notifications() {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=?", Integer.class, ownerId);
    }

    @Test void persistsPriceStatesAndNotificationsAcrossSeparateTransactions() {
        var reservation = book(start);
        assertEquals(65, reservation.amount().intValueExact());
        assertEquals(start.plusMinutes(30), reservation.endTime());
        assertEquals("AVAILABLE", machineStatus());
        assertNotNull(reservation.createdAt());
        assertEquals(SessionStatus.IN_USE, service.start(reservation.id(), ownerId, false).status());
        assertEquals("IN_USE", machineStatus());
        assertEquals(SessionStatus.COMPLETED, service.finish(reservation.id(), ownerId, false).status());
        assertEquals("AVAILABLE", machineStatus());
        assertEquals(3, notifications());
        assertEquals(SessionStatus.COMPLETED, service.findById(reservation.id(), ownerId, false).status());
        assertEquals(65, service.findPayable(reservation.id()).getPayableAmount().intValueExact());
    }

    @Test void cancellationOfFutureReservationDoesNotReleaseRunningMachine() {
        var running = book(start);
        var future = book(start.plusMinutes(30));
        service.start(running.id(), ownerId, false);
        service.cancel(future.id(), ownerId, false);
        assertEquals("IN_USE", machineStatus());
        assertEquals(SessionStatus.IN_USE, service.findById(running.id(), ownerId, false).status());
        assertThrows(BusinessRuleException.class, () -> service.cancel(running.id(), ownerId, false));
        assertEquals(4, notifications());
    }

    @Test void cancelledReservationCanBeBookedAgainAndNonOwnerCannotChangeIt() {
        var reservation = book(start);
        assertThrows(AccessDeniedException.class, () -> service.start(reservation.id(), ownerId + 10000, false));
        assertEquals("AVAILABLE", machineStatus());
        assertEquals(1, notifications());
        service.cancel(reservation.id(), ownerId, false);
        assertNotEquals(reservation.id(), book(start).id());
    }

    @Test void overlapFailsWithoutExtraRowsOrNotifications() {
        book(start);
        assertThrows(BookingConflictException.class, () -> book(start.plusMinutes(10)));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM usage_sessions WHERE machine_id=?", Integer.class, machineId));
        assertEquals(1, notifications());
    }

    @Test void notificationFailureRollsBackBothSessionAndMachine() {
        var reservation = book(start);
        // Force the synchronous notification listener to fail in this isolated schema.
        jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT test_block_notification CHECK (user_id <> " + ownerId + ") NOT VALID");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> service.start(reservation.id(), ownerId, false));
            assertEquals(SessionStatus.RESERVED, service.findById(reservation.id(), ownerId, false).status());
            assertEquals("AVAILABLE", machineStatus());
            assertEquals(1, notifications());
        } finally {
            jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT test_block_notification");
        }
    }

    // Hold the machine lock until PostgreSQL confirms BOTH requests are waiting.
    // This makes contention observable rather than relying on thread timing.
    List<Object> contend(Callable<SessionResponse> first, Callable<SessionResponse> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Object>> pending = new ArrayList<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                machines.findByIdForUpdate(machineId).orElseThrow();
                for (var operation : List.of(first, second)) {
                    pending.add(executor.submit(() -> {
                        try { return operation.call(); }
                        catch (RuntimeException error) { return error; }
                    }));
                }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
                int waiters = 0;
                while (System.nanoTime() < deadline) {
                    // PostgreSQL caches activity snapshots within a transaction.
                    jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    waiters = jdbc.queryForObject("""
                            SELECT count(*) FROM pg_stat_activity
                            WHERE wait_event_type='Lock' AND query LIKE ?
                            """, Integer.class, "%" + SCHEMA + ".machines%");
                    if (waiters >= 2) break;
                    try { Thread.sleep(20); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                }
                assertEquals(2, waiters, "Both service transactions must wait on the shared machine lock");
            });
            List<Object> outcomes = new ArrayList<>();
            for (var future : pending) outcomes.add(future.get(10, TimeUnit.SECONDS));
            return outcomes;
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test void simultaneousOverlappingBookingsCreateExactlyOneReservation() throws Exception {
        var outcomes = contend(() -> book(start), () -> book(start));
        assertEquals(1, outcomes.stream().filter(SessionResponse.class::isInstance).count());
        assertEquals(1, outcomes.stream().filter(BookingConflictException.class::isInstance).count());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM usage_sessions WHERE machine_id=?", Integer.class, machineId));
        assertEquals(1, notifications());
    }

    @Test void simultaneousStartsPermitOnlyOneRunningSession() throws Exception {
        var first = book(start);
        var second = book(start.plusMinutes(30));
        var outcomes = contend(() -> service.start(first.id(), ownerId, false),
                () -> service.start(second.id(), ownerId, false));
        assertEquals(1, outcomes.stream().filter(SessionResponse.class::isInstance).count());
        assertEquals(1, outcomes.stream().filter(BusinessRuleException.class::isInstance).count());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM usage_sessions WHERE machine_id=? AND status='IN_USE'", Integer.class, machineId));
        assertEquals("IN_USE", machineStatus());
        assertEquals(3, notifications());
    }
}
