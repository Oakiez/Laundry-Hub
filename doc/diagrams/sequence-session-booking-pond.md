# Sequence 2 — จอง เริ่ม และจบรอบ Self-Service

ตรวจจาก develop a21cb2a; หน้าเว็บใน PR นี้ใช้ SessionService เดียวกับ API

```mermaid
sequenceDiagram
    actor Customer
    participant Controller as SessionApiController / SelfServiceWebController
    participant Service as SessionServiceImpl
    participant MachineRepo as MachineRepository
    participant Validator as BookingValidator
    participant Sessions as UsageSessionRepository
    participant Pricing as SelfServicePricing
    participant State as MachineStateFactory / MachineState
    participant Events as ApplicationEventPublisher
    participant Listener as NotificationEventListener
    participant Notifications as NotificationService
    Customer->>Controller: book(machineId, startTime, duration)
    Note over Controller: authentication + validation<br/> web POST ตรวจ CSRF
    Controller->>Service: book(machineId, actorId, staff, request)
    Note over Service: transaction B: ตรวจ owner + enabled CUSTOMER
    Service->>MachineRepo: findByIdForUpdate(machineId)
    MachineRepo-->>Service: Machine (PESSIMISTIC_WRITE)
    Service->>Validator: validate(machine, startTime, duration)
    Validator->>Sessions: existsOverlap(machineId, start, end)
    Sessions-->>Validator: false
    Validator-->>Service: endTime
    Service->>Pricing: calculate(basePrice, pricePerMinute, duration)
    Pricing-->>Service: amount
    Service->>Sessions: saveAndFlush(RESERVED session)
    Note over Service: การจองไม่เปลี่ยน Machine เป็น RESERVED
    Service->>Events: SessionStatusChangedEvent(RESERVED)
    Events->>Listener: synchronous on(SessionStatusChangedEvent)
    Listener->>Notifications: create notification
    Service-->>Controller: SessionResponse / commit B
    Controller-->>Customer: API 201 / web redirect to history
    Customer->>Controller: start(sessionId)
    Controller->>Service: start(sessionId, actorId, staff)
    Note over Service: transaction S: machine ID query → lock Machine → lock Session
    Service->>Sessions: findMachineIdBySessionId(sessionId)
    Sessions-->>Service: machineId
    Service->>MachineRepo: findByIdForUpdate(machineId)
    Service->>Sessions: findByIdForUpdate(sessionId)
    Service->>State: getState(machine.status).canStart()
    State-->>Service: true for AVAILABLE
    Note over Service: ตรวจ owner + RESERVED<br/> Session/Machine → IN_USE
    Service->>Sessions: flush()
    Service->>Events: MachineStatusChangedEvent(IN_USE)
    Note over Events: ยังไม่มี listener ของ Machine event
    Service->>Events: SessionStatusChangedEvent(IN_USE)
    Events->>Listener: on(SessionStatusChangedEvent)
    Listener->>Notifications: create notification
    Service-->>Controller: SessionResponse / commit S
    Controller-->>Customer: API 200 / web redirect
    Customer->>Controller: finish(sessionId)
    Controller->>Service: finish(sessionId, actorId, staff)
    Note over Service: transaction F: machine ID query → lock Machine → lock Session
    Service->>State: getState(machine.status).canFinish()
    State-->>Service: true for IN_USE
    Note over Service: ตรวจ owner + IN_USE<br/> Session → COMPLETED, Machine → AVAILABLE
    Service->>Sessions: flush()
    Service->>Events: MachineStatusChangedEvent(AVAILABLE)
    Service->>Events: SessionStatusChangedEvent(COMPLETED)
    Events->>Listener: on(SessionStatusChangedEvent)
    Listener->>Notifications: create notification
    Service-->>Controller: SessionResponse / commit F
    Controller-->>Customer: API 200 / web redirect
```

- lock ของ Machine อยู่จน transaction จบ ครอบคลุม overlap check และ insert; overlap ตรวจเฉพาะ RESERVED/IN_USE และช่วงติดกันยอมรับได้
- ราคา = basePrice + pricePerMinute × durationMinutes; web ไม่รับ owner หรือ amount จาก form
- เมื่อ overlap เกิดขึ้น API ตอบ 409; เว็บคืน form พร้อม error โดยยังเก็บค่าที่กรอก Wrong owner ตอบ 403
- Listener แจ้งเตือนทำงาน synchronous ใน transaction เดียวกัน หาก listener ล้มเหลว การบันทึกสถานะ rollback ด้วย
- start ยังไม่บังคับ payment prerequisite หรือ clock window; cancel ทำได้เฉพาะ RESERVED และไม่เปลี่ยน Machine ของรอบอื่น

หลักฐาน: `code/src/main/java/com/laundryhub/service/impl/SessionServiceImpl.java:56`, `:83`, `:98`, `:160`, `:178`; เทสต์การแข่งขันและ rollback: `test/java/com/laundryhub/service/SessionLifecycleIntegrationTest.java`
