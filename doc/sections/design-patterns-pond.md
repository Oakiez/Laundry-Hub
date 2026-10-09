# Design patterns — Pond: Self-service module

## Strategy

`SelfServicePricing` implements `PricingStrategy<SelfServicePricingInput>`.
The algorithm is `basePrice + pricePerMinute * durationMinutes` using BigDecimal.
It rejects missing/negative prices and durations outside 10–180 minutes.
Tests exercise boundary values and pricing arithmetic. SessionService integration
through the PricingStrategy interface remains the next step.

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

SessionPayableProvider now adapts SessionService.findPayable to the shared
PayableProvider interface and declares USAGE_SESSION. Spring collects it into
CheckoutFacade's provider registry through constructor injection. No checkout or
processor code is changed to add this supported payable type. Missing sessions
propagate ResourceNotFoundException; ownership remains CheckoutFacade's responsibility.

No Machine/Session HTTP endpoints or UI are delivered yet. The existing overlap
query and machine lock must be composed in SessionService and tested under
concurrency before claiming simultaneous bookings are prevented end to end.
