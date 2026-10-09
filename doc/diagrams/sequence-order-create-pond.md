# Sequence 1 — สร้าง Full-Service order

ตรวจจาก develop a21cb2a และ OrderServiceImpl ของพีช เป็น scenario ร่วมของทีม ไม่ใช่โค้ดที่ปอนด์เขียน

```mermaid
sequenceDiagram
    actor Customer
    participant Security as Security / method authorization
    participant API as OrderApiController
    participant Service as OrderServiceImpl
    participant Users as UserRepository
    participant Branches as BranchRepository
    participant Types as ServiceTypeRepository
    participant Pricing as FullServicePricing
    participant Orders as LaundryOrderRepository
    participant DB as PostgreSQL
    Customer->>Security: POST /api/v1/customers/{customerId}/orders
    Security->>API: authenticated owner, valid CreateOrderRequest
    API->>Service: create(customerId, request)
    Note over Service,DB: Spring @Transactional
    Service->>Users: findById(customerId)
    Users-->>Service: User
    Service->>Branches: findById(request.branchId)
    Branches-->>Service: Branch
    loop แต่ละรายการใน request.items
        Service->>Types: findById(serviceTypeId)
        Types-->>Service: active ServiceType
        Service->>Pricing: calculate(FullServicePricingInput)
        Pricing-->>Service: subtotal
        Note over Service: สร้าง LaundryOrderItem / รวมยอดบน server
    end
    Service->>Orders: save(order)
    Orders->>DB: persist order + cascade items
    DB-->>Orders: generated IDs
    Orders-->>Service: LaundryOrder
    Note over Service: OrderMapper.toResponse ภายใน transaction
    Service-->>API: OrderResponse
    Note over Service,DB: commit ก่อนส่ง response สำเร็จ
    API-->>Customer: 201 Created
```

`create()` ไม่ประกาศ OrderStatusChangedEvent; event ของ Order ถูกประกาศเมื่อเปลี่ยน/ยกเลิกสถานะ ห้ามวาด notification ในการสร้าง order โดยไม่มี publisher จริง หาก user/branch/service type ไม่พบหรือ service type ปิดใช้ จะหยุดก่อนบันทึกสำเร็จ

หลักฐาน: `code/src/main/java/com/laundryhub/controller/api/OrderApiController.java:46`, `code/src/main/java/com/laundryhub/service/impl/OrderServiceImpl.java:70` และ `:174` (applyItems)
