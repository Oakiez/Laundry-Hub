# SOLID — Pond: Self-service module

Scope: pricing, machine states, booking validation, repositories, and machine management.
SessionService now supplies payment lookup; booking/lifecycle methods and machine/session
API/web integration are still pending at this stage.
Paths below are relative to `code/src/main/java/com/laundryhub/`.

| Principle | Evidence | Explanation / limitation |
|---|---|---|
| SRP | `service/BookingValidator.java:16`, `mapper/MachineMapper.java:10`, `service/impl/MachineServiceImpl.java:32` | Booking time rules, DTO mapping, and machine management have separate owners. MachineService coordinates repositories and state decisions. |
| OCP | `service/pricing/SelfServicePricing.java:9` | Time-based pricing implements the shared PricingStrategy contract; another pricing implementation can be added independently. SessionService will consume that interface in the next stage. |
| LSP | `service/state/MachineState.java:5` | Every state implements the same capability queries without throwing UnsupportedOperationException. MachineService uses the capability to reject maintenance changes while a machine is in use. |
| ISP | `domain/entity/UsageSession.java:31` | UsageSession implements the small Payable contract without depending on payment processing operations. |
| DIP | `service/impl/MachineServiceImpl.java:40` | Constructor injection supplies repository interfaces and ApplicationEventPublisher. The service implements MachineService. Mapper and registry are concrete collaborators; this is not a claim that every dependency is abstract. |
| DIP | `service/SessionPayableProvider.java:13`, `service/impl/SessionServiceImpl.java:15` | Checkout discovers PayableProvider beans. The session adapter depends on SessionService; its implementation uses UsageSessionRepository. Payment does not need a session repository dependency. |

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
