# Design patterns — Pond: Self-service module

## Strategy

`SelfServicePricing` implements `PricingStrategy<SelfServicePricingInput>`.
The algorithm is `basePrice + pricePerMinute * durationMinutes` using BigDecimal.
It rejects missing/negative prices and durations outside 10–180 minutes.
Tests exercise boundary values and pricing arithmetic. SessionService receives
PricingStrategy through constructor injection (`service/impl/SessionServiceImpl.java:42`)
and persists a server-calculated amount when booking.

## State

MachineState declares `status`, `canStart`, `canFinish`, and `canSetOutOfService`.
AvailableState, ReservedState, InUseState, and OutOfServiceState supply the
capabilities. MachineService consults the current state's maintenance capability
before applying a manual status change. Starting/finishing a machine belongs to
the session lifecycle and cannot be bypassed through this manual operation.

MachineStateFactory is a **simple registry factory**, not the GoF Factory Method
pattern. Constructor injection collects implementations and rejects missing or
duplicate status registrations. Do not count this registry as another GoF pattern.

## Observer through Spring application events

MachineService publishes MachineStatusChangedEvent only when the status actually
changes. Repeating the current status is a no-op without another event. The service
depends on ApplicationEventPublisher, not a particular notification implementation.
A consumer for machine maintenance notifications is optional and not implemented
in this change. Publishing an event does not by itself guarantee delivery after a
transaction commits; a future consumer must choose appropriate transaction semantics.

## Repository and Service Layer

MachineRepository and UsageSessionRepository encapsulate persistence queries.
MachineServiceImpl coordinates duplicate checks, reference validation, lifecycle
restrictions, mapping and transactions. Database uniqueness remains authoritative
if two creates pass the preliminary duplicate-name check simultaneously.

Additional management rules in this implementation: preserve machines with session
history instead of deleting them; disallow moving/changing their type once they have
history. Manual status changes accept AVAILABLE/OUT_OF_SERVICE only; IN_USE is owned
by SessionService. These rules protect booking history and should be reviewed with
the team along with the core brief requirements.

## Current limits

SessionPayableProvider adapts SessionService.findPayable to the shared
PayableProvider interface and declares USAGE_SESSION. Spring collects it into
CheckoutFacade's provider registry through constructor injection. No checkout or
processor code is changed to add this supported payable type. Missing sessions
propagate ResourceNotFoundException; ownership remains CheckoutFacade's responsibility.

SessionService now composes the overlap query and machine lock in one transaction.
Booking remains RESERVED while the machine stays AVAILABLE. Start checks canStart,
changes both states to IN_USE; finish checks canFinish and releases the machine.
Cancel accepts RESERVED only and changes no machine status.

Session and machine events are published after flushing changes
(`service/impl/SessionServiceImpl.java:178`). The existing synchronous session
notification listener joins the transaction. Integration tests deliberately reject
a notification insert and verify session/machine changes roll back together.
Machine events currently have no notification consumer.

MachineApiController and SessionApiController now expose DTOs through their service
interfaces with constructor injection, @Valid requests, @PreAuthorize role rules
and PageResponse pagination. User identity/staff flags come from SecurityUtils.
UI remains pending. Start follows the brief's
session/machine status rules; it does not enforce a clock window or payment prerequisite.
Wrong lifecycle states use BusinessRuleException (400), following the shared handler
and the brief's unit-test contract; the API table's proposed 409 needs team agreement
with the team; the implemented endpoints retain the shared 400 mapping.
See doc/self-service-api.md for endpoints and verified HTTP behavior.
Database concurrency tests are opt-in via
LAUNDRY_DB_TESTS=true and run against an isolated generated PostgreSQL schema.
