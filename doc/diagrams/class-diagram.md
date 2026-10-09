# Class Diagram — LaundryHub

> แบ่งเป็น 3 รูปเพื่อให้อ่านง่าย: (1) Domain Model ทั้งระบบ (2) โมดูลออเดอร์ฝากซักแบบครบทุก Layer พร้อมตำแหน่ง Pattern (3) Pattern ฝั่ง Payment / Notification
> สัญลักษณ์: `<|..` implements · `-->` ใช้งาน (dependency ผ่าน constructor) · `*--` composition · `..>` สร้าง/ส่งต่อ · `«...»` ชื่อ Pattern

## 1. Domain Model (Entity และความสัมพันธ์)

```mermaid
classDiagram
    direction LR

    class Payable {
        <<interface>>
        +getId() Long
        +getPayableAmount() BigDecimal
        +getPayableType() PayableType
        +getOwnerUserId() Long
    }

    class User {
        -Long id
        -String username
        -String email
        -String password
        -Role role
        -boolean enabled
        +attachProfile(CustomerProfile)
    }
    class CustomerProfile {
        -Long id
        -String fullName
        -String phone
        -String address
    }
    class Branch {
        -Long id
        -String name
        -String address
        -String phone
    }
    class ServiceType {
        -Long id
        -String name
        -BigDecimal pricePerKg
        -BigDecimal expressSurcharge
        -boolean active
    }
    class LaundryOrder {
        -Long id
        -OrderStatus status
        -boolean express
        -BigDecimal totalWeightKg
        -BigDecimal totalAmount
        -String note
        +addItem(LaundryOrderItem)
        +clearItems()
    }
    class LaundryOrderItem {
        -Long id
        -String itemName
        -BigDecimal weightKg
        -BigDecimal subtotal
    }
    class Machine {
        -Long id
        -Long branchId
        -String name
        -MachineType machineType
        -MachineStatus status
        -BigDecimal basePrice
        -BigDecimal pricePerMinute
    }
    class UsageSession {
        -Long id
        -Long userId
        -SessionStatus status
        -LocalDateTime startTime
        -LocalDateTime endTime
        -Integer durationMinutes
        -BigDecimal amount
    }
    class Payment {
        -Long id
        -Long orderId
        -Long sessionId
        -BigDecimal amount
        -PaymentMethod method
        -PaymentStatus status
        +forPayable(Payable, PaymentMethod)$ Payment
        +markPaid()
        +markFailed()
    }
    class Notification {
        -Long id
        -Long userId
        -String message
        -boolean read
        +markRead()
    }

    User "1" -- "0..1" CustomerProfile : profile, one-to-one
    LaundryOrder "*" --> "1" User : user
    LaundryOrder "*" --> "1" Branch : branch
    LaundryOrder "1" *-- "1..*" LaundryOrderItem : items (cascade ALL, orphanRemoval)
    LaundryOrderItem "*" --> "1" ServiceType : serviceType
    UsageSession "*" --> "1" Machine : machine
    Machine ..> Branch : branchId
    UsageSession ..> User : userId
    Payment ..> LaundryOrder : orderId, one-to-one
    Payment ..> UsageSession : sessionId, one-to-one
    Notification ..> User : userId

    Payable <|.. LaundryOrder
    Payable <|.. UsageSession
```

- เส้นทึบ `-->` = ความสัมพันธ์ JPA (`@ManyToOne` / `@OneToMany` / `@OneToOne`) ทุกเส้นเป็น `FetchType.LAZY`
- เส้นประ `..>` = เก็บเป็น FK แบบ `Long` ไม่ผูก entity ตรง เพื่อให้โมดูลไม่รู้จักกัน (เช่น `Payment` ไม่ต้องรู้จัก `LaundryOrder`)
- `LaundryOrder` *-- `LaundryOrderItem` เป็น composition: item ไม่มีความหมายถ้าไม่มี order ลบ order แล้ว item ถูกลบด้วย

## 2. โมดูลออเดอร์ฝากซัก (Layer + Pattern)

```mermaid
classDiagram
    direction TB

    class OrderApiController {
        <<RestController>>
        +create(customerId, CreateOrderRequest) OrderResponse
        +listForCustomer(customerId, status, Pageable) PageResponse
        +listAll(status, Pageable) PageResponse
        +get(customerId, orderId) OrderResponse
        +update(customerId, orderId, UpdateOrderRequest) OrderResponse
        +cancel(customerId, orderId)
        +changeStatus(orderId, ChangeStatusRequest) OrderResponse
    }

    class OrderService {
        <<interface>>
        +create(customerId, CreateOrderRequest) OrderResponse
        +getForCustomer(customerId, orderId) OrderResponse
        +listForCustomer(customerId, status, Pageable) PageResponse
        +listAll(status, Pageable) PageResponse
        +update(customerId, orderId, UpdateOrderRequest) OrderResponse
        +cancelByCustomer(customerId, orderId)
        +changeStatus(orderId, Action) OrderResponse
        +findPayable(orderId) Payable
    }
    class OrderServiceImpl {
        -applyItems(LaundryOrder, List)
        -advance(LaundryOrder)
        -cancel(LaundryOrder)
        -publishStatusChanged(LaundryOrder)
    }

    class LaundryOrderRepository {
        <<interface>>
        +findByUserId(Long, Pageable) Page
        +findByUserIdAndStatus(Long, OrderStatus, Pageable) Page
        +findByStatus(OrderStatus, Pageable) Page
        +findWithItemsById(Long) Optional
    }
    class ServiceTypeRepository {
        <<interface>>
        +existsByNameIgnoreCase(String) boolean
        +findByActiveTrue() List
    }

    class PricingStrategy~I~ {
        <<interface>>
        +calculate(I input) BigDecimal
    }
    class FullServicePricing {
        +calculate(FullServicePricingInput) BigDecimal
    }
    class SelfServicePricing {
        +calculate(SelfServicePricingInput) BigDecimal
    }

    class OrderState {
        <<interface>>
        +status() OrderStatus
        +next() Optional~OrderState~
        +canCancel() boolean
    }
    class OrderStateFactory {
        +from(OrderStatus)$ OrderState
    }
    class ReceivedState
    class WashingState
    class DryingState
    class IroningState
    class ReadyState
    class PickedUpState
    class CancelledState

    class OrderMapper {
        +toResponse(LaundryOrder) OrderResponse
    }
    class OrderResponse {
        <<record>>
        +builder()$ OrderResponseBuilder
    }

    class OrderStatusChangedEvent {
        <<record>>
        +orderId Long
        +userId Long
        +newStatus OrderStatus
    }
    class NotificationEventListener {
        +on(OrderStatusChangedEvent)
    }

    class PayableProvider {
        <<interface>>
        +supports() PayableType
        +findPayable(Long) Payable
    }
    class OrderPayableProvider
    class CheckoutFacade

    OrderApiController --> OrderService : DIP
    OrderService <|.. OrderServiceImpl
    OrderServiceImpl --> LaundryOrderRepository : «Repository»
    OrderServiceImpl --> ServiceTypeRepository : «Repository»
    OrderServiceImpl --> PricingStrategy : «Strategy»
    PricingStrategy <|.. FullServicePricing
    PricingStrategy <|.. SelfServicePricing
    OrderServiceImpl ..> OrderStateFactory : «State»
    OrderStateFactory ..> OrderState : creates
    OrderState <|.. ReceivedState
    OrderState <|.. WashingState
    OrderState <|.. DryingState
    OrderState <|.. IroningState
    OrderState <|.. ReadyState
    OrderState <|.. PickedUpState
    OrderState <|.. CancelledState
    OrderServiceImpl --> OrderMapper : «DTO + Mapper»
    OrderMapper ..> OrderResponse : «Builder»
    OrderServiceImpl ..> OrderStatusChangedEvent : publishEvent «Observer»
    NotificationEventListener ..> OrderStatusChangedEvent : @EventListener
    PayableProvider <|.. OrderPayableProvider
    OrderPayableProvider --> OrderService
    CheckoutFacade --> PayableProvider : List of providers «DIP / OCP»
```

## 3. Payment / Notification (Factory, Facade, Observer)

```mermaid
classDiagram
    direction LR

    class CheckoutFacade {
        <<Facade>>
        +checkout(CheckoutRequest, userId, staff) PaymentResponse
        +confirmPayment(paymentId) PaymentResponse
        +getPayment(paymentId, userId, staff) PaymentResponse
    }
    class PayableProvider {
        <<interface>>
    }
    class PaymentService {
        <<interface>>
        +create(Payable, PaymentMethod) Payment
        +confirm(paymentId) Payment
    }
    class PaymentServiceImpl
    class PaymentProcessorFactory {
        +getProcessor(PaymentMethod) PaymentProcessor
    }
    class PaymentProcessor {
        <<interface>>
        +method() PaymentMethod
        +process(Payment) PaymentStatus
    }
    class CashProcessor
    class QrMockProcessor
    class PaymentCompletedEvent {
        <<record>>
    }
    class NotificationEventListener {
        +on(OrderStatusChangedEvent)
        +on(SessionStatusChangedEvent)
        +on(PaymentCompletedEvent)
    }
    class NotificationService {
        <<interface>>
        +notifyUser(userId, message)
    }

    CheckoutFacade --> PayableProvider : find payable
    CheckoutFacade --> PaymentService
    CheckoutFacade ..> PaymentCompletedEvent : publishEvent «Observer»
    PaymentService <|.. PaymentServiceImpl
    PaymentServiceImpl --> PaymentProcessorFactory : «Factory Method»
    PaymentProcessorFactory ..> PaymentProcessor : selects by method
    PaymentProcessor <|.. CashProcessor
    PaymentProcessor <|.. QrMockProcessor
    NotificationEventListener ..> PaymentCompletedEvent : @EventListener
    NotificationEventListener --> NotificationService
```

## ตำแหน่ง Design Pattern ในระบบ

| Pattern | กลุ่ม | คลาส | ไฟล์ | เจ้าของ |
|---|---|---|---|---|
| Strategy | Behavioral | `PricingStrategy` ← `FullServicePricing`, `SelfServicePricing` | `service/pricing/` | พีช, ปอนด์ |
| State | Behavioral | `OrderState` ← 7 สถานะ + `OrderStateFactory` | `service/state/` | พีช |
| Observer | Behavioral | `OrderStatusChangedEvent`, `PaymentCompletedEvent` → `NotificationEventListener` | `event/` | พีช (ยิง), โชกุน (ฟัง) |
| Factory Method | Creational | `PaymentProcessorFactory` → `PaymentProcessor` | `service/payment/` | โชกุน |
| Facade | Structural | `CheckoutFacade` | `service/payment/CheckoutFacade.java` | โชกุน |
| Builder | Creational | `OrderResponse.builder()` (Lombok `@Builder`) | `dto/response/OrderResponse.java` | พีช |
| Repository | Enterprise | `*Repository extends JpaRepository` | `repository/` | ทุกคน |
| Service Layer | Enterprise | `*Service` (interface) + `impl/*ServiceImpl` | `service/` | ทุกคน |
| DTO + Mapper | Enterprise | `dto/request`, `dto/response`, `mapper/*Mapper` | `dto/`, `mapper/` | ทุกคน |
| Dependency Injection | Enterprise | constructor injection ทุก `@Service` / `@RestController` | ทั้งระบบ | ทุกคน |
| Adapter | Structural | `AppUserDetails` แปลง `User` ให้ Spring Security ใช้ | `security/AppUserDetails.java` | โอ๊ค |

> รูปนี้สะท้อนโค้ดใน `develop` ณ วันที่ 9 ต.ค. 2569 — ถ้าโมดูลเครื่องซัก (Machine State, Session service) เข้ามาเพิ่ม ให้เจ้าของโมดูลเติมลงในรูปที่ 1–2 และตาราง
