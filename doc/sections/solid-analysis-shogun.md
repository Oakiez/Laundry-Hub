# SOLID Analysis — ส่วนของโชกุน (Payment / Notification / API Quality)

> ส่วนนี้ให้โอ๊ครวมเข้า `doc/solid-analysis.md`
> เลขบรรทัดอ้างอิงโค้ด ณ วันที่เขียน ถ้าแก้ไฟล์ต้องอัปเดตเลขบรรทัดด้วย
> path ย่อ: `code/src/main/java/com/laundryhub/` = `…/`

## S — Single Responsibility

| ไฟล์ : บรรทัด | หน้าที่เดียว | เหตุผล |
|---|---|---|
| `…/service/payment/PaymentProcessorFactory.java:17` | เลือก processor ตามวิธีชำระ | ไม่คำนวณเงิน ไม่บันทึกข้อมูล |
| `…/service/impl/PaymentServiceImpl.java:25` | กฎการสร้างและยืนยันการชำระเงิน (กันจ่ายซ้ำ, เปลี่ยนสถานะ, บันทึก) | ไม่ตรวจสิทธิ์ ไม่ยิง event ไม่แปลงเป็น DTO |
| `…/service/payment/CheckoutFacade.java:29` | ประสานขั้นตอน checkout (หา payable → ตรวจสิทธิ์ → สร้าง payment → ยิง event) | ไม่มีกฎการเงินของตัวเอง ส่งต่อให้ service |
| `…/mapper/PaymentMapper.java:9` | แปลง `Payment` → `PaymentResponse` | แยกจาก service เพื่อไม่ให้ Entity หลุดเป็น API contract |
| `…/event/NotificationEventListener.java:17` | แปลงเหตุการณ์เป็นข้อความแจ้งเตือน | ไม่รู้วิธีบันทึก (ให้ `NotificationService` ทำ) |
| `…/exception/GlobalExceptionHandler.java:30` | แปลง exception เป็น `ApiErrorResponse` จุดเดียว | service/controller ไม่ต้องจัดรูปแบบ error เอง |

## O — Open/Closed

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/service/payment/PaymentProcessor.java:8` | interface ที่ทุกวิธีชำระ implement |
| `…/service/payment/PaymentProcessorFactory.java:21-27` | รับ `List<PaymentProcessor>` ทาง constructor แล้วสร้างตาราง method → processor (บรรทัด 23) เพิ่มวิธีชำระใหม่ = เพิ่มคลาส `@Component` ใหม่ **โดยไม่แก้ Factory และ `PaymentServiceImpl`** |
| `…/service/payment/CoinProcessor.java:18` | ตัวอย่างจริงของการเพิ่มวิธีชำระใหม่ (หยอดเหรียญ) ที่เพิ่มเป็นคลาสใหม่ทั้งคลาส โดย Factory และ Service ไม่ถูกแก้ · เทสต์ `PaymentProcessorFactoryTest.addingNewProcessor_needsNoChangeToFactory` ยืนยัน |
| `…/service/impl/PaymentServiceImpl.java:43` | เรียก `getProcessor(method).process(payment)` โดยไม่มี `if/switch` ตามวิธีชำระ |
| `…/event/NotificationEventListener.java:25` | เพิ่มช่องทางแจ้งเตือนใหม่ = เพิ่ม listener ใหม่ ไม่แก้โมดูลที่ยิง event |

## L — Liskov Substitution

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/service/payment/CashProcessor.java:10`, `…/service/payment/QrMockProcessor.java:10` และ `…/service/payment/CoinProcessor.java:18` | ทั้งสามแทน `PaymentProcessor` ได้ ทำตามสัญญาเดียวกัน (`method()` + `process()` คืน `PaymentStatus`) ไม่ throw `UnsupportedOperationException` (`CoinProcessor` โยน `BusinessRuleException` บรรทัด 28 เมื่อใช้กับออเดอร์ฝากซัก ซึ่งเป็นการปฏิเสธข้อมูลที่ไม่ถูกต้องตามกฎธุรกิจ ไม่ใช่การไม่รองรับเมธอด) |
| `test/java/com/laundryhub/service/payment/PaymentProcessorFactoryTest.java` | เทสต์ใช้ processor สองตัวสลับกันผ่าน Factory ได้โดยโค้ดฝั่งใช้ไม่ต้องรู้ชนิดจริง |

## I — Interface Segregation

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/domain/Payable.java:7` | interface เล็ก 4 เมธอด (`getId`, `getPayableAmount`, `getPayableType`, `getOwnerUserId`) Payment ขอข้อมูลเท่าที่ต้องใช้ ไม่ต้องรับ `LaundryOrder`/`UsageSession` ทั้งก้อน |
| `…/service/payment/PayableProvider.java:6` | 2 เมธอด (`supports`, `findPayable`) |
| `…/service/payment/PaymentProcessor.java:8` | 2 เมธอด |
| `…/service/PaymentService.java:13` และ `…/service/NotificationService.java:7` | แยกเป็นคนละ interface ตามหน้าที่ ผู้ใช้ไม่ถูกบังคับให้พึ่งเมธอดที่ไม่เกี่ยวข้อง |

## D — Dependency Inversion

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/service/payment/CheckoutFacade.java:36` | รับ `List<PayableProvider>`, `PaymentService` (interface), `ApplicationEventPublisher` ผ่าน **constructor** ไม่ผูกกับ `OrderService`/`SessionService` ตรงๆ |
| `…/service/payment/CheckoutFacade.java:92` | เลือก provider จาก `PayableType` ผ่าน interface |
| `…/service/impl/PaymentServiceImpl.java:30` | รับ `PaymentRepository` และ `PaymentProcessorFactory` ทาง constructor |
| `…/domain/entity/Payment.java:35` | เก็บ `orderId` เป็น `Long` ไม่ import `LaundryOrder`/`UsageSession` ทำให้โมดูล Payment ไม่ขึ้นกับโมดูลอื่น |
| `…/event/NotificationEventListener.java:21` | พึ่ง `NotificationService` (interface) ผ่าน constructor |

ไม่มี `@Autowired` บน field ในโค้ดของโมดูลนี้ (ใช้ constructor injection ทั้งหมดตามกติกาทีม)
