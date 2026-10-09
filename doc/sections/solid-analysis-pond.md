# SOLID — Pond: Self-service module

Scope: pricing, machine states, booking validation, repositories, machine management,
and session booking/lifecycle/payment lookup. Machine/session API and web integration
are still pending at this stage.
Paths below are relative to `code/src/main/java/com/laundryhub/`.

| Principle | Evidence | Explanation / limitation |
|---|---|---|
| SRP | `service/BookingValidator.java:16`, `mapper/MachineMapper.java:10`, `service/impl/MachineServiceImpl.java:32` | Booking time rules, DTO mapping, and machine management have separate owners. MachineService coordinates repositories and state decisions. |
| OCP | `service/pricing/SelfServicePricing.java:9`, `service/impl/SessionServiceImpl.java:42` | Time-based pricing implements the shared PricingStrategy contract; SessionService consumes that interface through constructor injection. |
| LSP | `service/state/MachineState.java:5` | Every state implements the same capability queries without throwing UnsupportedOperationException. MachineService uses the capability to reject maintenance changes while a machine is in use. |
| ISP | `domain/entity/UsageSession.java:31` | UsageSession implements the small Payable contract without depending on payment processing operations. |
| DIP | `service/impl/MachineServiceImpl.java:40` | Constructor injection supplies repository interfaces and ApplicationEventPublisher. The service implements MachineService. Mapper and registry are concrete collaborators; this is not a claim that every dependency is abstract. |
| DIP | `service/SessionPayableProvider.java:13`, `service/impl/SessionServiceImpl.java:41` | Checkout discovers PayableProvider beans. The session adapter depends on SessionService. SessionService receives repository interfaces, PricingStrategy and ApplicationEventPublisher through its constructor. BookingValidator, state registry and mapper are concrete collaborators. |
| SRP | `service/impl/SessionServiceImpl.java:56`, `mapper/SessionMapper.java:7` | SessionService coordinates booking, ownership, transactions and events; BookingValidator owns time/overlap rules, PricingStrategy owns arithmetic, and SessionMapper owns response conversion. |

MachineService returns DTOs from within a transaction, so a controller will not
serialize JPA entities or access a lazy association after leaving the service.
Machine/UsageSession now reference Branch/User using LAZY associations without
cascading removal to shared data. The original foreign keys remain unchanged.

All machine mutations are transactional. Update/delete/status operations acquire
the same machine row lock used by SessionService. A transaction alone
does not prevent two simultaneous booking requests from both seeing an empty slot.

Machine requests use Bean Validation, including monetary precision matching the
database. MachineService is `@Validated`; controllers must still use `@Valid` and
`@PreAuthorize` when added. No SecurityConfig request matchers are changed here.

SessionService.findPayable is a read-only transactional lookup, not an authorization
check. CheckoutFacade checks the owner/staff before creating payment and has an outer
transaction spanning lookup and payment. Returning a managed Payable follows the
existing order provider contract; future callers must consider its LAZY associations.

SessionService maps responses inside its transaction. Write operations lock the
machine before checking availability/overlap and inserting or changing a session.
Lifecycle operations resolve only the machine ID, then lock machine and session in
that order (`service/impl/SessionServiceImpl.java:160`). PostgreSQL integration tests
observe two waiting transactions and verify one conflicting request fails.
Cancellation is restricted to RESERVED and never frees another session's machine.
The caller must derive currentUserId/staff from authentication, never request data;
MachineApiController and SessionApiController now enforce role rules using
@PreAuthorize. They depend on service interfaces and return DTOs, never entities.
SessionApiController derives currentUserId/staff from SecurityUtils; ownership
of individual sessions remains enforced inside SessionService as well.
The MVC tests check role denial before service invocation and identity spoofing;
the PostgreSQL HTTP test verifies actual ownership enforcement across layers.
