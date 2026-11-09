# CA2: test design, answers, and walkthrough

## Scope and sources

This solution completes the testing work for SWT-05-CA2.pdf. It was prepared with
AI assistance, which page 1 explicitly allows. The two students still need to
review the code, understand the tests, perform the external submission, and
answer the oral assessment themselves.

The test strategy applies these local course materials:

- [Assignment](../../SWT-05-CA2.pdf), pp. 1-3: learning objective, two engine questions, nine Mockito concepts, submission rules.
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
unused parameter; a **fake** is a simplified working implementation; a **spy**
records/delegates to a real object unless stubbed. These tests do not need spies,
handwritten fakes, reflection, static mocking, or a mock of `Aircraft`.

## Risk and systematic case selection

The highest risks are taking off unsafely, continuing flight after failure,
losing/reporting fuel incorrectly, and accepting illegal lifecycle operations.
Those receive decision-table, state-transition, boundary, and failure-injection
tests. Small gear/navigation state tests are cheaper and lower priority. Tests
were designed from README behavior, the assignment's interaction objective, and
the public component contracts before the first suite run. Observed unspecified
exception behavior is labeled characterization in the traceability matrix.

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

- Negative constructor values; zero initial/minimum fuel; threshold 19.99 / 20 / 20.01.
- Negative, smallest negative magnitude, signed zero, zero, smallest positive
  `double`, ordinary positive and fractional cruise values.
- Consumption just below, exactly at, and one representable value above the
  remaining fuel (`Math.nextUp(20.0)`). Failed consumption must preserve fuel.
- Null, empty and whitespace-only destinations; absent constructor dependencies.

This deliberately samples continuous numeric inputs. Enumerating the tiny finite
state table is practical; enumerating every numeric value or every sequence of
flights is not. No redundancy-heavy suite of all preflight boolean combinations
is needed to isolate the three preconditions.

## Fixture and assertion design

`AircraftTest` and `AircraftIntegrationTest` use `@BeforeEach` to construct fresh,
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
comparison. No production code or testing branch was introduced to pass tests.

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

## Review, detected gaps, and limits

See `DOMAIN-REVIEW.md` for the second review and added scenarios.
See `VERIFICATION.md` for measured results, randomized ordering, and temporary
fault experiments. Full structural coverage is a useful check against missed
branches; it does not prove all requirements or numeric inputs are correct. The
eight representative faults show assertion sensitivity, not a universal mutation
score. Peer review by the two actual team members and the oral assessment remain
outside this automated check.

**D1: non-finite fuel values are accepted.** IEEE-754 comparisons with `NaN` are
false, so the starter's `< 0` / `<= 0` checks do not reject it. Reproduction:

```sh
mvn test
java --class-path target/classes scripts/NonFiniteFuelProbe.java
```

Observed: `consumeFuel(NaN)` changes remaining fuel to `NaN`; `NaN` constructor
fuel is accepted; infinity minus infinity also produces `NaN`; `cruise(NaN)`
leaves the aircraft `IN_FLIGHT` with unusable fuel state. Expected robustness
policy would reject non-finite values with `IllegalArgumentException` and leave
state/fuel unchanged. The assignment does not spell out this policy. The finite
regression suite does not assert that accepting NaN is correct, and no test is
disabled to hide a failure. The probe intentionally exposes the unresolved gap. A separate checker,
`java --class-path target/classes scripts/CheckFiniteFuelContract.java`, tests the
proposed rejection contract for NaN and both infinities at all four fuel input
boundaries. It reports 4 passes and 8 failures and exits 1. It is not included in
the passing Maven case count; no finite-only validation requirement is invented
on behalf of the assignment.
A future production fix should specify finite values, use `Double.isFinite` on
constructor/consumption/cruise inputs, and add corresponding regression cases.

**D2: landing and takeoff failures have limited recovery.** If gear deployment
throws, shutdown is not attempted; if engine stop throws, the gear is already
deployed but status remains `IN_FLIGHT`. Two characterization tests expose these
partial outcomes and prevent claiming `LANDED` after a failed landing.
Four additional cases inject gear/shutdown failure while already responding to
engine/fuel failure. The recovery exception propagates; on the fuel path it masks
the original fuel exception, so the advertised wrapped-cause behavior is not
maintained when recovery itself fails. A separate cruise-query test records that
an exceptional engine reading, unlike a false reading, does not trigger landing. If gear
retraction fails after engine startup, status becomes `ABORTED` but the engine
is not stopped by the supplied implementation. No rollback or redundant recovery
is specified in the assignment. These are simulator limitations requiring an
explicit recovery policy, not proof of real aviation safety. The production
sources were retained, following the course instruction to test the real SUT.

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
   all numeric domains, realistic hardware recovery, or production safety. D1/D2
   are concrete counterexamples.
10. **What remains for submission?** Team review, private repository, timing the
    visibility change, committing/pushing, and submitting URL/hash as listed in
    `SUBMISSION.md`.
