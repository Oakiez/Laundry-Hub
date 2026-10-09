# Class Diagram — LaundryHub

> แบ่งเป็น 4 รูปเพื่อให้อ่านง่าย: (1) Domain Model ทั้งระบบ (2) โมดูลออเดอร์ฝากซัก + ประเภทบริการ (3) โมดูลซักเอง (เครื่อง/รอบใช้งาน) (4) Payment / Notification
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
        -String name
        -MachineType machineType
        -MachineStatus status
        -BigDecimal basePrice
        -BigDecimal pricePerMinute
    }
    class UsageSession {
        -Long id
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
    Machine "*" --> "1" Branch : branch
    UsageSession "*" --> "1" Machine : machine
    UsageSession "*" --> "1" User : user
    Payment ..> LaundryOrder : orderId, one-to-one
    Payment ..> UsageSession : sessionId, one-to-one
    Notification ..> User : userId

    Payable <|.. LaundryOrder
    Payable <|.. UsageSession
```

- เส้นทึบ `-->` = ความสัมพันธ์ JPA (`@ManyToOne` / `@OneToMany` / `@OneToOne`) ทุกเส้นเป็น `FetchType.LAZY`
- เส้นประ `..>` = เก็บเป็น FK แบบ `Long` ไม่ผูก entity ตรง เพื่อให้โมดูลไม่รู้จักกัน (เช่น `Payment` ไม่ต้องรู้จัก `LaundryOrder`)
- `LaundryOrder` *-- `LaundryOrderItem` เป็น composition: item ไม่มีความหมายถ้าไม่มี order ลบ order แล้ว item ถูกลบด้วย
- `ServiceType` ไม่ถูกลบจริง (soft delete ด้วย `active = false`) เพราะ `LaundryOrderItem` ของออเดอร์เก่ายังอ้างถึง

## 2. โมดูลออเดอร์ฝากซัก + ประเภทบริการ (Layer + Pattern)

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
    class ServiceTypeApiController {
        <<RestController>>
        +findActive() List
        +findById(id) ServiceTypeResponse
        +create(ServiceTypeRequest) ServiceTypeResponse
        +update(id, ServiceTypeRequest) ServiceTypeResponse
        +deactivate(id)
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
    class ServiceTypeService {
        <<interface>>
        +findActive() List
        +findById(id) ServiceTypeResponse
        +create(ServiceTypeRequest) ServiceTypeResponse
        +update(id, ServiceTypeRequest) ServiceTypeResponse
        +deactivate(id)
    }
    class ServiceTypeServiceImpl

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
        +existsByNameIgnoreCaseAndIdNot(String, Long) boolean
        +findByActiveTrueOrderByNameAsc() List
    }

    class PricingStrategy~I~ {
        <<interface>>
        +calculate(I input) BigDecimal
    }
    class FullServicePricing {
        +calculate(FullServicePricingInput) BigDecimal
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
    class ServiceTypeMapper {
        +toResponse(ServiceType) ServiceTypeResponse
        +toEntity(ServiceTypeRequest) ServiceType
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

    OrderApiController --> OrderService : DIP
    ServiceTypeApiController --> ServiceTypeService : DIP
    OrderService <|.. OrderServiceImpl
    ServiceTypeService <|.. ServiceTypeServiceImpl
    OrderServiceImpl --> LaundryOrderRepository : «Repository»
    OrderServiceImpl --> ServiceTypeRepository : «Repository»
    ServiceTypeServiceImpl --> ServiceTypeRepository : «Repository»
    ServiceTypeServiceImpl --> ServiceTypeMapper : «DTO + Mapper»
    OrderServiceImpl --> PricingStrategy : «Strategy»
    PricingStrategy <|.. FullServicePricing
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
```

## 3. โมดูลซักเอง: เครื่องและรอบใช้งาน (Layer + Pattern)

```mermaid
classDiagram
    direction TB

    class MachineService {
        <<interface>>
        +search(branchId, status, type, Pageable) Page
        +findById(id) MachineResponse
        +create(MachineRequest) MachineResponse
        +update(id, MachineRequest) MachineResponse
        +delete(id)
        +changeStatus(id, MachineStatus) MachineResponse
    }
    class MachineServiceImpl

    class MachineState {
        <<interface>>
        +status() MachineStatus
        +canStart() boolean
        +canFinish() boolean
        +canSetOutOfService() boolean
    }
    class MachineStateFactory {
        +getState(MachineStatus) MachineState
    }
    class AvailableState
    class ReservedState
    class InUseState
    class OutOfServiceState

    class MachineRepository {
        <<interface>>
    }
    class UsageSessionRepository {
        <<interface>>
    }
    class MachineMapper
    class BookingValidator {
        +validate(Machine, startTime, durationMinutes) LocalDateTime
    }

    class PricingStrategy~I~ {
        <<interface>>
        +calculate(I input) BigDecimal
    }
    class SelfServicePricing {
        +calculate(SelfServicePricingInput) BigDecimal
    }

    class SessionService {
        <<interface>>
        +findPayable(sessionId) Payable
    }
    class SessionServiceImpl
    class PayableProvider {
        <<interface>>
    }
    class SessionPayableProvider

    class MachineStatusChangedEvent {
        <<record>>
    }

    MachineService <|.. MachineServiceImpl
    MachineServiceImpl --> MachineRepository : «Repository»
    MachineServiceImpl --> UsageSessionRepository : «Repository»
    MachineServiceImpl --> MachineMapper : «DTO + Mapper»
    MachineServiceImpl --> MachineStateFactory : «State»
    MachineStateFactory ..> MachineState : looks up by status
    MachineState <|.. AvailableState
    MachineState <|.. ReservedState
    MachineState <|.. InUseState
    MachineState <|.. OutOfServiceState
    MachineServiceImpl ..> MachineStatusChangedEvent : publishEvent «Observer»
    BookingValidator --> UsageSessionRepository : overlap check
    PricingStrategy <|.. SelfServicePricing
    SessionService <|.. SessionServiceImpl
    SessionServiceImpl --> UsageSessionRepository : «Repository»
    PayableProvider <|.. SessionPayableProvider
    SessionPayableProvider --> SessionService
```

- `MachineStateFactory` ได้ state ทุกตัวจาก Spring (`List<MachineState>`) แล้วเก็บใน `EnumMap` ต่างจาก `OrderStateFactory` ที่ใช้ `switch` แบบ static ทั้งคู่ทำหน้าที่เดียวกันคือแปลง enum ในฐานข้อมูลเป็นคลาส State
- ณ วันที่ปรับรูปนี้ `BookingValidator` และ `SelfServicePricing` ยังไม่มี Service ตัวไหนเรียกใช้ (มีเทสต์ของตัวเองแล้ว) ส่วน `MachineStatusChangedEvent` ยังไม่มี listener รูปนี้จึงไม่ได้วางเส้นจาก Service ไปหาคลาสเหล่านั้น

## 4. Payment / Notification (Strategy + Factory, Facade, Observer)

```mermaid
classDiagram
    direction LR

    class PaymentApiController {
        <<RestController>>
    }
    class CheckoutFacade {
        <<Facade>>
        +checkout(CheckoutRequest, userId, staff) PaymentResponse
        +confirmPayment(paymentId) PaymentResponse
        +getPayment(paymentId, userId, staff) PaymentResponse
    }
    class PayableProvider {
        <<interface>>
        +supports() PayableType
        +findPayable(Long) Payable
    }
    class OrderPayableProvider
    class SessionPayableProvider
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
    class CoinProcessor
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

    PaymentApiController --> CheckoutFacade
    CheckoutFacade --> PayableProvider : List of providers «DIP / OCP»
    PayableProvider <|.. OrderPayableProvider
    PayableProvider <|.. SessionPayableProvider
    CheckoutFacade --> PaymentService
    CheckoutFacade ..> PaymentCompletedEvent : publishEvent «Observer»
    PaymentService <|.. PaymentServiceImpl
    PaymentServiceImpl --> PaymentProcessorFactory : «Factory»
    PaymentProcessorFactory ..> PaymentProcessor : selects by method
    PaymentProcessor <|.. CashProcessor
    PaymentProcessor <|.. QrMockProcessor
    PaymentProcessor <|.. CoinProcessor
    NotificationEventListener ..> PaymentCompletedEvent : @EventListener
    NotificationEventListener --> NotificationService
```

## ตำแหน่ง Design Pattern ในระบบ

| Pattern | กลุ่ม | คลาส | ไฟล์ | เจ้าของ |
|---|---|---|---|---|
| Strategy | Behavioral | `PricingStrategy` ← `FullServicePricing`, `SelfServicePricing` · `PaymentProcessor` ← `Cash/QrMock/CoinProcessor` | `service/pricing/`, `service/payment/` | พีช, ปอนด์, โชกุน |
| State | Behavioral | `OrderState` ← 7 สถานะ + `OrderStateFactory` · `MachineState` ← 4 สถานะ + `MachineStateFactory` | `service/state/` | พีช, ปอนด์ |
| Observer | Behavioral | `OrderStatusChangedEvent`, `MachineStatusChangedEvent`, `PaymentCompletedEvent` → `NotificationEventListener` | `event/` | พีช, ปอนด์ (ยิง), โชกุน (ยิง/ฟัง) |
| Factory (Simple Factory แบบ registry) | Creational | `PaymentProcessorFactory` → `PaymentProcessor` | `service/payment/` | โชกุน |
| Facade | Structural | `CheckoutFacade` | `service/payment/CheckoutFacade.java` | โชกุน |
| Builder | Creational | `OrderResponse.builder()` (Lombok `@Builder`) | `dto/response/OrderResponse.java` | พีช |
| Adapter | Structural | `AppUserDetails` แปลง `User` ให้ Spring Security ใช้ | `security/AppUserDetails.java` | โอ๊ค |
| Repository | Enterprise | `*Repository extends JpaRepository` | `repository/` | ทุกคน |
| Service Layer | Enterprise | `*Service` (interface) + `impl/*ServiceImpl` | `service/` | ทุกคน |
| DTO + Mapper | Enterprise | `dto/request`, `dto/response`, `mapper/*Mapper` | `dto/`, `mapper/` | ทุกคน |
| Dependency Injection | Enterprise | constructor injection ทุก `@Service` / `@RestController` | ทั้งระบบ | ทุกคน |

> รูปนี้สะท้อนโค้ดใน `develop` ณ วันที่ 10 ต.ค. 2569 (หลัง merge PR #17 และ #18) ถ้าเพิ่มคลาสใหม่ (เช่น controller ของเครื่อง/รอบใช้งาน) ให้เจ้าของโมดูลเติมลงในรูปที่ 3 และตาราง
