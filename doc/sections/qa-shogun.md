# เตรียมตอบอาจารย์ — โชกุน (Payment / Notification / API Quality)

> ใช้เป็นเอกสารเตรียมตัวส่วนตัว อ่านแล้วต้อง **อธิบายด้วยคำพูดของตัวเองได้** ไม่ใช่ท่อง
> path ย่อ: `code/src/main/java/com/laundryhub/` = `…/`

## 5 คำถามหลักจาก brief (หัวข้อ 10)

### 1) Observer ใน Spring (`ApplicationEventPublisher` / `@EventListener`) ทำงานยังไง และ sync/async ต่างกันอย่างไร

**ทำงานยังไง**
1. ผู้ยิงเรียก `eventPublisher.publishEvent(new PaymentCompletedEvent(...))` (ใน `CheckoutFacade.publishCompleted`, `…/service/payment/CheckoutFacade.java`)
2. Spring (ตัวกระจายเหตุการณ์ของ `ApplicationContext`) หา listener ทุกตัวที่รับ event ชนิดนั้น (เทียบจากชนิดพารามิเตอร์ของเมธอดที่ใส่ `@EventListener`)
3. เรียก listener ทีละตัว → `NotificationEventListener.on(PaymentCompletedEvent)` เรียก `notificationService.notifyUser(...)`
4. ผู้ยิงไม่รู้จักผู้ฟัง (loose coupling) เพิ่มผู้ฟังใหม่ได้โดยไม่แก้ผู้ยิง
- event ของเราเป็น `record` ธรรมดา ไม่ต้อง `extends ApplicationEvent` (Spring 4.2 ขึ้นไปรับ object อะไรก็ได้)

**Sync vs Async**
| | Sync (ค่าเริ่มต้นที่เราใช้) | Async (`@Async` + `@EnableAsync`) |
|---|---|---|
| thread | เธรดเดียวกับผู้ยิง ผู้ยิงรอจน listener เสร็จ | อีกเธรด ผู้ยิงไม่รอ |
| transaction | **ธุรกรรมเดียวกัน** ถ้า listener พัง ธุรกรรมหลัก rollback ด้วย | แยกธุรกรรม listener พังแล้วงานหลักไม่กระทบ |
| exception | ส่งกลับไปที่ผู้ยิง | หายไปกับเธรดนั้น (ต้องจัดการเอง) |
| ข้อเสีย | แจ้งเตือนช้า/พังแล้วกระทบการชำระเงิน | อาจอ่านข้อมูลที่ธุรกรรมหลักยังไม่ commit และอาจไม่มีแจ้งเตือนถ้าล้ม |

**เราเลือก sync เพราะ** ต้องการข้อมูลสอดคล้อง (ไม่มีกรณีจ่ายแล้วแต่ไม่มีแจ้งเตือน) ถ้าต้องแยกให้ใช้ `@TransactionalEventListener(AFTER_COMMIT)` แต่ต้องเปิด transaction ใหม่ (`REQUIRES_NEW`) ใน `notifyUser` มิฉะนั้นข้อมูลจะไม่ถูกบันทึก

### 2) Factory ที่รับ `List<PaymentProcessor>` ทาง constructor ทำให้ OCP ได้ยังไง

- `PaymentProcessorFactory` (`…/service/payment/PaymentProcessorFactory.java` บรรทัด 21) รับ `List<PaymentProcessor>` ทาง constructor Spring จะ **ฉีดทุก bean ที่ implement `PaymentProcessor`** เข้ามาให้เอง
- Factory วนลูปสร้างตาราง `method → processor` (`processors.put(processor.method(), processor)`) แล้ว `getProcessor(method)` ค้นจากตาราง
- **เพิ่มวิธีชำระใหม่ (เช่น `CoinProcessor`)** = เพิ่มคลาส `@Component` implement `PaymentProcessor` แค่นั้น **ไม่ต้องแก้ Factory และ `PaymentServiceImpl`** → *open for extension, closed for modification* (OCP)
- ถ้าไม่ได้ทำแบบนี้ ต้องมี `if/switch` ตามวิธีชำระ แล้วแก้ทุกครั้งที่เพิ่มวิธีใหม่
- ป้องกันพลาด: มี processor ซ้ำ method เดียวกัน → โยน `IllegalStateException` ตั้งแต่ตอนสตาร์ท (fail fast), method ที่ไม่รองรับ → `BusinessRuleException` (400)
- **ซื่อสัตย์เรื่องชื่อ pattern:** นี่คือ Simple Factory แบบ registry + Strategy ไม่ใช่ GoF *Factory Method* ตามตำรา (ที่ให้คลาสลูกเป็นคนสร้าง)

### 3) Facade ต่างจาก Service ธรรมดาอย่างไร

| | Service (`PaymentServiceImpl`) | Facade (`CheckoutFacade`) |
|---|---|---|
| ขอบเขต | กฎของเรื่องเดียว (payment) | ประสานหลายส่วนเพื่อทำงานหนึ่งอย่างให้จบ |
| ทำอะไร | สร้าง/ยืนยัน payment, กันจ่ายซ้ำ, เรียก processor | หา payable → ตรวจสิทธิ์ → เรียก service → ยิง event → แปลงเป็น DTO |
| รู้จักใคร | repository, factory | `PayableProvider` หลายตัว, `PaymentService`, event publisher, mapper |
| ไม่มี | ไม่ตรวจสิทธิ์/ไม่ยิง event | ไม่มีกฎการเงินของตัวเอง (ส่งต่อให้ service) |

ประโยชน์: Controller เรียกเมธอดเดียว (`checkout`) ไม่ต้องรู้ลำดับ 4 ขั้นตอน และถ้าเพิ่มขั้นตอนใหม่ (เช่น ส่งอีเมล) แก้ที่ Facade ที่เดียว

### 4) `@RestControllerAdvice` ทำงานยังไง ลำดับของ handler เลือกยังไง

- `@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody` เป็น bean กลางที่รวม `@ExceptionHandler` ไว้ใช้ร่วมกันทุก controller (`…/exception/GlobalExceptionHandler.java` บรรทัด 30)
- เมื่อ controller โยน exception ตัวจัดการ exception ของ Spring MVC จะหาที่จัดการ: ดู `@ExceptionHandler` ในตัว controller เองก่อน แล้วค่อยดูใน advice
- **เลือก handler ที่ตรงกับชนิด exception ใกล้ที่สุดในลำดับชั้นการสืบทอด** ไม่ขึ้นกับลำดับที่เขียนในไฟล์ ดังนั้น `handleUnexpected(Exception)` (บรรทัด 114) เป็นตาข่ายสุดท้าย ส่วน `ResourceNotFoundException` ถูกจับโดย handler ของตัวเองก่อน
- **สิ่งที่เจอจริงจากการทดสอบ (เล่าให้อาจารย์ฟังได้):** (1) ถ้าใส่ตัวกรองขอบเขตให้ advice (`basePackages` หรือ `annotations`) บาง error เช่น 405 เกิดตอนที่ยังไม่รู้ว่าเป็น controller ไหน advice จึงไม่ทำงาน (2) handler ของ `Exception` กลืน error ของ Spring MVC เองทำให้ 405/400 กลายเป็น 500 ต้องเพิ่ม handler เฉพาะ (3) exception ที่เกิดใน filter ของ Spring Security (401) ไม่ผ่าน advice นี้

### 5) ทำไม `payments` มี FK 2 ตัวพร้อม `CHECK` และ UNIQUE ช่วยป้องกันอะไร

การชำระ 1 รายการจ่ายให้ "ออเดอร์ฝากซัก" **หรือ** "รอบใช้เครื่อง" อย่างใดอย่างหนึ่ง จึงมี `order_id` และ `session_id` ที่ nullable ทั้งคู่ และคุมด้วย constraint 3 ชั้น:
1. **FK** → ผูกกับแถวจริงเท่านั้น (referential integrity)
2. **`CHECK chk_payment_target`** → ต้องมีค่า **เพียงตัวเดียว** (XOR) กันแถวที่ผูกทั้งสองหรือไม่ผูกอะไรเลย
3. **`UNIQUE` บน `order_id` และ `session_id`** → 1 ออเดอร์/1 รอบใช้เครื่อง มีใบชำระได้ **ใบเดียว** กันจ่ายซ้ำ (PostgreSQL ถือว่า `NULL` ไม่ซ้ำกัน ค่า NULL ของอีกฝั่งจึงไม่ชน)

ทำไมต้องพึ่ง DB ไม่ใช่โค้ดอย่างเดียว: ถ้ามีสองคำขอพร้อมกัน ทั้งคู่ผ่านการเช็คในโค้ดได้ (check-then-act) แต่ `UNIQUE` กั้นไว้ที่ DB แล้ว `GlobalExceptionHandler` แปลง `DataIntegrityViolationException` เป็น 409 ส่วนการเช็คในโค้ด (`PaymentServiceImpl.alreadyHasPayment`) มีไว้ให้ข้อความ error ที่อ่านง่าย
**หลักฐานจริง:** `PaymentRepositoryDbTest` (`test/java/com/laundryhub/repository/`) รันกับ PostgreSQL จริง 13 ข้อผ่าน: จ่ายซ้ำออเดอร์/รอบใช้เครื่อง → DB ปฏิเสธด้วย `payments_order_id_key` / `payments_session_id_key`, ผูกทั้ง order และ session หรือไม่ผูกเลย → ปฏิเสธด้วย `CHECK`, ออเดอร์ที่ไม่มีอยู่ → ปฏิเสธด้วย `fk_payment_order` (ดู `doc/test-report/test-report.md` หัวข้อ 3.6)
ทางเลือกที่ไม่ใช้: สองตารางแยก (โค้ดซ้ำ) หรือ `payable_type + payable_id` แบบ polymorphic (ตั้ง FK จริงไม่ได้)

---

## คำถามเสริมที่อาจารย์น่าจะถาม (ตอบสั้นๆ)

| คำถาม | คำตอบสั้นๆ |
|---|---|
| ทำไมไม่รับ `amount` จาก client | กันแก้ราคา ยอดมาจาก `Payable.getPayableAmount()` ฝั่งเซิร์ฟเวอร์ (`Payment.forPayable`, `CheckoutRequest` ไม่มีฟิลด์ amount) |
| ทำไมใช้ constructor injection | ฟิลด์เป็น `final` ทดสอบง่าย (ส่ง mock เข้า constructor) เห็น dependency ชัด และกฎของทีมห้าม `@Autowired` บน field |
| ทำไมแยก DTO กับ Entity | ไม่ให้ Entity หลุดเป็น API contract (เช่น ฟิลด์ภายใน/lazy proxy) แปลงด้วย `PaymentMapper` |
| ทำไม `Payment` เก็บ `orderId` เป็น `Long` ไม่ใช้ `@OneToOne` | ไม่ให้โมดูล Payment รู้จัก `LaundryOrder`/`UsageSession` ตรงๆ (DIP) FK ยังบังคับที่ DB |
| ทำไมใช้ LAZY | EAGER ดึงข้อมูลที่ไม่ได้ใช้และก่อน N+1 (ตั้ง `open-in-view: false` ต้องดึงภายใน transaction แล้วส่งเป็น DTO) |
| `@PreAuthorize` กับตรวจสิทธิ์ใน Facade ต่างกันยังไง | `@PreAuthorize` ตรวจจาก role/ไอดีใน path ได้ทันที (รายการทั้งหมด, confirm) ส่วน "เจ้าของ payable" ต้องค้นข้อมูลก่อน จึงตรวจใน `CheckoutFacade` |
| ทำไมรายการแบ่งหน้าใช้ `PagedModel` | โครงสร้าง JSON คงที่ ต่างจาก `Page` ตรงๆ ที่ Spring เตือนว่าไม่เสถียร |
| ทำไมตรวจฟิลด์ `sort` | `?sort=abc` เคยทำให้ได้ 500 แก้ด้วย whitelist (`PageableValidator`) และไม่เปิดให้เรียงด้วยฟิลด์ที่ไม่ตั้งใจเปิด |
| เทสต์อะไรบ้าง | Unit test (Mockito) ของ Factory, Processor, Service, Facade, Listener, Controller (MockMvc standalone) + ทดสอบจริงกับ Postgres แบบ manual ดู `doc/test-report/test-report.md` |
| บั๊กที่เจอระหว่างทำ | advice scope ผิด, 405 กลายเป็น 500, `markPaid` เขียนทับเวลา, `notifyUser` ไม่ตรวจ input, `sort` ผิดชื่อได้ 500, Swagger แสดงพารามิเตอร์ผิด (ดูหัวข้อ 4 ของ test report) |
| ถ้าเพิ่มวิธีชำระใหม่ต้องทำอะไร | เพิ่มคลาส `@Component` implement `PaymentProcessor` (เช่น `CoinProcessor`) ไม่แก้ Factory/Service |
| ข้อจำกัดที่รู้ | integration test กับ DB จริง (`PaymentRepositoryDbTest` 13 ข้อ) เป็นแบบ opt-in ต้องตั้ง `LAUNDRY_DB_TESTS=true` และ CI ยังไม่รัน, `@PreAuthorize` ยังไม่มีเทสต์อัตโนมัติ, notification เป็น sync (กระทบธุรกรรมหลักถ้าพัง) |

## ไฟล์ที่ต้องอธิบายได้ทีละไฟล์ (เรียงตามความสำคัญ)
1. `…/service/payment/CheckoutFacade.java` — ลำดับ 4 ขั้นตอน, ตรวจเจ้าของ, ยิง event
2. `…/service/payment/PaymentProcessorFactory.java` — รับ `List` ทาง constructor, ตารางค้นหา
3. `…/service/payment/PaymentProcessor.java` + `CashProcessor` + `QrMockProcessor` — Strategy, เงินสด `PENDING` / QR `PAID`
4. `…/service/impl/PaymentServiceImpl.java` — กันจ่ายซ้ำ, `confirm`
5. `…/domain/entity/Payment.java` — `forPayable`, `markPaid`, เหตุผลที่เก็บ FK เป็น `Long`
6. `…/event/NotificationEventListener.java` — Observer, เหตุผลที่ sync
7. `…/exception/GlobalExceptionHandler.java` — แต่ละ handler และสถานะ
8. `…/controller/api/PaymentApiController.java`, `NotificationApiController.java` — endpoint, สิทธิ์, แบ่งหน้า
9. `…/common/PageableValidator.java` — กัน `sort` ผิด
10. `…/config/OpenApiConfig.java` — Swagger + Basic Auth
