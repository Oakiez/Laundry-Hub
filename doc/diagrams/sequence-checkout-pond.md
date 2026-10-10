# Sequence 3 — ชำระเงินสำหรับรอบใช้งาน

ตรวจ flow จาก develop a21cb2a และตรวจซ้ำหลังรวม PR #24 ที่ 6a877cd ใช้ API ของโชกุนและ SessionPayableProvider ของปอนด์ ไม่พึ่ง getPayableSummary ซึ่งถูกลบแล้ว

```mermaid
sequenceDiagram
    actor Customer
    participant API as PaymentApiController
    participant Facade as CheckoutFacade
    participant Provider as SessionPayableProvider
    participant Sessions as SessionServiceImpl
    participant Repo as UsageSessionRepository
    participant Payments as PaymentServiceImpl
    participant Factory as PaymentProcessorFactory
    participant Processor as Cash / QrMock / CoinProcessor
    participant PaymentRepo as PaymentRepository
    participant Events as ApplicationEventPublisher
    participant Listener as NotificationEventListener
    participant Notifications as NotificationService
    Customer->>API: POST /api/v1/payments (USAGE_SESSION, payableId, method)
    API->>Facade: checkout(request, authenticated actorId, staff)
    Note over Facade,PaymentRepo: Spring @Transactional
    Facade->>Provider: findPayable(payableId)
    Provider->>Sessions: findPayable(payableId)
    Sessions->>Repo: findById(payableId)
    Repo-->>Sessions: UsageSession implements Payable
    Sessions-->>Provider: Payable
    Provider-->>Facade: Payable
    alt ไม่ใช่เจ้าของและไม่ใช่พนักงาน
        Facade-->>API: AccessDeniedException
        API-->>Customer: 403 ApiErrorResponse
    else มีสิทธิ์
        Facade->>Payments: create(payable, method)
        Payments->>PaymentRepo: ตรวจการชำระซ้ำตาม sessionId
        Payments->>Factory: getProcessor(method)
        Factory-->>Payments: processor
        Payments->>Processor: process(payment)
        Processor-->>Payments: CASH → PENDING<br/> QR_MOCK/COIN → PAID
        Note over Payments: amount จาก Payable ฝั่ง server
        Payments->>PaymentRepo: save(payment)
        PaymentRepo-->>Payments: Payment
        Payments-->>Facade: Payment
        opt status == PAID
            Facade->>Events: PaymentCompletedEvent
            Events->>Listener: on(PaymentCompletedEvent)
            Listener->>Notifications: create notification
        end
        Note over Facade: PaymentMapper.toResponse
        Facade-->>API: PaymentResponse / commit
        API-->>Customer: 201 Created
    end
```

COIN ใช้กับ USAGE_SESSION เท่านั้น CASH รอพนักงาน confirm ทาง `PATCH /api/v1/payments/{id}/confirm` แล้วจึงประกาศ PaymentCompletedEvent การชำระซ้ำตอบ 409 และไม่สร้าง payment ตัวที่สอง Diagram นี้ไม่อ้างว่ามีหน้าชำระเงินเว็บหรือบังคับจ่ายก่อน start

หลักฐาน: `code/src/main/java/com/laundryhub/controller/api/PaymentApiController.java:55`, `code/src/main/java/com/laundryhub/service/payment/CheckoutFacade.java`, `code/src/main/java/com/laundryhub/service/payment/SessionPayableProvider.java`, `test/java/com/laundryhub/service/SessionPaymentIntegrationTest.java`
