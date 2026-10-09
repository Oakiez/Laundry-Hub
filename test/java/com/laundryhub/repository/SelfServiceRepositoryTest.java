package com.laundryhub.repository;

import com.laundryhub.domain.entity.Machine;
import com.laundryhub.domain.entity.UsageSession;
import com.laundryhub.domain.enums.MachineStatus;
import com.laundryhub.domain.enums.MachineType;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.SessionStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in PostgreSQL tests: every run uses its own schema, never public tables. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LAUNDRY_DB_TESTS", matches = "true")
class SelfServiceRepositoryTest {

    private static final String SCHEMA = "self_service_test_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv().getOrDefault(
            "TEST_DB_URL", "jdbc:postgresql://localhost:5433/laundryhub");
    private static final String USER = System.getenv().getOrDefault("TEST_DB_USER", "laundry");
    private static final String PASSWORD = System.getenv().getOrDefault("TEST_DB_PASSWORD", "laundry");
    private static final LocalDateTime BASE = LocalDateTime.of(2030, 1, 1, 10, 0);

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
        // SCHEMA is generated above, never taken from user input or app config.
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Autowired private MachineRepository machines;
    @Autowired private UsageSessionRepository sessions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private Long branchId;
    private Long userId;
    private Machine machine;

    @BeforeEach
    void setUp() {
        branchId = jdbc.queryForObject(
                "INSERT INTO branches(name) VALUES ('Repository test branch') RETURNING id", Long.class);
        userId = jdbc.queryForObject("""
                INSERT INTO users(username, email, password, role)
                VALUES ('repository_test', 'repository@example.test', 'test-only', 'CUSTOMER')
                RETURNING id
                """, Long.class);
        machine = saveMachine("Washer A", MachineType.WASHER);
    }

    private Machine saveMachine(String name, MachineType type) {
        var result = new Machine();
        result.setBranchId(branchId);
        result.setName(name);
        result.setMachineType(type);
        return machines.saveAndFlush(result);
    }

    private UsageSession saveSession(SessionStatus status) {
        var session = new UsageSession();
        session.setMachine(machine);
        session.setUserId(userId);
        session.setStatus(status);
        session.setStartTime(BASE);
        session.setEndTime(BASE.plusMinutes(60));
        session.setDurationMinutes(60);
        session.setAmount(new BigDecimal("65.00"));
        return sessions.saveAndFlush(session);
    }

    @ParameterizedTest
    @CsvSource({"-60,0,false", "60,120,false", "120,180,false",
            "-30,30,true", "30,90,true", "15,45,true", "-30,90,true", "0,60,true"})
    void overlapUsesStrictIntervalBoundaries(int start, int end, boolean expected) {
        saveSession(SessionStatus.RESERVED);
        assertEquals(expected, sessions.existsOverlap(
                machine.getId(), BASE.plusMinutes(start), BASE.plusMinutes(end)));
    }

    @ParameterizedTest
    @EnumSource(SessionStatus.class)
    void onlyReservedAndInUseSessionsBlockBookings(SessionStatus status) {
        saveSession(status);
        boolean expected = status == SessionStatus.RESERVED || status == SessionStatus.IN_USE;
        assertEquals(expected, sessions.existsOverlap(machine.getId(), BASE, BASE.plusMinutes(60)));
    }

    @Test
    void anotherMachineDoesNotBlockBooking() {
        saveSession(SessionStatus.RESERVED);
        var other = saveMachine("Washer B", MachineType.WASHER);
        assertFalse(sessions.existsOverlap(other.getId(), BASE, BASE.plusMinutes(60)));
    }

    @Test
    void emptyScheduleDoesNotBlockBooking() {
        assertFalse(sessions.existsOverlap(machine.getId(), BASE, BASE.plusMinutes(60)));
    }

    @Test
    void persistsPayableSessionAndFindsOwnerHistory() {
        Long id = saveSession(SessionStatus.RESERVED).getId();
        entityManager.clear();
        var loaded = sessions.findById(id).orElseThrow();
        assertEquals(userId, loaded.getOwnerUserId());
        assertEquals(PayableType.USAGE_SESSION, loaded.getPayableType());
        assertEquals(0, new BigDecimal("65.00").compareTo(loaded.getPayableAmount()));
        assertNotNull(loaded.getCreatedAt());
        assertEquals(machine.getId(), loaded.getMachine().getId());
        assertEquals(1, sessions.findByUserId(userId, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, sessions.findByUserId(-1L, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, sessions.findByMachine_Id(machine.getId(), PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void machineSearchSupportsFiltersPaginationAndSorting() {
        saveMachine("Dryer B", MachineType.DRYER);
        var page = machines.search(null, null, null,
                PageRequest.of(0, 1, Sort.by("name")));
        assertEquals(2, page.getTotalElements());
        assertEquals("Dryer B", page.getContent().get(0).getName());
        var filtered = machines.search(branchId, MachineStatus.AVAILABLE, MachineType.WASHER,
                PageRequest.of(0, 10));
        assertEquals(1, filtered.getTotalElements());
        assertEquals(machine.getId(), filtered.getContent().get(0).getId());
        assertEquals(0, machines.search(-1L, null, null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, machines.search(null, MachineStatus.OUT_OF_SERVICE, null,
                PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void duplicateNameChecksExcludeCurrentMachineDuringUpdates() {
        assertTrue(machines.existsByBranchIdAndName(branchId, "Washer A"));
        assertFalse(machines.existsByBranchIdAndNameAndIdNot(branchId, "Washer A", machine.getId()));
        var other = saveMachine("Washer B", MachineType.WASHER);
        assertTrue(machines.existsByBranchIdAndNameAndIdNot(branchId, "Washer A", other.getId()));
        assertFalse(machines.existsByBranchIdAndName(-1L, "Washer A"));
    }
}
