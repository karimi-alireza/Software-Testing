# CA2: test design, answers, and walkthrough

## Scope and sources

This solution completes the testing work for SWT-05-CA2.pdf. It was prepared with
AI assistance, which page 1 explicitly allows. The two students still need to
review the code, understand the tests, perform the external submission, and
answer the oral assessment themselves.

The test strategy applies these local course materials:

- [Assignment](../SWT-05-CA2.pdf), pp. 1-3: learning objective, two engine questions, nine Mockito concepts, submission rules.
- [Effective test design](../../../../Slides/05-Principles-of-Effective-Test-Design.pdf), pp. 3, 8, 10-15, 18, 20, 22, 24-28: traceability, risk, simple cases, partitions, boundaries, state transitions, independence, positive/negative testing, review, clear oracles.
- [Automation principles](../../../../Slides/06-Principals%20of%20Test%20Automation.pdf), pp. 4-7, 11-12: self-checking tests, front-door access, isolate dependencies, keep test logic out of production, avoid replacing the SUT.
- [xUnit/JUnit](../../../../Slides/07-xUnit-Junit.pdf), pp. 3-6: direct/indirect control and observation points, fixture strategy.
- [Fixtures and verification](../../../../Slides/09-Fixture-Management-and-Result-Verification.pdf), pp. 3-9, 13, 26-31: transient fresh fixtures, implicit/delegated setup, garbage-collected teardown, state and behavior verification.
- [Test doubles](../../../../Slides/10-Test-Doubles.pdf), pp. 2-14: indirect inputs/outputs, stub responders and saboteurs, mocks, injection.
- [Test organization](../../../../Slides/11-Organizing-Our-Tests%20and-A-Road-Map-to-Effective-Test-Automation.pdf), pp. 2-3, 17-18: class/feature organization, happy paths, alternate paths, outgoing calls, maintainable setup.
- [Testability](../../../../Slides/12-Design-for-Testability.pdf), pp. 3-5: observability, controllability, decomposability, constructor injection.

The test-pattern book in `Slides/` is supporting reference material; the course
slide principles above drive this small project's design. Functional, acceptance,
and user-story slides provide context, but this assignment is a unit/component
test exercise; it does not specify a UI, user acceptance session, load target, or
security requirement. No such acceptance claims are inferred from these tests.

## Answers to the two assignment questions

**1. Does the program actually start the supplied engine?** Inject a Mockito-created
engine into a real `Aircraft`, provide successful preflight readings, prepare,
and call `takeOff()`. Then `verify(engine, times(1)).start()` observes the
outgoing start command. `takeoffStartsEngineOnceAndRetractsGear` also checks
gear retraction and the aircraft's `IN_FLIGHT` state. This is **behavior
verification**. It proves delegation to the supplied engine, not that a real
physical engine runs. `completeFlightUsesRealComponents` separately observes
`isRunning()` on the real engine.

**2. If the engine reports failure, does the program stop the flight?** After a
successful takeoff, stub `isOperational()` to return false and call
`cruise(5.0)`. `engineFailureDuringCruiseLandsWithoutConsumingFuel` checks
`LANDED`, one deploy/stop command, and no consumption. Here the false result
is an **indirect input supplied by a stub**. `realEngineFailureDuringFlightCausesEmergencyLanding`
also calls the real `Engine.fail()` and confirms deployed gear, stopped engine,
and unchanged real fuel. Failure before preparation instead rejects preparation;
these are separate lifecycle situations with separate tests.

A **stub** supplies controlled return values or exceptions. A **mock** is used to
verify expected outgoing calls. Mockito calls generated doubles “mocks”, but the
role depends on how a test uses them. The same generated engine can provide
stubbed sensor readings and record start/stop calls. A **dummy** merely fills an
unused parameter; a **fake** is a simplified working implementation. A general
**Test Spy** records calls so tests can inspect indirect outputs. Mockito's
specific `spy()` additionally delegates unstubbed calls to a real object. These tests do not need spies,
handwritten fakes, reflection, static mocking, or a mock of `Aircraft`.

## Risk and systematic case selection

The highest risks are taking off unsafely, continuing flight after failure,
losing/reporting fuel incorrectly, and accepting illegal lifecycle operations.
Those receive decision-table, state-transition, boundary, and failure-injection
tests. Small gear/navigation state tests are cheaper and lower priority. Tests
were designed from README behavior, the assignment's interaction objective, and
the public component contracts. The follow-up assessment authorized explicit
finite-input and recovery policies; those are labeled separately from the
original assignment requirements in the traceability matrix. Preparation-query
propagation still characterizes the supplied behavior.

Preparation uses four representative decisions, sufficient to isolate each
condition's effect. Later queries are deliberately unstubbed when an earlier
check rejects; exact sensor-call order is not generally asserted.

| Operational engine | Enough fuel | Available route | Expected |
| --- | --- | --- | --- |
| true | true | true | `READY` |
| false | irrelevant | irrelevant | `FlightException`, remain grounded |
| true | false | irrelevant | `FlightException`, remain grounded |
| true | true | false | `FlightException`, remain grounded |

The finite lifecycle table has five states and five commands. Its six allowed
state/command pairs are:

| Starting state | Command | Result on successful components |
| --- | --- | --- |
| GROUNDED | prepareForTakeoff | READY |
| LANDED | prepareForTakeoff | READY |
| READY | takeOff | IN_FLIGHT |
| IN_FLIGHT | cruise | IN_FLIGHT |
| IN_FLIGHT | land | LANDED |
| IN_FLIGHT | emergencyLand | LANDED |

All 19 remaining pairs are tested from reachable public states. A failed takeoff
provides `ABORTED` without manipulating private fields. Failure transitions are
additional tests: takeoff -> `ABORTED`; engine/fuel failure during cruise ->
`LANDED`. Each parameterized lifecycle case checks the exception and absence of
changes to status, fuel, engine running state and gear deployment.

Fuel input partitions and boundary representatives include:

- Negative constructor values, including the immediate neighbor below zero in
  each argument; zero initial/minimum fuel; threshold 19.99 / 20 / 20.01.
- Negative, smallest negative magnitude, signed zero, zero, smallest positive
  `double`, ordinary positive and fractional cruise values.
- Consumption just below, exactly at, and one representable value above the
  remaining fuel (`Math.nextUp(20.0)`). Failed consumption must preserve fuel.
- Null, empty and whitespace-only destinations; absent constructor dependencies.
- NaN and both infinities at initial-fuel, minimum-fuel, consumption and cruise
  boundaries; invalid consumption/cruise must preserve real state.

This deliberately samples continuous numeric inputs. Enumerating the tiny finite
state table is practical; enumerating every numeric value or every sequence of
flights is not. No redundancy-heavy suite of all preflight boolean combinations
is needed to isolate the three preconditions.

## Fixture and assertion design

`AircraftTest`, `AircraftRecoveryTest` and `AircraftIntegrationTest` use `@BeforeEach` to construct fresh,
transient objects. Small component tests use local inline setup. Helpers such as
`prepareAircraft`, `startFlight`, and `assertLandedSafely` name reusable setup and
one cohesive landing outcome. There is no mutable static fixture or dependency
between tests. Teardown is garbage collection; no external service, file,
database, clock or network is used by test cases.

Each case follows setup, exercise, verify, and implicit teardown. Strict
`MockitoExtension` stubbing catches unused setup and keeps each scenario minimal.
Parameterized setup sometimes branches to construct only the needed fixture;
assertions themselves do not branch or duplicate the production algorithm.
One behavior can require several related assertions: a safe landing means
landed status, deployed gear and stopped engine together.

State assertions use public accessors; behavior assertions inspect outgoing
calls. A mock's `isRunning()` would just return its default false, so it is never
used as proof of engine shutdown. Success is checked on real state or verified
commands instead. Exceptions use `assertThrows`; wrapping tests compare the
actual cause with `assertSame`. Exact error wording is not frozen, except that
invalid-lifecycle diagnostics must identify the current state. Decimal fuel
subtraction uses a small explicit tolerance, rather than a fragile exact decimal
comparison. The production fixes implement actual validation/recovery behavior;
there are no test-only branches or replacements of the aircraft under test.

`doNothing()` is normally redundant for a void method on a mock. It appears
explicitly because the assignment requires it. `thenThrow()` handles a value-returning
query; `doThrow()` handles void commands. `any(Double.class)` is the safe typed
`any` overload for the primitive `double` parameter. Exact amounts are still
verified in successful-consumption tests; broad matchers do not replace important
oracles.

`reset()` clears both call history and stubbings. It is normally unnecessary
because each test already gets fresh objects. Its single deliberate use divides
two flights within one scenario, after the first flight is complete; dependencies
are restubbed and the second start is verified once. It does not change the real
aircraft's `LANDED` state. Without reset that call count would be two. Reset is
not used to conceal calls or to establish dependencies between tests.

## Review fixes and recovery contract

The 2026-11-10 assessment found three faults that escaped the original 103-case
suite: accepting a tiny negative initial fuel, accepting a tiny negative minimum,
and silently repairing a failed engine during a rejected start. Constructor tests
now include the immediate negative neighbor of zero for each argument. Engine
tests assert nonoperational/stopped state after rejected starts and reject another
retry. The sensitivity script now includes these three formerly surviving faults.

Following the user's request to fix all identified weaknesses, the production
contract was extended explicitly. These policies address real simulator defects;
they are documented additions rather than claims about extra original PDF rules.

**Numeric validation:** both constructor fuel values must be finite and
nonnegative; consumption and cruise amounts must be finite and positive. NaN and
both infinities throw `IllegalArgumentException` before fuel/component mutations.
The independent checker now reports 12 passed / 0 failed; the former-corruption
probe verifies four formerly failing examples. Equivalent cases run in Maven.

**Aborted takeoff:** startup/retraction failures set `ABORTED`, then attempt gear
deployment and engine shutdown. These cleanup commands also handle collaborators
that changed state before throwing. The original failure is the cause of the
`FlightException`; cleanup failures are retained as suppressed exceptions.

**Landing and emergency recovery:** deployment is attempted before shutdown;
shutdown is attempted even when deployment throws. `LANDED` is assigned only when
both commands succeed. Otherwise the existing `IN_FLIGHT` state remains, so the
API does not falsely claim a completed landing and permits another landing
attempt. If both commands fail, deployment is the primary recovery exception and
shutdown is suppressed beneath it. Direct landing calls propagate this recovery
exception; they have no earlier initiating exception to preserve.

**Cruise failures:** a false engine reading still requests emergency landing
without consuming fuel. An engine-query exception now also requests emergency
landing, then throws `FlightException` with the query exception as its cause. A
fuel exception follows the same recovery/preservation pattern. If recovery fails,
the initiating fuel/query exception stays primary and receives the recovery
failure as a suppressed exception. Exception identity checks prevent Java's
self-suppression error when a collaborator reuses an exception instance.

`AircraftRecoveryTest` covers fuel/query failures combined with each recovery
failure, both recovery commands failing, aborted-start cleanup, and reused
exception objects. Real-component integration tests demonstrate restored gear,
stopped engines after partial failures, no fuel corruption, and successful retry
after a transient deployment failure. Older characterization assertions that
required skipping shutdown or masking the original exception were replaced with
these explicit regression requirements. Preparation-query exceptions still
propagate unchanged before readiness changes.

See `DOMAIN-REVIEW.md` for traceability and `VERIFICATION.md` for measured results.
The sensitivity experiments are targeted checks, not an exhaustive mutation
score. The finite model cannot guarantee successful landing if hardware commands
continue throwing; it can attempt both actions and retain all failure information.
Refueling, repair, physical certification, concurrency and additional route/fuel
checks at takeoff are outside the specified model. The two real team members
still need to review the work and complete the oral/external submission steps.

## Oral-assessment practice

1. **What is the SUT?** The real `Aircraft` in aircraft unit tests; a real individual
   component in its component tests. Integration tests include the aircraft and
   its real collaborators.
2. **Why inject dependencies?** The constructor supplies control points for sensor
   responses and observation points for commands without changing flight logic.
3. **What defect would a status-only takeoff test miss?** Setting `IN_FLIGHT` while
   omitting engine startup. Interaction verification catches it; the sensitivity
   experiment also demonstrates this failure.
4. **Why both mocked and real-component tests?** Mocks isolate decisions and inject
   rare failures; real components confirm that commands cause the intended state
   changes and actually deduct fuel.
5. **Does `verify()` execute the method?** No. It checks recorded calls after the
   exercise. `times(1)` requires exactly one call; `never()` requires none.
6. **Why assert the cause?** A wrapper exception alone could lose the original
   failure. Identity checking proves the exact injected cause survived.
7. **Why a separate failure query test?** `thenThrow()` supplies a different kind of
   indirect input from `thenReturn(false)`: an exceptional query response rather
   than an operational reading.
8. **Why not mock the aircraft or force its private status?** That would replace or
   bypass the behavior being tested. Public operations reach each starting state.
9. **What do 100% coverage and green tests not prove?** Requirements completeness,
   all numeric domains, realistic hardware recovery, or production safety. The
   three originally surviving faults demonstrate why assertion quality matters.
10. **What remains for submission?** Team review, private repository, timing the
    visibility change, committing/pushing, and submitting URL/hash as listed in
    `SUBMISSION.md`.
