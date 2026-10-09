package com.laundryhub.repository;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.entity.Notification;
import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ทดสอบ Payment/Notification กับ PostgreSQL จริง: ยืนยันว่า entity ตรงกับตาราง และ constraint ของ DB
 * (FK, UNIQUE, CHECK chk_payment_target, CHECK amount >= 0) ทำงานตามที่ออกแบบ
 *
 * รันเฉพาะเมื่อตั้ง LAUNDRY_DB_TESTS=true (เหมือน SelfServiceRepositoryTest) แต่ละรอบสร้าง schema ชั่วคราวของตัวเอง
 * รัน Flyway ในนั้นแล้วลบทิ้ง จึงไม่แตะตารางจริง
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LAUNDRY_DB_TESTS", matches = "true")
class PaymentRepositoryDbTest {

    private static final String SCHEMA = "payment_test_" + UUID.randomUUID().toString().replace("-", "");
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
        // SCHEMA ถูกสร้างในคลาสนี้เอง ไม่ได้มาจาก input ของผู้ใช้
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Autowired private PaymentRepository payments;
    @Autowired private NotificationRepository notifications;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private Long userId;
    private Long orderId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        Long branchId = jdbc.queryForObject(
                "INSERT INTO branches(name) VALUES ('Payment test branch') RETURNING id", Long.class);
        userId = jdbc.queryForObject("""
                INSERT INTO users(username, email, password, role)
                VALUES ('payment_test', 'payment@example.test', 'test-only', 'CUSTOMER')
                RETURNING id
                """, Long.class);
        orderId = jdbc.queryForObject("""
                INSERT INTO laundry_orders(user_id, branch_id, total_weight_kg, total_amount)
                VALUES (?, ?, 2.00, 100.00) RETURNING id
                """, Long.class, userId, branchId);
        Long machineId = jdbc.queryForObject("""
                INSERT INTO machines(branch_id, name, machine_type) VALUES (?, 'Washer T', 'WASHER') RETURNING id
                """, Long.class, branchId);
        sessionId = jdbc.queryForObject("""
                INSERT INTO usage_sessions(machine_id, user_id, start_time, end_time, duration_minutes, amount)
                VALUES (?, ?, ?, ?, 30, 40.00) RETURNING id
                """, Long.class, machineId, userId, BASE, BASE.plusMinutes(30));
    }

    // ---------- Payment: การแมปกับตารางจริง ----------

    @Test
    void savePaymentForOrder_persistsAndReadsBackAllColumns() {
        Payment saved = payments.saveAndFlush(Payment.forPayable(
                payable(PayableType.LAUNDRY_ORDER, orderId, "100.00"), PaymentMethod.CASH));
        entityManager.clear(); // ล้างแคช ให้ findById อ่านจากฐานข้อมูลจริง

        Payment found = payments.findById(saved.getId()).orElseThrow();
        assertEquals(orderId, found.getOrderId());
        assertEquals(null, found.getSessionId());
        assertEquals(0, new BigDecimal("100.00").compareTo(found.getAmount()));
        assertEquals(PaymentMethod.CASH, found.getMethod());
        assertEquals(PaymentStatus.PENDING, found.getStatus());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    void savePaymentForSession_andMarkPaid_persistsStatusAndPaidAt() {
        Payment payment = Payment.forPayable(
                payable(PayableType.USAGE_SESSION, sessionId, "40.00"), PaymentMethod.QR_MOCK);
        payment.markPaid();
        Payment saved = payments.saveAndFlush(payment);
        entityManager.clear(); // ล้างแคช ให้ findById อ่านจากฐานข้อมูลจริง

        Payment found = payments.findById(saved.getId()).orElseThrow();
        assertEquals(sessionId, found.getSessionId());
        assertEquals(PaymentStatus.PAID, found.getStatus());
        assertNotNull(found.getPaidAt());
    }

    @Test
    void existsByOrderIdAndSessionId_reflectStoredPayments() {
        assertFalse(payments.existsByOrderId(orderId));
        payments.saveAndFlush(Payment.forPayable(
                payable(PayableType.LAUNDRY_ORDER, orderId, "100.00"), PaymentMethod.CASH));

        assertTrue(payments.existsByOrderId(orderId));
        assertFalse(payments.existsBySessionId(sessionId));
    }

    @Test
    void findByStatus_filtersAndPages() {
        payments.saveAndFlush(Payment.forPayable(
                payable(PayableType.LAUNDRY_ORDER, orderId, "100.00"), PaymentMethod.CASH));
        Payment paid = Payment.forPayable(
                payable(PayableType.USAGE_SESSION, sessionId, "40.00"), PaymentMethod.QR_MOCK);
        paid.markPaid();
        payments.saveAndFlush(paid);

        Page<Payment> pending = payments.findByStatus(PaymentStatus.PENDING,
                PageRequest.of(0, 10, Sort.by("createdAt").descending()));

        assertEquals(1, pending.getTotalElements());
        assertEquals(PaymentStatus.PENDING, pending.getContent().get(0).getStatus());
    }

    // ---------- Payment: constraint ของ DB (คำถามข้อ 5 ของ brief) ----------

    @Test
    void secondPaymentForSameOrder_isRejectedByUniqueConstraint() {
        payments.saveAndFlush(Payment.forPayable(
                payable(PayableType.LAUNDRY_ORDER, orderId, "100.00"), PaymentMethod.CASH));

        assertThrows(DataIntegrityViolationException.class, () -> payments.saveAndFlush(Payment.forPayable(
                payable(PayableType.LAUNDRY_ORDER, orderId, "100.00"), PaymentMethod.QR_MOCK)));
    }

    @Test
    void secondPaymentForSameSession_isRejectedByUniqueConstraint() {
        payments.saveAndFlush(Payment.forPayable(
                payable(PayableType.USAGE_SESSION, sessionId, "40.00"), PaymentMethod.CASH));

        assertThrows(DataIntegrityViolationException.class, () -> payments.saveAndFlush(Payment.forPayable(
                payable(PayableType.USAGE_SESSION, sessionId, "40.00"), PaymentMethod.COIN)));
    }

    @Test
    void paymentForNonexistentOrder_isRejectedByForeignKey() {
        assertThrows(DataIntegrityViolationException.class, () -> payments.saveAndFlush(Payment.forPayable(
                payable(PayableType.LAUNDRY_ORDER, 987654321L, "100.00"), PaymentMethod.CASH)));
    }

    @Test
    void paymentLinkedToBothOrderAndSession_isRejectedByCheckConstraint() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO payments(order_id, session_id, amount, method) VALUES (?, ?, 10.00, 'CASH')",
                orderId, sessionId));
    }

    @Test
    void paymentLinkedToNeither_isRejectedByCheckConstraint() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO payments(order_id, session_id, amount, method) VALUES (NULL, NULL, 10.00, 'CASH')"));
    }

    @Test
    void negativeAmount_isRejectedByCheckConstraint() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO payments(order_id, amount, method) VALUES (?, -1.00, 'CASH')", orderId));
    }

    @Test
    void unknownMethod_isRejectedByCheckConstraint() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO payments(order_id, amount, method) VALUES (?, 10.00, 'BITCOIN')", orderId));
    }

    // ---------- Notification ----------

    @Test
    void saveNotification_persistsAndFiltersByReadFlag() {
        notifications.saveAndFlush(new Notification(userId, "ออเดอร์ #1 สถานะ: WASHING"));
        Notification read = new Notification(userId, "ชำระเงินสำเร็จ");
        read.markRead();
        notifications.saveAndFlush(read);

        PageRequest firstPage = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<Notification> all = notifications.findByUserId(userId, firstPage);
        Page<Notification> unread = notifications.findByUserIdAndRead(userId, false, firstPage);

        assertEquals(2, all.getTotalElements());
        assertEquals(1, unread.getTotalElements());
        assertFalse(unread.getContent().get(0).isRead());
        assertNotNull(unread.getContent().get(0).getCreatedAt());
    }

    @Test
    void notificationForNonexistentUser_isRejectedByForeignKey() {
        assertThrows(DataIntegrityViolationException.class,
                () -> notifications.saveAndFlush(new Notification(987654321L, "ไม่มีผู้ใช้")));
    }

    private static Payable payable(PayableType type, Long id, String amount) {
        return new Payable() {
            public Long getId() { return id; }
            public BigDecimal getPayableAmount() { return new BigDecimal(amount); }
            public PayableType getPayableType() { return type; }
            public Long getOwnerUserId() { return 1L; }
        };
    }
}
