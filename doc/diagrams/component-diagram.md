# Component Diagram

แสดงส่วนประกอบหลักของ LaundryHub และการพึ่งพากัน ตามสถาปัตยกรรมแบบชั้น (Layered) กฎคือ **ลูกศรไปได้เฉพาะชั้นล่างลงไป** และ Controller ไม่เรียก Repository โดยตรง

```mermaid
flowchart TB
    Browser["เบราว์เซอร์ผู้ใช้<br/>(Thymeleaf + Bootstrap 5)"]
    Swagger["Swagger UI / REST client<br/>(HTTP Basic)"]

    subgraph APP["LaundryHub — Spring Boot 3.5.7 (Java 17)"]
        direction TB

        subgraph SEC["Security (ด่านหน้าของทุกคำขอ)"]
            SF["SecurityFilterChain<br/>SecurityConfig"]
            UDS["AppUserDetailsService<br/>AppUserDetails"]
            ERR["ApiAuthenticationEntryPoint<br/>ApiAccessDeniedHandler"]
        end

        subgraph PRES["Presentation"]
            WEB["Web Controllers<br/>controller/web/*<br/>(คืนหน้า Thymeleaf)"]
            API["REST Controllers<br/>controller/api/*<br/>(คืน JSON, @Valid)"]
            GEH["GlobalExceptionHandler<br/>(@RestControllerAdvice)"]
        end

        subgraph SVC["Service Layer (interface + impl, @Transactional)"]
            direction LR
            AUTH["Auth / User / Branch<br/>Service"]
            ORD["OrderService<br/>+ OrderState (State)"]
            MCH["MachineService / SessionService<br/>+ BookingValidator<br/>+ MachineState (State)"]
            PAY["PaymentService<br/>CheckoutFacade (Facade)<br/>PaymentProcessorFactory (Factory)"]
            NTF["NotificationService"]
            PRC["PricingStrategy (Strategy)<br/>FullServicePricing<br/>SelfServicePricing"]
            PRV["PayableProvider<br/>OrderPayableProvider<br/>SessionPayableProvider"]
        end

        EVT[["Spring Event Bus<br/>ApplicationEventPublisher<br/>NotificationEventListener (Observer)"]]
        DTO["DTO + Mapper<br/>dto/*  ·  mapper/*"]

        subgraph DATA["Data Access"]
            REPO["Repositories<br/>Spring Data JPA"]
            ENT["Entities + Enums<br/>domain/*"]
            FLY["Flyway<br/>V1 schema · V2 seed"]
        end
    end

    DB[("PostgreSQL 16<br/>(Neon)")]

    Browser -->|HTTPS| SF
    Swagger -->|HTTPS| SF
    SF --- UDS
    SF --- ERR
    SF --> WEB
    SF --> API
    API -.->|exception| GEH

    WEB --> AUTH & ORD & MCH & PAY & NTF
    API --> AUTH & ORD & MCH & PAY & NTF
    WEB -.-> DTO
    API -.-> DTO

    ORD --> PRC
    MCH --> PRC
    PAY --> PRV
    PRV --> ORD
    PRV --> MCH

    ORD -->|publishEvent| EVT
    MCH -->|publishEvent| EVT
    PAY -->|publishEvent| EVT
    EVT -->|เรียก| NTF

    AUTH --> REPO
    ORD --> REPO
    MCH --> REPO
    PAY --> REPO
    NTF --> REPO
    REPO --> ENT
    REPO -->|JDBC + SSL| DB
    FLY -->|migrate ตอนสตาร์ท| DB
    UDS --> REPO
```

## คำอธิบายส่วนประกอบ

| ส่วนประกอบ | หน้าที่ | ตัวอย่างในโค้ด |
|---|---|---|
| **Security** | ตรวจตัวตนและสิทธิ์ก่อนถึง Controller รองรับฟอร์มล็อกอิน (หน้าเว็บ) และ HTTP Basic (API) คืน 401/403 เป็น JSON มาตรฐานสำหรับ `/api/**` | `config/SecurityConfig`, `security/*` |
| **Presentation** | รับคำขอ ตรวจข้อมูลด้วย `@Valid` เรียก Service และคืนผล (หน้าเว็บ/JSON) ไม่มีตรรกะธุรกิจ | `controller/web/*`, `controller/api/*` |
| **GlobalExceptionHandler** | แปลง exception จากทุก REST Controller เป็น `ApiErrorResponse` รูปแบบเดียวกัน (400/404/409/500) | `exception/GlobalExceptionHandler` |
| **Service Layer** | ตรรกะธุรกิจและ transaction แต่ละโมดูลพึ่งพา interface ไม่ใช่ implementation (DIP) | `service/*`, `service/impl/*` |
| **Strategy (Pricing)** | วิธีคิดราคาสลับได้โดยไม่แก้ผู้เรียก | `service/pricing/*` |
| **State** | ควบคุมลำดับการเปลี่ยนสถานะออเดอร์และเครื่อง | `service/state/*` |
| **PayableProvider** | ให้ Payment หา "สิ่งที่ต้องจ่าย" โดยไม่รู้จักโมดูล Order/Session ตรงๆ (DIP + ISP) | `service/payment/PayableProvider` |
| **Spring Event Bus** | ผู้เปลี่ยนสถานะแค่ `publishEvent` ส่วนการแจ้งเตือนเป็นผู้ฟัง (Observer) โมดูลต้นทางไม่รู้จักระบบแจ้งเตือน | `event/*` |
| **DTO + Mapper** | ไม่เปิด Entity ออก API (เช่น ไม่ให้ `password` หลุด) | `dto/*`, `mapper/*` |
| **Data Access** | Repository (Spring Data JPA) + Entity + Flyway migration | `repository/*`, `domain/*`, `db/migration/*` |
| **PostgreSQL (Neon)** | ฐานข้อมูลหลัก 10 ตาราง | ดู `er-diagram.md` |

## ส่วนประกอบภายนอกที่ระบบพึ่งพา
- **PostgreSQL บน Neon** (ผ่าน JDBC + SSL)
- **Bootstrap 5** โหลดจาก CDN โดยเบราว์เซอร์ (ไม่ผ่านเซิร์ฟเวอร์ของเรา)
- **springdoc-openapi** สร้างหน้า Swagger UI ที่ `/swagger-ui.html`

---

**ตรวจก่อนส่ง (โอ๊ค):** ชื่อคลาสในแผนภาพต้องมีอยู่จริงใน `main` (โดยเฉพาะ `OrderService`, `MachineService`/`SessionService`, `OrderPayableProvider`/`SessionPayableProvider`, `*State`)
