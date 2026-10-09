# Design Patterns

> **ไฟล์นี้คือฉบับรวม (โอ๊คเป็นคนรวม) — เพื่อนไม่ต้องแก้ไฟล์นี้**
> ทุกคนเขียนส่วนของตัวเองในไฟล์แยก `doc/sections/design-patterns-<ชื่อ>.md` (ตัวอย่างดู `design-patterns-shogun.md`)
> ทุก Pattern ต้องตอบได้ว่า **"ปัญหาอะไร → pattern แก้อย่างไร → ถ้าไม่ใช้จะเป็นอย่างไร"** ห้ามยัด pattern เพื่อให้ครบ (ถูกหักคะแนน)
> ตารางด้านล่างเป็นโครงของฉบับรวม แถวที่ยังเป็น `⬜` โอ๊คจะเติมจากไฟล์ใน `doc/sections/` ตอนรวมเล่ม

ผู้รับผิดชอบ: **โอ๊ค** (Foundation/Security), **พีช** (Order), **ปอนด์** (Machine), **โชกุน** (Payment/Notification)

---

## 1. Enterprise / Architectural Patterns (บังคับทุกกลุ่ม)

| Pattern | ปัญหาที่แก้ | ไฟล์/คลาสที่ใช้ | Class Diagram |
|---|---|---|---|
| **Layered Architecture** | ตรรกะหน้าจอ ธุรกิจ และข้อมูลปนกัน แก้จุดหนึ่งพังทั้งระบบ | แยก package `controller` → `service` → `repository` → `domain` (ห้ามข้ามชั้น) | ดู README หัวข้อ System Architecture ⬜ |
| **MVC** | ไม่แยกหน้าจอออกจากตรรกะ | Controller: `controller/web/*`; Model: `domain/entity`, `dto`; View: `templates/*.html` (Thymeleaf) | ⬜ |
| **Repository** | โค้ด SQL/การเข้าถึง DB กระจายอยู่ในตรรกะธุรกิจ | `repository/UserRepository`, `CustomerProfileRepository`, `BranchRepository` (Spring Data JPA) และของโมดูลอื่น | ⬜ |
| **Service Layer** | ตรรกะธุรกิจและ transaction กระจายอยู่ใน Controller | `service/*Service` + `service/impl/*ServiceImpl` (`@Transactional`) | ⬜ |
| **DTO + Mapper** | เปิด Entity ออก API แล้ว `password` หลุด/ถูกแก้ field ที่ไม่ควรแก้ (mass assignment) | `dto/request/RegisterRequest` (ไม่มี `role`), `dto/response/UserResponse` (ไม่มี `password`), `mapper/UserMapper`, `mapper/BranchMapper` | ⬜ |
| **Dependency Injection (Constructor)** | คลาสสร้าง dependency เอง ผูกแน่น ทดสอบยาก | ทุก Service/Controller รับ dependency ผ่าน constructor (ไม่มี `@Autowired` บน field) เช่น `AuthServiceImpl`, `AuthApiController` | ⬜ |

## 2. GoF Patterns — กลุ่ม **Behavioral** (ทีมเลือกกลุ่มนี้ ครบ 3 แบบ)

| Pattern | ปัญหาที่แก้ | ไฟล์/คลาสที่ใช้ | ผู้เขียน | Class Diagram |
|---|---|---|---|---|
| **Strategy** | วิธีคิดราคามีหลายแบบ (ฝากซัก/ซักเอง) แก้ if-else ทุกครั้งที่เพิ่มแบบใหม่ | `PricingStrategy<I>` → `FullServicePricing`, `SelfServicePricing` | พีช, ปอนด์ | ⬜ |
| **State** | สถานะต้องเปลี่ยนตามลำดับเท่านั้น (ห้ามข้ามขั้น/ย้อนผิดกฎ) | ออเดอร์: `OrderState` และ state ทุกตัว / เครื่อง: `MachineState` และ state ทุกตัว | พีช, ปอนด์ | ⬜ |
| **Observer** | เมื่อสถานะเปลี่ยนต้องแจ้งเตือน โดยโมดูลต้นทางไม่ต้องรู้จักผู้รับ | `event/*Event` (4 ตัว) + `ApplicationEventPublisher` + `NotificationEventListener` | โชกุน (ผู้ยิง: พีช, ปอนด์) | ⬜ |

## 3. Pattern เสริม (ใส่เฉพาะที่ใช้จริง)

| Pattern | ปัญหาที่แก้ | ไฟล์/คลาสที่ใช้ | ผู้เขียน | Class Diagram |
|---|---|---|---|---|
| **Factory Method** | เลือกวิธีจ่ายเงินตาม `PaymentMethod` โดยไม่ต้อง if-else ในที่ใช้งาน | `PaymentProcessorFactory` → `Cash/QrMock/Coin Processor` | โชกุน | ⬜ |
| **Facade** | ขั้นตอน checkout หลายขั้น (คำนวณ→สร้าง payment→อัปเดตสถานะ→ยิง event) ให้ Controller เรียกครั้งเดียว | `CheckoutFacade` | โชกุน | ⬜ |
| **Builder** | สร้าง `OrderResponse` ที่มี field เยอะอ่านง่ายกว่า constructor ยาว | ⬜ | พีช | ⬜ |

---

## เช็กลิสต์ก่อนส่ง (โอ๊คเป็นคนตรวจตอนรวมเล่ม)
- [ ] ไม่มี `⬜` เหลือ และทุกไฟล์ที่อ้างถึงมีอยู่จริง
- [ ] Behavioral ครบ 3 แบบ (Strategy, State, Observer) มีที่ใช้จริงในโค้ด
- [ ] ทุก pattern มีเหตุผลว่า "ถ้าไม่ใช้จะเป็นอย่างไร"
- [ ] ลิงก์ Class Diagram ชี้ไปไฟล์ใน `doc/diagrams/` ที่มีจริง
