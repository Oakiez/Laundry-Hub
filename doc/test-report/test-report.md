# Test Report — LaundryHub

ผู้รับผิดชอบ: ภีมเดช กลั่นกิ่ง (โชกุน) · Test Lead
ขอบเขตของฉบับนี้: โมดูล Payment, Notification และ API Quality (Exception handling) ซึ่งเป็นส่วนของผู้เขียน
ผลรวมทั้งโปรเจคดูหัวข้อ [5. ผลรวมทั้งโปรเจค](#5-ผลรวมทั้งโปรเจค)

## 1. สภาพแวดล้อมและวิธีรัน

| รายการ | ค่า |
|---|---|
| วันที่รันล่าสุด | 9 ต.ค. 2569 (2026-10-09) 00:40 น. |
| Java | Temurin 17.0.20.1 |
| Maven | 3.10.0 |
| Spring Boot | 3.5.7 |
| Test framework | JUnit 5 + Mockito + Spring Test (MockMvc) ผ่าน `spring-boot-starter-test` |
| ตำแหน่งโค้ดเทสต์ | `test/java/` (ตั้ง `testSourceDirectory` ใน `code/pom.xml`) |

```bash
cd code
mvn test                                        # รันทั้งหมด
mvn test -Dtest=GlobalExceptionHandlerTest      # รันคลาสเดียว
```

ภาพหน้าจอผลรัน (`BUILD SUCCESS`, `Tests run: 57, Failures: 0, Errors: 0`):

![ผลรัน mvn test ของโมดูล Payment, Notification และ API Quality](../../img/test-run-payment.png)

## 2. สรุปผล (โมดูลของผู้เขียน)

| โมดูล | คลาสเทสต์ | จำนวน | ผ่าน | ล้มเหลว |
|---|---|---:|---:|---:|
| API Quality | `GlobalExceptionHandlerTest` | 12 | 12 | 0 |
| Payment (entity) | `PaymentTest` | 4 | 4 | 0 |
| Payment (strategy) | `CashProcessorTest` | 2 | 2 | 0 |
| Payment (strategy) | `QrMockProcessorTest` | 2 | 2 | 0 |
| Payment (factory) | `PaymentProcessorFactoryTest` | 4 | 4 | 0 |
| Payment (service) | `PaymentServiceImplTest` | 9 | 9 | 0 |
| Payment (facade) | `CheckoutFacadeTest` | 10 | 10 | 0 |
| Notification (service) | `NotificationServiceImplTest` | 10 | 10 | 0 |
| Notification (observer) | `NotificationEventListenerTest` | 4 | 4 | 0 |
| **รวม** | **9 คลาส** | **57** | **57** | **0** |

ผลจาก Maven: `Tests run: 57, Failures: 0, Errors: 0, Skipped: 0` · `BUILD SUCCESS`

## 3. รายละเอียดกรณีทดสอบ

### 3.1 API Quality — `GlobalExceptionHandlerTest` (12)
ใช้ `MockMvcBuilders.standaloneSetup(...).setControllerAdvice(...)` ไม่ต้องรัน Spring ทั้งตัว ตรวจ status และรูปแบบ JSON (`ApiErrorResponse`)

| กรณี | คาดหวัง |
|---|---|
| `ResourceNotFoundException` | 404 พร้อม `status`, `error`, `message`, `path`, `fieldErrors` ว่าง |
| `BusinessRuleException` | 400 |
| `DuplicateResourceException` | 409 |
| `BookingConflictException` | 409 |
| `DataIntegrityViolationException` | 409 และ**ไม่ส่งข้อความ/ชื่อ constraint ของ DB** ออกไป |
| `AccessDeniedException` | 403 |
| `Exception` ทั่วไป | 500 และ**ไม่ส่งข้อความภายใน** ออกไป |
| `@Valid` ไม่ผ่าน | 400 พร้อม `fieldErrors[0].field` |
| JSON เสียรูป | 400 |
| HTTP method ผิด | 405 พร้อม body `ApiErrorResponse` |
| ขาด query parameter ที่จำเป็น | 400 |
| Content-Type ไม่รองรับ | 415 |

### 3.2 Payment
**`PaymentTest` (4)** — `forPayable` ตั้ง `orderId` อย่างเดียวและดึงยอดจาก Payable · ตั้ง `sessionId` อย่างเดียวสำหรับรอบใช้เครื่อง · `markPaid` ตั้งสถานะและเวลา · `markPaid` เรียกซ้ำไม่เขียนทับ `paidAt`

**`CashProcessorTest` (2)** / **`QrMockProcessorTest` (2)** — `method()` ถูกต้อง · เงินสดคืน `PENDING` · QR จำลองคืน `PAID`

**`PaymentProcessorFactoryTest` (4)** — คืน processor ถูกตัวตาม method · method ที่ไม่รองรับ (`COIN`) → `BusinessRuleException` · `null` → `BusinessRuleException` · processor ซ้ำ method เดียวกัน → ล้มตั้งแต่สร้าง (fail fast)

**`PaymentServiceImplTest` (9)** — QR → `PAID` ทันทีพร้อม `paidAt` และยอดจาก Payable · เงินสด → `PENDING` · จ่ายซ้ำ (order) → `DuplicateResourceException` และไม่เรียก `save` · จ่ายซ้ำ (session) ตรวจด้วย `sessionId` · วิธีชำระไม่รองรับ → `BusinessRuleException` · `confirm` จาก `PENDING` → `PAID` · `confirm` ที่ `PAID` แล้ว → `DuplicateResourceException` · `confirm` ที่ `FAILED` → `BusinessRuleException` · ไม่พบ payment → `ResourceNotFoundException`

**`CheckoutFacadeTest` (10)** (Mockito: provider/service/publisher เป็น mock)

| กรณี | คาดหวัง |
|---|---|
| ชำระสำเร็จ (`PAID`) | ยิง `PaymentCompletedEvent` ถูกต้อง (userId เจ้าของ, ชนิด, refId) |
| ค้าง `PENDING` (เงินสด) | **ไม่ยิง** event |
| ผู้เรียกไม่ใช่เจ้าของ | `AccessDeniedException` และไม่สร้าง payment |
| ผู้เรียกเป็นพนักงาน | ชำระแทนผู้อื่นได้ |
| ชำระซ้ำ | `DuplicateResourceException` และไม่ยิง event |
| ไม่มี provider ของชนิดนั้น | `BusinessRuleException` |
| ยืนยันรับเงิน | ยิง event ไปยังเจ้าของ |
| ดู payment (เจ้าของ / ไม่ใช่เจ้าของ / พนักงาน) | ผ่าน / `AccessDeniedException` / ผ่านโดยไม่ต้องค้นหาเจ้าของ |

### 3.3 Notification
**`NotificationServiceImplTest` (10)** — สร้างแจ้งเตือนสถานะยังไม่อ่าน · ตัดข้อความที่ยาวเกิน 255 · `userId` null และข้อความ null/ว่าง → `BusinessRuleException` และไม่ `save` · กรอง `unread` ทั้ง 3 แบบ (null/true/false) · `markRead` โดยเจ้าของ · ไม่ใช่เจ้าของ → `AccessDeniedException` และไม่ `save` · ไม่พบ → `ResourceNotFoundException`

**`NotificationEventListenerTest` (4)** — Observer เรียก `notificationService.notifyUser` ด้วยข้อความที่ถูกต้องสำหรับ `OrderStatusChangedEvent`, `SessionStatusChangedEvent` และ `PaymentCompletedEvent` (ออเดอร์/รอบใช้เครื่อง)

### 3.4 ทดสอบกับแอปจริง (manual end-to-end)

รันแอปจริงด้วย `docker compose up -d db` (PostgreSQL 16 พอร์ต 5433) และ `mvn spring-boot:run` เมื่อ 9 ต.ค. 2569 แล้วทดสอบด้วย `curl` และ Swagger UI โดยใช้ผู้ใช้ตัวอย่างจาก `V2__seed_data.sql`

**การเริ่มระบบ:** Flyway ใช้ migration V1/V2 สำเร็จ · Hibernate `ddl-auto: validate` ผ่าน (entity ของ `Payment`/`Notification` ตรงกับตารางจริงทุกคอลัมน์) · `Started LaundryHubApplication` โดยไม่มี error

| # | กรณี | ผู้ใช้ | ผลที่ได้ | ผลที่คาดหวัง |
|---|---|---|---:|---|
| 1 | `GET /api/v1/payments` | ไม่ล็อกอิน | 401 | ผ่าน |
| 2 | `GET /api/v1/payments` | CUSTOMER | 403 + `ApiErrorResponse` | ผ่าน (`@PreAuthorize`) |
| 3 | `GET /api/v1/payments` | STAFF | 200 + `content`/`page` | ผ่าน |
| 4 | `GET /api/v1/payments?status=NOPE` | STAFF | 400 | ผ่าน |
| 5 | `GET /api/v1/payments?sort=abc` | STAFF | 400 (ก่อนแก้ได้ 500) | ผ่านหลังแก้ ดูข้อบกพร่องข้อ 6 |
| 6 | `GET /api/v1/payments?sort=password` | STAFF | 400 | ผ่าน (ไม่เปิดให้เรียงด้วยฟิลด์ที่ไม่อนุญาต) |
| 7 | `GET /api/v1/users/3/notifications` | CUSTOMER เจ้าของ | 200 | ผ่าน |
| 8 | `GET /api/v1/users/2/notifications` | CUSTOMER (ของคนอื่น) | 403 | ผ่าน |
| 9 | `GET /api/v1/users/3/notifications?unread=true` | ADMIN | 200 | ผ่าน |
| 10 | `PATCH /api/v1/notifications/9999/read` | CUSTOMER | 404 | ผ่าน |
| 11 | `POST /api/v1/payments` body ว่าง | CUSTOMER | 400 + `fieldErrors` 3 รายการ | ผ่าน |
| 12 | `POST /api/v1/payments` (ออเดอร์ที่ไม่มี) | CUSTOMER | 404 `Order 1 not found` | ผ่าน (Facade ทำงานต่อกับ `PayableProvider` ของโมดูลออเดอร์) |
| 13 | `PATCH /api/v1/payments/1/confirm` | CUSTOMER | 403 | ผ่าน |
| 14 | `PATCH /api/v1/payments/9999/confirm` | STAFF | 404 | ผ่าน |

error ทุกแบบ (ยกเว้น 401 จากชั้น Security) ตอบเป็นรูปแบบ `ApiErrorResponse` เดียวกัน

ภาพจาก Swagger UI (พารามิเตอร์แบ่งหน้า `page`/`size`/`sort` แยกช่อง และผล 200):

![Swagger UI: GET /api/v1/payments ผล 200](../../img/swagger-payments-list-200.png)

![Swagger UI: โครงสร้างตัวอย่างของ PaymentResponse](../../img/swagger-payments-response-schema.png)

## 4. ข้อบกพร่องที่พบระหว่างทดสอบและรีวิว (และแก้แล้ว)

**ข้อ 1 — เทสต์ `GlobalExceptionHandlerTest` ล้ม 8 ข้อ**
- ปัญหา: `@RestControllerAdvice(basePackages=...)` จำกัดขอบเขตผิด handler จึงไม่ถูกเรียกกับ controller ที่อยู่นอก package
- แก้ไข: เปลี่ยนตัวกรองขอบเขต (commit `fix`)

**ข้อ 2 — Code Review ของปอนด์ (PR #3)**
- ปัญหา: handler ของ `Exception` กลืน error ของ Spring MVC ทำให้ 405/400/415 กลายเป็น 500
- แก้ไข: เพิ่ม handler เฉพาะ 3 ชนิด พร้อมเทสต์

**ข้อ 3 — เทสต์ที่เพิ่มจากข้อ 2 ล้ม 1 ข้อ**
- ปัญหา: 405 เกิดก่อน Spring เลือก controller (handler เป็น null) ตัวกรอง advice แบบกำหนดเงื่อนไขจึงไม่ทำงาน และ body ไม่ใช่ `ApiErrorResponse`
- แก้ไข: เอาตัวกรองออกให้ advice ครอบทุก controller

**ข้อ 4 — Code Review ของปอนด์ (PR #3)**
- ปัญหา: `markPaid()` เรียกซ้ำแล้วเขียนทับ `paidAt`
- แก้ไข: ทำให้ idempotent พร้อมเทสต์

**ข้อ 5 — Code Review ของปอนด์ (PR #6)**
- ปัญหา: `notifyUser` ไม่ตรวจ `userId` และข้อความที่เป็น null
- แก้ไข: ตรวจแล้วโยน `BusinessRuleException` พร้อมเทสต์

**ข้อ 6 — Code Review ของปอนด์ (PR #10) และยืนยันด้วยการทดสอบจริง**
- ปัญหา: `GET /api/v1/payments?sort=abc` (ฟิลด์ที่ไม่มี) ได้ **500** แทนที่จะเป็น 400 เพราะ error ของ Spring Data หลุดไปถึง handler สุดท้าย
- แก้ไข: เพิ่ม `PageableValidator` ตรวจฟิลด์ที่อนุญาตให้เรียง (whitelist) ใน `PaymentApiController` และ `NotificationApiController` ตอบ 400 พร้อมบอกฟิลด์ที่ใช้ได้ มีเทสต์ 7 ข้อ และยืนยันซ้ำกับแอปจริง

**ข้อ 7 — พบจากการดู Swagger ระหว่างทดสอบจริง**
- ปัญหา: พารามิเตอร์แบ่งหน้าของ payments/notifications แสดงใน Swagger เป็นช่อง `pageable` ก้อนเดียว (JSON) ทดลองใช้งานไม่สะดวก ขณะที่ endpoint ออเดอร์แสดง `page`/`size`/`sort` แยกช่อง
- แก้ไข: เพิ่ม `@ParameterObject` ที่พารามิเตอร์ `Pageable`

## 5. ผลรวมทั้งโปรเจค

> ต้องอัปเดตตารางนี้หลังรวมงานของทุกคนเข้า `develop` และรัน `mvn test` ครั้งสุดท้ายก่อนส่ง

| โมดูล | ผู้รับผิดชอบ | จำนวนเทสต์ | ผ่าน | ล้มเหลว |
|---|---|---:|---:|---:|
| Payment / Notification / API Quality | โชกุน | 57 | 57 | 0 |
| Auth / User / Branch | โอ๊ค | (กรอก) | (กรอก) | (กรอก) |
| Full-Service Order | พีช | (กรอก) | (กรอก) | (กรอก) |
| Self-Service Machine | ปอนด์ | (กรอก) | (กรอก) | (กรอก) |
| **รวม** | | (กรอก) | (กรอก) | (กรอก) |

## 6. ข้อจำกัดและสิ่งที่ยังไม่ได้ทดสอบ

- **ยังไม่มีเทสต์อัตโนมัติระดับ Integration กับฐานข้อมูลจริง** เทสต์อัตโนมัติทั้งหมดเป็น Unit Test (Mockito / MockMvc standalone) การยืนยันกับ PostgreSQL ทำแบบ manual ในหัวข้อ 3.4 เท่านั้น (ชื่อคอลัมน์ผ่าน `validate`) แต่ **ยังไม่ได้ทดสอบ constraint ของฐานข้อมูล** (`UNIQUE` ของ `order_id`/`session_id`, `CHECK chk_payment_target`) กับการบันทึก payment จริง เพราะยังไม่มีออเดอร์/รอบใช้เครื่องตัวอย่างให้ชำระ
- **สิทธิ์ (`@PreAuthorize`) ยืนยันแบบ manual แล้ว** (หัวข้อ 3.4) แต่ยังไม่มีเทสต์อัตโนมัติ เพราะเทสต์ controller แบบ standalone ไม่เปิดใช้ method security
- **ยังไม่ได้ทดสอบหน้าเว็บ (Thymeleaf)**
- **ยังไม่ได้ตั้งค่า Jacoco** จึงยังไม่มีตัวเลข code coverage (รายการระดับ P2)
- ไม่มีเทสต์การทำงานพร้อมกัน (race) ของการจ่ายซ้ำ ป้องกันด้วย `UNIQUE` ใน DB ซึ่งต้องยืนยันด้วย integration test
