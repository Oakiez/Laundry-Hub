# SOLID Analysis — ส่วนของพีช (Full-Service Order)

> ส่วนนี้ให้โอ๊ครวมเข้า `doc/solid-analysis.md`
> เลขบรรทัดอ้างอิงโค้ด ณ วันที่ 9 ต.ค. 2569 (หลัง merge PR #12) ถ้าแก้ไฟล์ต้องอัปเดตเลขบรรทัดด้วย
> path ย่อ: `code/src/main/java/com/laundryhub/` = `…/`

## S — Single Responsibility

| ไฟล์ : บรรทัด | หน้าที่เดียว | เหตุผล |
|---|---|---|
| `…/service/impl/OrderServiceImpl.java:42` | กฎธุรกิจของออเดอร์ (สร้าง, แก้ไข, เปลี่ยนสถานะ, ยกเลิก, ตรวจเจ้าของ) | ไม่คิดราคาเอง (ส่งให้ `PricingStrategy` บรรทัด 186) ไม่แปลง DTO เอง (ส่งให้ `OrderMapper`) ไม่สร้างแจ้งเตือนเอง (ยิง event บรรทัด 170) |
| `…/service/pricing/FullServicePricing.java:15` | คำนวณราคาต่อรายการเท่านั้น | ไม่รู้จัก DB หรือ entity รับแค่ตัวเลขผ่าน `FullServicePricingInput` จึงทดสอบแยกได้โดยไม่ต้อง mock อะไร |
| `…/service/pricing/FullServicePricingInput.java:13` | ตรวจความถูกต้องของข้อมูลที่ใช้คิดราคา (compact constructor) | แยกการตรวจค่าออกจากสูตร สูตรจึงเชื่อได้ว่าค่าที่ได้รับถูกต้องเสมอ |
| `…/service/state/OrderStateFactory.java:10` | แปลง `OrderStatus` (ค่าใน DB) เป็นคลาส State | Service ไม่ต้องรู้ว่าสถานะไหนคือคลาสอะไร |
| `…/mapper/OrderMapper.java:11` | แปลง `LaundryOrder` → `OrderResponse` | ไม่มี `toEntity` เพราะการสร้างออเดอร์ต้องค้น DB และคิดราคา ซึ่งเป็นงานของ Service |
| `…/controller/api/OrderApiController.java:34` | รับ HTTP, ตรวจสิทธิ์ (`@PreAuthorize`), validation (`@Valid`) แล้วส่งต่อ | ไม่มีกฎธุรกิจและไม่ import Repository เลย |
| `…/service/OrderPayableProvider.java:13` | ส่งออเดอร์ให้โมดูล Payment ในรูป `Payable` | แยกจาก `OrderService` เพื่อให้ Payment ไม่ต้องรู้จัก API ทั้งหมดของออเดอร์ |

## O — Open/Closed

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/service/pricing/PricingStrategy.java:5` + `…/service/impl/OrderServiceImpl.java:51` | Service ถือ `PricingStrategy<FullServicePricingInput>` (interface) และเรียกที่บรรทัด 186 ถ้าเพิ่มวิธีคิดราคาใหม่ (เช่น โปรลดราคา) = เพิ่มคลาสที่ implement `PricingStrategy` **ไม่ต้องแก้ `OrderServiceImpl`** · `SelfServicePricing` ของปอนด์ก็ implement interface เดียวกันโดยไม่แตะโค้ดของพีช |
| `…/service/impl/OrderServiceImpl.java:151-166` | `advance()` / `cancel()` ถาม State ว่า `next()` / `canCancel()` ไม่มี `if/switch` ตามสถานะ เพิ่มสถานะใหม่ = เพิ่มคลาส State 1 คลาส + 1 บรรทัดใน `OrderStateFactory.java:11-19` **Service ไม่ต้องแก้** |
| `…/service/impl/OrderServiceImpl.java:169-172` | ยิง `OrderStatusChangedEvent` อย่างเดียว ถ้าอยากเพิ่มการแจ้งเตือนช่องทางใหม่ (อีเมล/LINE) = เพิ่ม listener ใหม่ **ไม่ต้องแก้ OrderService** |
| `…/service/OrderPayableProvider.java:22` | `supports()` คืน `LAUNDRY_ORDER` แล้ว `CheckoutFacade` (`…/service/payment/CheckoutFacade.java:36`) เลือก provider เอง โมดูลออเดอร์ต่อเข้า Payment ได้**โดยไม่แก้โค้ดของโชกุนแม้แต่บรรทัดเดียว** |

## L — Liskov Substitution

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/service/state/OrderState.java:7` และ state ทั้ง 7 คลาสใน `…/service/state/` | ทุกคลาส implement ครบ 3 เมธอด ใช้แทนกันได้ทุกตัว สถานะสุดท้ายคืน `Optional.empty()` (`PickedUpState.java:16`, `CancelledState.java:16`) **แทนการ throw `UnsupportedOperationException`** ผู้เรียก (`OrderServiceImpl.java:152`) จึงไม่ต้องเช็คว่าเป็นคลาสไหนก่อนเรียก |
| `test/java/com/laundryhub/service/state/OrderStateTest.java` | วนทุกค่าใน `OrderStatus` เรียก `next()` / `canCancel()` ผ่าน interface เดียวกัน ไม่มีตัวไหน throw (15 เคส) |
| `…/service/pricing/FullServicePricing.java:15` | ใช้แทน `PricingStrategy` ได้ตามสัญญา: รับ input คืน `BigDecimal` ปัด 2 ตำแหน่ง ข้อมูลผิดได้ `BusinessRuleException` (400) ตามที่ทั้งระบบใช้ ไม่ใช่ exception แปลกๆ |

## I — Interface Segregation

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/domain/entity/LaundryOrder.java:22, 87-99` | implement `Payable` (`…/domain/Payable.java:7`) ที่มีแค่ 4 เมธอด โมดูล Payment เห็นออเดอร์ผ่าน 4 เมธอดนี้เท่านั้น แก้ field อื่นของออเดอร์ไม่ได้ |
| `…/service/OrderService.java:31` | `findPayable` คืน `Payable` ไม่ใช่ `LaundryOrder` ฝั่งที่เรียกจึงไม่ผูกกับ entity |
| `…/service/state/OrderState.java:7-13` | interface เล็ก 3 เมธอด (`status`, `next`, `canCancel`) มีแค่สิ่งที่ State ทุกตัวต้องตอบได้ |
| `…/service/pricing/PricingStrategy.java:5-7` | เมธอดเดียว `calculate(I)` และเป็น generic ทำให้ฝากซักกับซักเองใช้ interface เดียวกันได้โดยไม่ต้องมีเมธอดที่อีกฝั่งไม่ใช้ |

## D — Dependency Inversion

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/controller/api/OrderApiController.java:40` | ขึ้นกับ `OrderService` (interface) ไม่ใช่ `OrderServiceImpl` |
| `…/service/impl/OrderServiceImpl.java:51, 53, 55` | ขึ้นกับ `PricingStrategy` และ `ApplicationEventPublisher` (interface ทั้งคู่) รับทุกอย่างผ่าน **constructor** (บรรทัด 55) ไม่มี `@Autowired` บน field เทสต์จึงส่ง mock เข้าไปได้ตรงๆ (`test/java/com/laundryhub/service/OrderServiceTest.java`) |
| `…/service/OrderPayableProvider.java:15, 17` | ขึ้นกับ `OrderService` (interface) ผ่าน constructor ไม่เรียก Repository เอง |
| `…/service/payment/CheckoutFacade.java:36` | ฝั่ง Payment ขึ้นกับ `PayableProvider` (abstraction ใน contract) ไม่รู้จัก `LaundryOrder` / `OrderServiceImpl` เลย ทิศทางการพึ่งพาจึงชี้เข้าหา interface ไม่ใช่เข้าหาโมดูลออเดอร์ |
