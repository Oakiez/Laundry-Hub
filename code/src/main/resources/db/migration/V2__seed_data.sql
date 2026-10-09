-- =====================================================================
-- LaundryHub — V2__seed_data.sql
-- ข้อมูลตัวอย่างสำหรับทดสอบ (ไม่ใช่รหัสผ่านจริงของใคร)
-- ทุก user ใช้รหัสผ่านตัวอย่างเดียวกัน: password123  (เก็บเป็น BCrypt hash)
-- =====================================================================

INSERT INTO users (username, email, password, role) VALUES
    ('admin',     'admin@laundryhub.local',     '$2a$10$Aqmwk0USAXW.q/1hlkxj8OPz8KgFTuotxr2wdPiyXGvau.jij7msK', 'ADMIN'),
    ('staff1',    'staff1@laundryhub.local',    '$2a$10$Aqmwk0USAXW.q/1hlkxj8OPz8KgFTuotxr2wdPiyXGvau.jij7msK', 'STAFF'),
    ('customer1', 'customer1@laundryhub.local', '$2a$10$Aqmwk0USAXW.q/1hlkxj8OPz8KgFTuotxr2wdPiyXGvau.jij7msK', 'CUSTOMER');

INSERT INTO customer_profiles (user_id, full_name, phone, address)
SELECT id, 'Customer One', '0812345678', '123 Sample Road, Khon Kaen'
FROM users WHERE username = 'customer1';

INSERT INTO branches (name, address, phone) VALUES
    ('LaundryHub สาขาหลัก',     '1 ถนนมิตรภาพ ขอนแก่น',   '043-000-001'),
    ('LaundryHub สาขามหาวิทยาลัย', '2 ถนนมหาวิทยาลัย ขอนแก่น', '043-000-002');

INSERT INTO service_types (name, price_per_kg, express_surcharge) VALUES
    ('ซักธรรมดา',     25.00, 10.00),
    ('ซักผ้าห่ม',     40.00, 15.00),
    ('รีดอย่างเดียว', 30.00, 10.00);

INSERT INTO machines (branch_id, name, machine_type, base_price, price_per_minute)
SELECT b.id, m.name, m.machine_type, m.base_price, m.price_per_minute
FROM (SELECT id FROM branches ORDER BY id LIMIT 1) b
CROSS JOIN (VALUES
    ('Washer 1', 'WASHER', 30.00, 1.00),
    ('Washer 2', 'WASHER', 30.00, 1.00),
    ('Dryer 1',  'DRYER',  20.00, 0.50),
    ('Dryer 2',  'DRYER',  20.00, 0.50)
) AS m (name, machine_type, base_price, price_per_minute);
