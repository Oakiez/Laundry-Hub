# State Diagram — Machine และ UsageSession

รูปแสดง transition ที่ Service อนุญาตจริง ไม่อนุมานจากการมีค่า enum เพียงอย่างเดียว

```mermaid
stateDiagram-v2
    [*] --> AVAILABLE: create machine
    AVAILABLE --> IN_USE: start RESERVED session / owner or staff
    IN_USE --> AVAILABLE: finish IN_USE session / owner or staff
    AVAILABLE --> OUT_OF_SERVICE: manual maintenance / STAFF or ADMIN
    OUT_OF_SERVICE --> AVAILABLE: reopen / STAFF or ADMIN
    RESERVED --> AVAILABLE: manual reset / STAFF or ADMIN
    RESERVED --> OUT_OF_SERVICE: manual maintenance / STAFF or ADMIN
    note right of RESERVED
        enum และ State class รองรับไว้
        การจองปัจจุบันไม่เปลี่ยน Machine เป็น RESERVED
        ไม่มี transition ปกติเข้าสถานะนี้
    end note
    note right of IN_USE
        ปิดซ่อม/เปลี่ยนสถานะด้วยมือไม่ได้
        ต้อง finish ผ่าน SessionService
    end note
```

```mermaid
stateDiagram-v2
    [*] --> RESERVED: book / validate overlap + server pricing
    RESERVED --> IN_USE: start / Machine.canStart
    RESERVED --> CANCELLED: cancel
    IN_USE --> COMPLETED: finish / Machine.canFinish
    COMPLETED --> [*]
    CANCELLED --> [*]
    note right of CANCELLED
        ไม่เปลี่ยนสถานะเครื่อง
        จึงไม่ปล่อยเครื่องที่รอบอื่นกำลังใช้
    end note
```

- booking ใช้ lock Machine + ตรวจช่วงเวลาทั้ง RESERVED/IN_USE แต่ไม่เปลี่ยนสถานะเครื่อง เพราะอาจจองในอนาคต
- start/finish ล็อก Machine แล้ว Session ตรวจเจ้าของและเปลี่ยนทั้งสอง entity ใน transaction เดียว
- State classes ให้ capability; `MachineStateFactory` เป็น simple registry factory ไม่ใช่ GoF Factory Method
- ความผิดสถานะตอบ BusinessRuleException (400 ตาม shared handler); maintenance ระหว่าง IN_USE ตอบ BookingConflictException (409)
- event ของ Session มี NotificationEventListener; event ของ Machine ยังไม่มี listener

หลักฐาน: `code/src/main/java/com/laundryhub/service/impl/MachineServiceImpl.java:109`, `code/src/main/java/com/laundryhub/service/impl/SessionServiceImpl.java:83`, `:98`, `:113` และ `code/src/main/java/com/laundryhub/service/state/`
