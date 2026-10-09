# SOLID Analysis

เอกสารนี้ระบุว่าแต่ละหลักการ SOLID ปรากฏที่ **ไฟล์ไหน บรรทัดไหน** พร้อมเหตุผลสั้นๆ แยกตามผู้รับผิดชอบโมดูล

| ผู้รับผิดชอบ | โมดูล |
|---|---|
| โอ๊ค | Foundation / Security / Auth / Branch |
| พีช | Full-Service Order |
| ปอนด์ | Self-Service Machine |
| โชกุน | Payment / Notification / API Quality |

> path ย่อ: `code/src/main/java/com/laundryhub/` = `…/` (ของปอนด์ระบุ path เทียบจากโฟลเดอร์นี้โดยไม่มี `…/` นำหน้า)
> เลขบรรทัดอ้างอิงโค้ด ณ วันที่ส่งงาน ตรวจเทียบกับโค้ดแล้ว
> ต้นฉบับของแต่ละคนอยู่ใน `doc/sections/solid-analysis-<ชื่อ>.md`

## S — Single Responsibility (แต่ละคลาสเปลี่ยนด้วยเหตุผลเดียว)

### โอ๊ค

| ไฟล์ : บรรทัด | หน้าที่เดียว | เหตุผล |
|---|---|---|
| `…/service/impl/AuthServiceImpl.java:17` | สมัครสมาชิก (เช็กซ้ำ, เข้ารหัสผ่าน, สร้าง `User` + `CustomerProfile`) | ไม่ดูแลโปรไฟล์หลังสมัคร (เป็นของ `UserServiceImpl`) และไม่คิดวิธีเข้ารหัสเอง (ให้ `PasswordEncoder`) |
| `…/service/impl/UserServiceImpl.java:17` | อ่านข้อมูลผู้ใช้และอ่าน/แก้โปรไฟล์ | แยกจากเรื่องบัญชี/รหัสผ่าน แก้เรื่องหนึ่งไม่กระทบอีกเรื่อง |
| `…/service/impl/BranchServiceImpl.java:17` | กฎการจัดการสาขา (CRUD) | ไม่รู้เรื่องหน้าเว็บ/JSON ไม่แปลง DTO เอง (ส่งให้ `BranchMapper`) |
| `…/config/PasswordEncoderConfig.java:10` แยกจาก `…/config/SecurityConfig.java:16` | เลือกอัลกอริทึมเข้ารหัสรหัสผ่าน (BCrypt) | เปลี่ยนด้วยเหตุผลต่างจากกฎสิทธิ์ URL (SecurityConfig) และกัน dependency วนกัน (Service ฉีด encoder ได้โดยไม่ผูกกับ SecurityConfig) |
| `…/security/ApiErrorWriter.java:21` | เขียน `ApiErrorResponse` เป็น JSON ให้ error ที่เกิดในชั้น Security | ตัวจับ 401/403 (`ApiAuthenticationEntryPoint.java:14`, `ApiAccessDeniedHandler.java:14`) แค่ตัดสินใจว่าจะตอบอะไร ไม่ต้องรู้วิธีเขียน JSON |
| `…/mapper/UserMapper.java:10`, `…/mapper/BranchMapper.java:9` | แปลง Entity ↔ DTO | แยกจาก Service เพื่อไม่ให้ Entity หลุดเป็น API contract (เช่น `password`) |
| `…/common/SecurityUtils.java:9` | อ่านผู้ใช้ปัจจุบันจาก SecurityContext (`currentUserId`, `currentRole`) | จุดเดียวที่โมดูลอื่นเรียกใช้ ไม่ต้องกระจายโค้ดอ่าน SecurityContext |
| `…/controller/api/BranchApiController.java:27` | รับ HTTP, ตรวจสิทธิ์ (`@PreAuthorize`), `@Valid` แล้วส่งต่อ | ไม่มีกฎธุรกิจและไม่ import Repository เลย |

### พีช

| ไฟล์ : บรรทัด | หน้าที่เดียว | เหตุผล |
|---|---|---|
| `…/service/impl/OrderServiceImpl.java:42` | กฎธุรกิจของออเดอร์ (สร้าง, แก้ไข, เปลี่ยนสถานะ, ยกเลิก, ตรวจเจ้าของ) | ไม่คิดราคาเอง (ส่งให้ `PricingStrategy` บรรทัด 186) ไม่แปลง DTO เอง (ส่งให้ `OrderMapper`) ไม่สร้างแจ้งเตือนเอง (ยิง event บรรทัด 170) |
| `…/service/pricing/FullServicePricing.java:15` | คำนวณราคาต่อรายการเท่านั้น | ไม่รู้จัก DB หรือ entity รับแค่ตัวเลขผ่าน `FullServicePricingInput` จึงทดสอบแยกได้โดยไม่ต้อง mock อะไร |
| `…/service/pricing/FullServicePricingInput.java:13` | ตรวจความถูกต้องของข้อมูลที่ใช้คิดราคา (compact constructor) | แยกการตรวจค่าออกจากสูตร สูตรจึงเชื่อได้ว่าค่าที่ได้รับถูกต้องเสมอ |
| `…/service/state/OrderStateFactory.java:10` | แปลง `OrderStatus` (ค่าใน DB) เป็นคลาส State | Service ไม่ต้องรู้ว่าสถานะไหนคือคลาสอะไร |
| `…/mapper/OrderMapper.java:11` | แปลง `LaundryOrder` → `OrderResponse` | ไม่มี `toEntity` เพราะการสร้างออเดอร์ต้องค้น DB และคิดราคา ซึ่งเป็นงานของ Service |
| `…/controller/api/OrderApiController.java:34` | รับ HTTP, ตรวจสิทธิ์ (`@PreAuthorize`), validation (`@Valid`) แล้วส่งต่อ | ไม่มีกฎธุรกิจและไม่ import Repository เลย |
| `…/service/OrderPayableProvider.java:13` | ส่งออเดอร์ให้โมดูล Payment ในรูป `Payable` | แยกจาก `OrderService` เพื่อให้ Payment ไม่ต้องรู้จัก API ทั้งหมดของออเดอร์ |

### ปอนด์

| ไฟล์ : บรรทัด | คำอธิบาย / ข้อจำกัด (ตามที่ปอนด์เขียน) |
|---|---|
| `service/BookingValidator.java:16`, `mapper/MachineMapper.java:10`, `service/impl/MachineServiceImpl.java:32` | Booking time rules, DTO mapping, and machine management have separate owners. MachineService coordinates repositories and state decisions. |

### โชกุน

| ไฟล์ : บรรทัด | หน้าที่เดียว | เหตุผล |
|---|---|---|
| `…/service/payment/PaymentProcessorFactory.java:17` | เลือก processor ตามวิธีชำระ | ไม่คำนวณเงิน ไม่บันทึกข้อมูล |
| `…/service/impl/PaymentServiceImpl.java:21` | กฎการสร้างและยืนยันการชำระเงิน (กันจ่ายซ้ำ, เปลี่ยนสถานะ, บันทึก) | ไม่ตรวจสิทธิ์ ไม่ยิง event ไม่แปลงเป็น DTO |
| `…/service/payment/CheckoutFacade.java:29` | ประสานขั้นตอน checkout (หา payable → ตรวจสิทธิ์ → สร้าง payment → ยิง event) | ไม่มีกฎการเงินของตัวเอง ส่งต่อให้ service |
| `…/mapper/PaymentMapper.java:9` | แปลง `Payment` → `PaymentResponse` | แยกจาก service เพื่อไม่ให้ Entity หลุดเป็น API contract |
| `…/event/NotificationEventListener.java:17` | แปลงเหตุการณ์เป็นข้อความแจ้งเตือน | ไม่รู้วิธีบันทึก (ให้ `NotificationService` ทำ) |
| `…/exception/GlobalExceptionHandler.java:30` | แปลง exception เป็น `ApiErrorResponse` จุดเดียว | service/controller ไม่ต้องจัดรูปแบบ error เอง |

## O — Open/Closed (เพิ่มความสามารถด้วยการเพิ่มคลาส ไม่แก้ของเดิม)

### โอ๊ค

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/config/SecurityConfig.java:28` | กฎสิทธิ์ระดับ endpoint ย้ายไปอยู่ที่ `@PreAuthorize` ใน Controller ของแต่ละโมดูล (เช่น `…/controller/api/BranchApiController.java`, `UserApiController.java:24`) โมดูลใหม่ (Order, Machine, Payment) **เพิ่มกฎของตัวเองได้โดยไม่ต้องแก้ `SecurityConfig`** ซึ่งเป็นไฟล์กลาง เหลือแค่กฎระดับโซน (`/staff/**`, `/admin/**`) |
| `…/security/ApiErrorWriter.java:21` | เพิ่มชนิด error ใหม่จากชั้น Security = เรียก `write(...)` ด้วยสถานะ/ข้อความใหม่ ไม่ต้องแก้ตัวเขียน JSON |

### พีช

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/service/pricing/PricingStrategy.java:5` + `…/service/impl/OrderServiceImpl.java:51` | Service ถือ `PricingStrategy<FullServicePricingInput>` (interface) และเรียกที่บรรทัด 186 ถ้าเพิ่มวิธีคิดราคาใหม่ (เช่น โปรลดราคา) = เพิ่มคลาสที่ implement `PricingStrategy` **ไม่ต้องแก้ `OrderServiceImpl`** · `SelfServicePricing` ของปอนด์ก็ implement interface เดียวกันโดยไม่แตะโค้ดของพีช |
| `…/service/impl/OrderServiceImpl.java:151-166` | `advance()` / `cancel()` ถาม State ว่า `next()` / `canCancel()` ไม่มี `if/switch` ตามสถานะ เพิ่มสถานะใหม่ = เพิ่มคลาส State 1 คลาส + 1 บรรทัดใน `OrderStateFactory.java:11-19` **Service ไม่ต้องแก้** |
| `…/service/impl/OrderServiceImpl.java:169-172` | ยิง `OrderStatusChangedEvent` อย่างเดียว ถ้าอยากเพิ่มการแจ้งเตือนช่องทางใหม่ (อีเมล/LINE) = เพิ่ม listener ใหม่ **ไม่ต้องแก้ OrderService** |
| `…/service/OrderPayableProvider.java:22` | `supports()` คืน `LAUNDRY_ORDER` แล้ว `CheckoutFacade` (`…/service/payment/CheckoutFacade.java:36`) เลือก provider เอง โมดูลออเดอร์ต่อเข้า Payment ได้**โดยไม่แก้โค้ดของโชกุนแม้แต่บรรทัดเดียว** |

### ปอนด์

| ไฟล์ : บรรทัด | คำอธิบาย / ข้อจำกัด (ตามที่ปอนด์เขียน) |
|---|---|
| `service/pricing/SelfServicePricing.java:9` | Time-based pricing implements the shared PricingStrategy contract; another pricing implementation can be added independently. SessionService will consume that interface in the next stage. |

### โชกุน

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/service/payment/PaymentProcessor.java:8` | interface ที่ทุกวิธีชำระ implement |
| `…/service/payment/PaymentProcessorFactory.java:21-27` | รับ `List<PaymentProcessor>` ทาง constructor แล้วสร้างตาราง method → processor (บรรทัด 23) เพิ่มวิธีชำระใหม่ = เพิ่มคลาส `@Component` ใหม่ **โดยไม่แก้ Factory และ `PaymentServiceImpl`** |
| `…/service/payment/CoinProcessor.java:18` | ตัวอย่างจริงของการเพิ่มวิธีชำระใหม่ (หยอดเหรียญ) ที่เพิ่มเป็นคลาสใหม่ทั้งคลาส โดย Factory และ Service ไม่ถูกแก้ · เทสต์ `PaymentProcessorFactoryTest.addingNewProcessor_needsNoChangeToFactory` ยืนยัน |
| `…/service/impl/PaymentServiceImpl.java:39` | เรียก `getProcessor(method).process(payment)` โดยไม่มี `if/switch` ตามวิธีชำระ |
| `…/event/NotificationEventListener.java:25` | เพิ่มช่องทางแจ้งเตือนใหม่ = เพิ่ม listener ใหม่ ไม่แก้โมดูลที่ยิง event |

## L — Liskov Substitution (คลาสลูกใช้แทนคลาสแม่ได้ ไม่ throw `UnsupportedOperationException`)

### โอ๊ค

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/security/AppUserDetails.java:13` | implement `UserDetails` ครบสัญญาที่ Spring Security ต้องการ (`getAuthorities`, `getPassword`, `getUsername`, `isEnabled`) ใช้แทน `UserDetails` ได้ทุกที่ที่ framework เรียก ไม่มีเมธอดที่ throw `UnsupportedOperationException` |
| `…/security/AppUserDetailsService.java:10` | แทน `UserDetailsService` ได้ตามสัญญา: ไม่พบผู้ใช้ → `UsernameNotFoundException` ตามที่ Spring Security คาดหวัง |
| `…/security/ApiAuthenticationEntryPoint.java:14`, `ApiAccessDeniedHandler.java:14` | implement `AuthenticationEntryPoint` / `AccessDeniedHandler` และใช้แทนตัวเดิมของ Spring ได้โดยพฤติกรรมที่ framework ต้องการยังครบ (ตอบ 401/403) |

### พีช

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/service/state/OrderState.java:7` และ state ทั้ง 7 คลาสใน `…/service/state/` | ทุกคลาส implement ครบ 3 เมธอด ใช้แทนกันได้ทุกตัว สถานะสุดท้ายคืน `Optional.empty()` (`PickedUpState.java:16`, `CancelledState.java:16`) **แทนการ throw `UnsupportedOperationException`** ผู้เรียก (`OrderServiceImpl.java:152`) จึงไม่ต้องเช็คว่าเป็นคลาสไหนก่อนเรียก |
| `test/java/com/laundryhub/service/state/OrderStateTest.java` | วนทุกค่าใน `OrderStatus` เรียก `next()` / `canCancel()` ผ่าน interface เดียวกัน ไม่มีตัวไหน throw (15 เคส) |
| `…/service/pricing/FullServicePricing.java:15` | ใช้แทน `PricingStrategy` ได้ตามสัญญา: รับ input คืน `BigDecimal` ปัด 2 ตำแหน่ง ข้อมูลผิดได้ `BusinessRuleException` (400) ตามที่ทั้งระบบใช้ ไม่ใช่ exception แปลกๆ |

### ปอนด์

| ไฟล์ : บรรทัด | คำอธิบาย / ข้อจำกัด (ตามที่ปอนด์เขียน) |
|---|---|
| `service/state/MachineState.java:5` | Every state implements the same capability queries without throwing UnsupportedOperationException. MachineService uses the capability to reject maintenance changes while a machine is in use. |

### โชกุน

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/service/payment/CashProcessor.java:10`, `…/service/payment/QrMockProcessor.java:10` และ `…/service/payment/CoinProcessor.java:18` | ทั้งสามแทน `PaymentProcessor` ได้ ทำตามสัญญาเดียวกัน (`method()` + `process()` คืน `PaymentStatus`) ไม่ throw `UnsupportedOperationException` (`CoinProcessor` โยน `BusinessRuleException` บรรทัด 28 เมื่อใช้กับออเดอร์ฝากซัก ซึ่งเป็นการปฏิเสธข้อมูลที่ไม่ถูกต้องตามกฎธุรกิจ ไม่ใช่การไม่รองรับเมธอด) |
| `test/java/com/laundryhub/service/payment/PaymentProcessorFactoryTest.java` | เทสต์ใช้ processor สองตัวสลับกันผ่าน Factory ได้โดยโค้ดฝั่งใช้ไม่ต้องรู้ชนิดจริง |

## I — Interface Segregation (interface เล็ก ตรงการใช้งาน ไม่ใช่ Fat Interface)

### โอ๊ค

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/repository/UserRepository.java:8`, `BranchRepository.java:6`, `CustomerProfileRepository.java:8` | แยก Repository ตามตาราง และเพิ่มเฉพาะเมธอดที่ใช้จริง (เช่น `existsByUsernameIgnoreCase`, `findByUserId`) ไม่มี Repository เดียวรวมทุกตาราง |
| `…/service/AuthService.java:6`, `UserService.java:7`, `BranchService.java:8` | แบ่ง Service interface ตามหน้าที่ (สมัคร / ผู้ใช้-โปรไฟล์ / สาขา) Controller แต่ละตัวพึ่งเฉพาะ interface ที่ใช้ ไม่ต้องพึ่งเมธอดที่ไม่เกี่ยว |

### พีช

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/domain/entity/LaundryOrder.java:22, 87-99` | implement `Payable` (`…/domain/Payable.java:7`) ที่มีแค่ 4 เมธอด โมดูล Payment เห็นออเดอร์ผ่าน 4 เมธอดนี้เท่านั้น แก้ field อื่นของออเดอร์ไม่ได้ |
| `…/service/OrderService.java:31` | `findPayable` คืน `Payable` ไม่ใช่ `LaundryOrder` ฝั่งที่เรียกจึงไม่ผูกกับ entity |
| `…/service/state/OrderState.java:7-13` | interface เล็ก 3 เมธอด (`status`, `next`, `canCancel`) มีแค่สิ่งที่ State ทุกตัวต้องตอบได้ |
| `…/service/pricing/PricingStrategy.java:5-7` | เมธอดเดียว `calculate(I)` และเป็น generic ทำให้ฝากซักกับซักเองใช้ interface เดียวกันได้โดยไม่ต้องมีเมธอดที่อีกฝั่งไม่ใช้ |

### ปอนด์

| ไฟล์ : บรรทัด | คำอธิบาย / ข้อจำกัด (ตามที่ปอนด์เขียน) |
|---|---|
| `domain/entity/UsageSession.java:31` | UsageSession implements the small Payable contract without depending on payment processing operations. |

### โชกุน

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/domain/Payable.java:7` | interface เล็ก 4 เมธอด (`getId`, `getPayableAmount`, `getPayableType`, `getOwnerUserId`) Payment ขอข้อมูลเท่าที่ต้องใช้ ไม่ต้องรับ `LaundryOrder`/`UsageSession` ทั้งก้อน |
| `…/service/payment/PayableProvider.java:6` | 2 เมธอด (`supports`, `findPayable`) |
| `…/service/payment/PaymentProcessor.java:8` | 2 เมธอด |
| `…/service/PaymentService.java:10` และ `…/service/NotificationService.java:7` | แยกเป็นคนละ interface ตามหน้าที่ ผู้ใช้ไม่ถูกบังคับให้พึ่งเมธอดที่ไม่เกี่ยวข้อง |

## D — Dependency Inversion (พึ่ง interface + Constructor Injection)

### โอ๊ค

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/service/impl/AuthServiceImpl.java:23` | รับ `UserRepository`, `PasswordEncoder`, `UserMapper` ผ่าน **constructor** (ไม่มี `@Autowired` บน field) เป็น interface/ตัวแปลงที่เปลี่ยนได้ เทสต์ส่ง mock เข้าไปตรงๆ (`test/java/com/laundryhub/service/AuthServiceTest.java:41`) |
| `…/service/impl/UserServiceImpl.java:23`, `BranchServiceImpl.java:22` | รูปแบบเดียวกัน: ขึ้นกับ Repository interface ผ่าน constructor |
| `…/controller/api/AuthApiController.java:23`, `UserApiController.java:28`, `BranchApiController.java:31` | Controller ขึ้นกับ Service **interface** (`AuthService`, `UserService`, `BranchService`) ไม่รู้จัก `*Impl` และไม่ import Repository |
| `…/controller/web/AuthWebController.java:20`, `ProfileWebController.java:23`, `BranchWebController.java:29` | ฝั่งหน้าเว็บทำแบบเดียวกัน ใช้ Service interface เดียวกับฝั่ง API จึงไม่มีตรรกะซ้ำ |
| `…/security/AppUserDetailsService.java:10` | ขึ้นกับ `UserRepository` (interface) ไม่ผูกกับ JPA โดยตรง |

### พีช

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/controller/api/OrderApiController.java:40` | ขึ้นกับ `OrderService` (interface) ไม่ใช่ `OrderServiceImpl` |
| `…/service/impl/OrderServiceImpl.java:51, 53, 55` | ขึ้นกับ `PricingStrategy` และ `ApplicationEventPublisher` (interface ทั้งคู่) รับทุกอย่างผ่าน **constructor** (บรรทัด 55) ไม่มี `@Autowired` บน field เทสต์จึงส่ง mock เข้าไปได้ตรงๆ (`test/java/com/laundryhub/service/OrderServiceTest.java`) |
| `…/service/OrderPayableProvider.java:15, 17` | ขึ้นกับ `OrderService` (interface) ผ่าน constructor ไม่เรียก Repository เอง |
| `…/service/payment/CheckoutFacade.java:36` | ฝั่ง Payment ขึ้นกับ `PayableProvider` (abstraction ใน contract) ไม่รู้จัก `LaundryOrder` / `OrderServiceImpl` เลย ทิศทางการพึ่งพาจึงชี้เข้าหา interface ไม่ใช่เข้าหาโมดูลออเดอร์ |

### ปอนด์

| ไฟล์ : บรรทัด | คำอธิบาย / ข้อจำกัด (ตามที่ปอนด์เขียน) |
|---|---|
| `service/impl/MachineServiceImpl.java:40` | Constructor injection supplies repository interfaces and ApplicationEventPublisher. The service implements MachineService. Mapper and registry are concrete collaborators; this is not a claim that every dependency is abstract. |
| `service/SessionPayableProvider.java:13`, `service/impl/SessionServiceImpl.java:15` | Checkout discovers PayableProvider beans. The session adapter depends on SessionService; its implementation uses UsageSessionRepository. Payment does not need a session repository dependency. |

### โชกุน

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/service/payment/CheckoutFacade.java:36` | รับ `List<PayableProvider>`, `PaymentService` (interface), `ApplicationEventPublisher` ผ่าน **constructor** ไม่ผูกกับ `OrderService`/`SessionService` ตรงๆ |
| `…/service/payment/CheckoutFacade.java:92` | เลือก provider จาก `PayableType` ผ่าน interface |
| `…/service/impl/PaymentServiceImpl.java:26` | รับ `PaymentRepository` และ `PaymentProcessorFactory` ทาง constructor |
| `…/domain/entity/Payment.java:35` | เก็บ `orderId` เป็น `Long` ไม่ import `LaundryOrder`/`UsageSession` ทำให้โมดูล Payment ไม่ขึ้นกับโมดูลอื่น |
| `…/event/NotificationEventListener.java:21` | พึ่ง `NotificationService` (interface) ผ่าน constructor |

ไม่มี `@Autowired` บน field ในโค้ดของโมดูลนี้ (ใช้ constructor injection ทั้งหมดตามกติกาทีม)

## หมายเหตุเพิ่มเติมของปอนด์ (ส่วน Self-Service)

Scope: pricing, machine states, booking validation, repositories, and machine management.
SessionService now supplies payment lookup; booking/lifecycle methods and machine/session
API/web integration are still pending at this stage.
Paths below are relative to `code/src/main/java/com/laundryhub/`.


MachineService returns DTOs from within a transaction, so a controller will not
serialize JPA entities or access a lazy association after leaving the service.
Machine/UsageSession now reference Branch/User using LAZY associations without
cascading removal to shared data. The original foreign keys remain unchanged.

All machine mutations are transactional. Update/delete/status operations acquire
the same machine row lock that SessionService must use later. A transaction alone
does not prevent two simultaneous booking requests from both seeing an empty slot.

Machine requests use Bean Validation, including monetary precision matching the
database. MachineService is `@Validated`; controllers must still use `@Valid` and
`@PreAuthorize` when added. No SecurityConfig request matchers are changed here.

SessionService.findPayable is a read-only transactional lookup, not an authorization
check. CheckoutFacade checks the owner/staff before creating payment and has an outer
transaction spanning lookup and payment. Returning a managed Payable follows the
existing order provider contract; future callers must consider its LAZY associations.
