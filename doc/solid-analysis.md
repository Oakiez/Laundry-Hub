# SOLID Analysis

> **วิธีกรอก (ทุกคนเขียนส่วนของตัวเอง):** ระบุ **ไฟล์ + เลขบรรทัด + เหตุผลสั้นๆ** ว่าหลักการนี้ปรากฏตรงไหน
> - เลขบรรทัดให้ **เติมหลังโค้ดนิ่งแล้ว** (ช่วง 10–12 ชม.สุดท้าย) ไม่งั้นเลขจะเพี้ยนทุกครั้งที่แก้โค้ด
> - ระหว่างนี้เขียนชื่อไฟล์/คลาสกับเหตุผลไว้ก่อนได้ แล้วใส่ `⬜` ในช่องบรรทัด
> - แก้เฉพาะแถวของตัวเอง เพื่อไม่ให้ merge ชนกัน แล้วเปลี่ยน `⬜` เป็นเลขบรรทัดจริง
> - ตัวอย่างโค้ดที่ชี้ ต้องเป็นของที่อยู่ในโปรเจกต์จริง **ห้ามอ้างสิ่งที่ไม่ได้ทำ** (อาจารย์จะเปิดไฟล์ตรวจ)

ผู้รับผิดชอบ: **โอ๊ค** (Foundation/Security), **พีช** (Order), **ปอนด์** (Machine), **โชกุน** (Payment/Notification)

---

## S — Single Responsibility (แต่ละคลาสเปลี่ยนด้วยเหตุผลเดียว)

| ผู้เขียน | ไฟล์ | บรรทัด | เหตุผล |
|---|---|---|---|
| โอ๊ค | `service/impl/AuthServiceImpl.java` (สมัครสมาชิก) แยกจาก `service/impl/UserServiceImpl.java` (โปรไฟล์) | ⬜ | แยกเรื่องบัญชี/ล็อกอินออกจากเรื่องข้อมูลโปรไฟล์ แก้อย่างหนึ่งไม่กระทบอีกอย่าง |
| โอ๊ค | `config/PasswordEncoderConfig.java` แยกจาก `config/SecurityConfig.java` | ⬜ | การเลือกอัลกอริทึมเข้ารหัสกับกฎสิทธิ์ URL เปลี่ยนด้วยเหตุผลต่างกัน |
| โอ๊ค | `mapper/UserMapper.java`, `mapper/BranchMapper.java` | ⬜ | แยกการแปลง Entity↔DTO ออกจาก Service |
| พีช | ⬜ | ⬜ | ⬜ |
| ปอนด์ | `BookingValidator` แยกจาก `MachineService` | ⬜ | ⬜ ปอนด์เติมเหตุผล |
| โชกุน | ⬜ | ⬜ | ⬜ |

## O — Open/Closed (เพิ่มความสามารถด้วยการเพิ่มคลาส ไม่แก้ if-else เดิม)

| ผู้เขียน | ไฟล์ | บรรทัด | เหตุผล |
|---|---|---|---|
| พีช | `service/pricing/FullServicePricing.java` (Strategy) | ⬜ | เพิ่มวิธีคิดราคาใหม่ = เพิ่ม `PricingStrategy` ตัวใหม่ |
| ปอนด์ | `service/pricing/SelfServicePricing.java` | ⬜ | ⬜ |
| โชกุน | `PaymentProcessorFactory` + `CashProcessor`/`QrMockProcessor`/`CoinProcessor` | ⬜ | เพิ่มวิธีจ่ายเงินใหม่ = เพิ่ม `PaymentProcessor` ตัวใหม่ |
| โอ๊ค | ⬜ (ถ้ามี) | ⬜ | ⬜ |

## L — Liskov Substitution (คลาสลูกใช้แทนแม่ได้ ไม่ throw `UnsupportedOperationException`)

| ผู้เขียน | ไฟล์ | บรรทัด | เหตุผล |
|---|---|---|---|
| พีช | `service/state/` (`OrderState` และ state ทุกตัว) | ⬜ | ทุก state ใช้แทนกันได้ ไม่ throw `UnsupportedOperationException` |
| ปอนด์ | `service/state/` (`MachineState` และ state ทุกตัว) | ⬜ | ⬜ |
| โชกุน | `PaymentProcessor` ทุก implementation | ⬜ | ⬜ |
| โอ๊ค | `security/AppUserDetails.java` (implements `UserDetails`) | ⬜ | Spring Security ใช้แทน `UserDetails` ได้ทุกที่ |

## I — Interface Segregation (interface เล็ก ตรงการใช้งาน ไม่ใช่ Fat Interface)

| ผู้เขียน | ไฟล์ | บรรทัด | เหตุผล |
|---|---|---|---|
| โชกุน | `domain/Payable.java` (4 เมธอด) | ⬜ | Payment ต้องการแค่ id, จำนวนเงิน, ชนิด, เจ้าของ ไม่ต้องรู้เรื่องอื่นของ Order/Session |
| โอ๊ค | `repository/UserRepository.java`, `BranchRepository.java` | ⬜ | แยก repository ตามตาราง มีเฉพาะเมธอดที่ใช้จริง |
| พีช | ⬜ (แยก interface อ่าน/เขียน ถ้ามี) | ⬜ | ⬜ |

## D — Dependency Inversion (พึ่ง interface ไม่ใช่ concrete class + Constructor Injection)

| ผู้เขียน | ไฟล์ | บรรทัด | เหตุผล |
|---|---|---|---|
| โอ๊ค | `service/impl/AuthServiceImpl.java` (constructor รับ `UserRepository`, `PasswordEncoder`) | ⬜ | พึ่ง interface ทดสอบด้วย Mockito ได้ (ดู `test/.../AuthServiceTest.java`) |
| โอ๊ค | `controller/api/*`, `controller/web/*` (รับ `XxxService` interface) | ⬜ | Controller ไม่รู้จัก implementation และไม่เรียก Repository ตรง |
| โชกุน | `service/payment/PayableProvider.java` + `PaymentService` | ⬜ | `PaymentService` ไม่รู้จัก `OrderService`/`SessionService` ตรงๆ |
| พีช | ⬜ | ⬜ | ⬜ |
| ปอนด์ | ⬜ | ⬜ | ⬜ |

---

## เช็กลิสต์ก่อนส่ง (โอ๊คเป็นคนตรวจตอนรวมเล่ม)
- [ ] ทุกแถวไม่มี `⬜` เหลือ
- [ ] ทุกไฟล์ที่อ้างถึงมีอยู่จริงใน `main` และเลขบรรทัดตรงกับโค้ดตอนส่ง
- [ ] ครบทั้ง 5 หลักการ และแต่ละคนมีอย่างน้อย 1 แถว
