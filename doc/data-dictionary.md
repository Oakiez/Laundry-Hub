# Data Dictionary — LaundryHub

ที่มา: `code/src/main/resources/db/migration/V1__init_schema.sql` · PostgreSQL 16 · Migration ด้วย Flyway
แผนภาพความสัมพันธ์ดูที่ [`diagrams/er-diagram.md`](diagrams/er-diagram.md)

คำย่อ: **PK** = Primary Key · **FK** = Foreign Key · **UQ** = Unique · **NN** = Not Null

## 1. users — บัญชีผู้ใช้
| คอลัมน์ | ชนิด | Constraint | ความหมาย |
|---|---|---|---|
| id | BIGINT identity | PK | รหัสผู้ใช้ |
| username | VARCHAR(50) | NN, UQ | ชื่อผู้ใช้สำหรับล็อกอิน |
| email | VARCHAR(100) | NN, UQ | อีเมล |
| password | VARCHAR(255) | NN | รหัสผ่านที่เข้ารหัส (BCrypt) |
| role | VARCHAR(20) | NN, CHECK ∈ {CUSTOMER, STAFF, ADMIN} | บทบาท |
| enabled | BOOLEAN | NN, default TRUE | เปิด/ปิดบัญชี |
| created_at | TIMESTAMP | NN, default now | เวลาสร้าง |

## 2. customer_profiles — ข้อมูลลูกค้า (1:1 กับ users)
| คอลัมน์ | ชนิด | Constraint | ความหมาย |
|---|---|---|---|
| id | BIGINT identity | PK | |
| user_id | BIGINT | NN, UQ, FK → users(id) **ON DELETE CASCADE** | เจ้าของโปรไฟล์ (UQ ทำให้เป็น 1:1) |
| full_name | VARCHAR(100) | NN | ชื่อ-นามสกุล |
| phone | VARCHAR(20) | | เบอร์โทร |
| address | VARCHAR(255) | | ที่อยู่ |

## 3. branches — สาขา
| คอลัมน์ | ชนิด | Constraint | ความหมาย |
|---|---|---|---|
| id | BIGINT identity | PK | |
| name | VARCHAR(100) | NN | ชื่อสาขา |
| address | VARCHAR(255) | | ที่อยู่ |
| phone | VARCHAR(20) | | เบอร์โทร |

## 4. service_types — ประเภทบริการฝากซัก
| คอลัมน์ | ชนิด | Constraint | ความหมาย |
|---|---|---|---|
| id | BIGINT identity | PK | |
| name | VARCHAR(100) | NN, UQ | ชื่อบริการ |
| price_per_kg | NUMERIC(10,2) | NN, CHECK ≥ 0 | ราคาต่อกิโลกรัม |
| express_surcharge | NUMERIC(10,2) | NN, default 0, CHECK ≥ 0 | ค่าบริการด่วน |
| active | BOOLEAN | NN, default TRUE | เปิดให้บริการอยู่หรือไม่ |

## 5. laundry_orders — ออเดอร์ฝากซัก
| คอลัมน์ | ชนิด | Constraint | ความหมาย |
|---|---|---|---|
| id | BIGINT identity | PK | |
| user_id | BIGINT | NN, FK → users(id) | ลูกค้าเจ้าของออเดอร์ |
| branch_id | BIGINT | NN, FK → branches(id) | สาขาที่รับ |
| status | VARCHAR(20) | NN, default RECEIVED, CHECK ∈ {RECEIVED, WASHING, DRYING, IRONING, READY, PICKED_UP, CANCELLED} | สถานะ |
| express | BOOLEAN | NN, default FALSE | ด่วนหรือไม่ |
| total_weight_kg | NUMERIC(8,2) | NN, default 0 | น้ำหนักรวม |
| total_amount | NUMERIC(10,2) | NN, default 0 | ยอดรวม (ใช้เป็น `Payable.getPayableAmount`) |
| note | VARCHAR(255) | | หมายเหตุ |
| created_at / updated_at | TIMESTAMP | NN, default now | เวลาสร้าง/แก้ไข |

## 6. laundry_order_items — รายการผ้าในออเดอร์
| คอลัมน์ | ชนิด | Constraint | ความหมาย |
|---|---|---|---|
| id | BIGINT identity | PK | |
| order_id | BIGINT | NN, FK → laundry_orders(id) **ON DELETE CASCADE** | ออเดอร์แม่ |
| service_type_id | BIGINT | NN, FK → service_types(id) | ประเภทบริการ |
| item_name | VARCHAR(100) | NN | ชื่อรายการ |
| weight_kg | NUMERIC(8,2) | NN, CHECK > 0 | น้ำหนัก |
| subtotal | NUMERIC(10,2) | NN, default 0 | ราคาย่อยของรายการ |

## 7. machines — เครื่องซัก/อบ
| คอลัมน์ | ชนิด | Constraint | ความหมาย |
|---|---|---|---|
| id | BIGINT identity | PK | |
| branch_id | BIGINT | NN, FK → branches(id) | สาขา |
| name | VARCHAR(50) | NN, UQ ร่วมกับ branch_id | ชื่อ/เลขเครื่อง (ไม่ซ้ำในสาขาเดียวกัน) |
| machine_type | VARCHAR(10) | NN, CHECK ∈ {WASHER, DRYER} | ซัก/อบ |
| status | VARCHAR(20) | NN, default AVAILABLE, CHECK ∈ {AVAILABLE, RESERVED, IN_USE, OUT_OF_SERVICE} | สถานะเครื่อง |
| base_price | NUMERIC(10,2) | NN, default 0, CHECK ≥ 0 | ราคาเริ่มต้น |
| price_per_minute | NUMERIC(8,2) | NN, default 0, CHECK ≥ 0 | ราคาต่อนาที |

## 8. usage_sessions — รอบใช้เครื่อง/การจอง
| คอลัมน์ | ชนิด | Constraint | ความหมาย |
|---|---|---|---|
| id | BIGINT identity | PK | |
| machine_id | BIGINT | NN, FK → machines(id) | เครื่องที่ใช้ |
| user_id | BIGINT | NN, FK → users(id) | ผู้จอง |
| status | VARCHAR(20) | NN, default RESERVED, CHECK ∈ {RESERVED, IN_USE, COMPLETED, CANCELLED} | สถานะ |
| start_time / end_time | TIMESTAMP | NN, CHECK end_time > start_time | ช่วงเวลา |
| duration_minutes | INT | NN, CHECK > 0 | ระยะเวลา (นาที) |
| amount | NUMERIC(10,2) | NN, default 0 | ยอดชำระ (ใช้เป็น `Payable.getPayableAmount`) |
| created_at | TIMESTAMP | NN, default now | เวลาสร้าง |

## 9. payments — การชำระเงิน (ของผู้เขียน)
| คอลัมน์ | ชนิด | Constraint | ความหมาย |
|---|---|---|---|
| id | BIGINT identity | PK | |
| order_id | BIGINT | UQ, FK → laundry_orders(id) | ออเดอร์ที่ชำระ (ถ้าเป็นออเดอร์) |
| session_id | BIGINT | UQ, FK → usage_sessions(id) | รอบใช้เครื่องที่ชำระ (ถ้าเป็นรอบใช้เครื่อง) |
| amount | NUMERIC(10,2) | NN, CHECK ≥ 0 | ยอดเงิน (มาจาก Payable ฝั่งเซิร์ฟเวอร์) |
| method | VARCHAR(20) | NN, CHECK ∈ {CASH, QR_MOCK, COIN} | วิธีชำระ |
| status | VARCHAR(20) | NN, default PENDING, CHECK ∈ {PENDING, PAID, FAILED} | สถานะ |
| paid_at | TIMESTAMP | | เวลารับเงิน (ว่างถ้ายังไม่จ่าย) |
| created_at | TIMESTAMP | NN, default now | เวลาสร้าง |

Constraint พิเศษ: `chk_payment_target` — ต้องมี `order_id` **หรือ** `session_id` อย่างใดอย่างหนึ่งเท่านั้น

## 10. notifications — แจ้งเตือนในระบบ (ของผู้เขียน)
| คอลัมน์ | ชนิด | Constraint | ความหมาย |
|---|---|---|---|
| id | BIGINT identity | PK | |
| user_id | BIGINT | NN, FK → users(id) **ON DELETE CASCADE** | ผู้รับ |
| message | VARCHAR(255) | NN | ข้อความ |
| is_read | BOOLEAN | NN, default FALSE | อ่านแล้วหรือยัง |
| created_at | TIMESTAMP | NN, default now | เวลาสร้าง |

---

## เหตุผลของ Index

| Index | ตาราง(คอลัมน์) | เหตุผล |
|---|---|---|
| idx_orders_user_status | laundry_orders(user_id, status) | หน้า "ออเดอร์ของฉัน" และบอร์ดพนักงานกรองด้วยผู้ใช้และสถานะบ่อยที่สุด composite ตรงกับเงื่อนไข `WHERE user_id=? AND status=?` |
| idx_orders_branch | laundry_orders(branch_id) | ดูออเดอร์ตามสาขา และ PostgreSQL ไม่สร้าง index ให้ FK อัตโนมัติ |
| idx_items_order | laundry_order_items(order_id) | ดึงรายการผ้าของออเดอร์ (join ทุกครั้งที่เปิดออเดอร์) |
| idx_machines_branch_status | machines(branch_id, status) | หาเครื่องว่างในสาขา (กรองด้วยสาขาและสถานะ) |
| idx_sessions_machine_time | usage_sessions(machine_id, start_time, end_time) | ตรวจเวลาจองซ้อนกัน (overlap check) ของเครื่องเดียวกัน ซึ่งเป็น query ที่ทำทุกครั้งตอนจอง |
| idx_sessions_user | usage_sessions(user_id) | ประวัติการใช้เครื่องของผู้ใช้ |
| idx_notifications_user_read | notifications(user_id, is_read) | กล่องแจ้งเตือนกรอง "ยังไม่อ่าน" ของผู้ใช้ และนับจำนวนที่ยังไม่อ่าน |

คอลัมน์ที่เป็น `UNIQUE` (`payments.order_id`, `payments.session_id`, `customer_profiles.user_id`, `users.username/email`) PostgreSQL สร้าง index ให้อัตโนมัติ

## เหตุผลของ Cascade

| ความสัมพันธ์ | พฤติกรรมเมื่อลบแม่ | เหตุผล |
|---|---|---|
| users → customer_profiles | **CASCADE** | โปรไฟล์เป็นส่วนหนึ่งของผู้ใช้ ไม่มีความหมายถ้าไม่มีผู้ใช้ |
| laundry_orders → laundry_order_items | **CASCADE** | รายการผ้าเป็นส่วนประกอบของออเดอร์ (composition) ลบออเดอร์ต้องลบรายการ |
| users → notifications | **CASCADE** | แจ้งเตือนเป็นข้อมูลชั่วคราวของผู้ใช้ |
| ความสัมพันธ์อื่นทั้งหมด (เช่น payments → orders/sessions, orders → users) | ไม่ cascade (ห้ามลบถ้ายังถูกอ้างอิง) | ข้อมูลการเงินและประวัติการใช้งานต้องคงอยู่ ถ้าเผลอลบออเดอร์ที่ชำระแล้ว DB จะปฏิเสธ แทนที่จะลบใบชำระเงินหายไปเงียบๆ |

ฝั่ง JPA ตั้ง `cascade = ALL` เฉพาะ `laundry_orders → items` ให้สอดคล้องกับ DB

## เหตุผลของ Fetch Type

- ใช้ **LAZY เป็นค่าเริ่มต้นทุกความสัมพันธ์** เพราะ `EAGER` ดึงข้อมูลที่ไม่ได้ใช้ทุกครั้งและก่อปัญหา N+1 โดยเฉพาะหน้ารายการแบบแบ่งหน้า
- `spring.jpa.open-in-view=false` จึงต้องดึงข้อมูลที่ต้องใช้ภายใน transaction ของ service และส่งออกเป็น DTO ผ่าน Mapper (ไม่ส่ง Entity ออกจาก service layer)
- `Payment` และ `Notification` เก็บ FK (`order_id`, `session_id`, `user_id`) เป็นค่า `Long` ไม่ผูก `@ManyToOne/@OneToOne` ไปยัง entity ของโมดูลอื่น เพื่อให้โมดูล Payment ไม่ขึ้นกับ `LaundryOrder`/`UsageSession` ตรงๆ (DIP) และไม่มีความเสี่ยงเรื่อง lazy loading ข้ามโมดูล ความสมบูรณ์เชิงอ้างอิงยังคงถูกบังคับโดย FK ในฐานข้อมูล
