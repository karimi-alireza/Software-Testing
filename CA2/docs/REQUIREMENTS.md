# Assignment requirements and traceability

Source of truth: `../SWT-05-CA2.pdf`, all three pages. This is
assignment 2, “Mocks aren't Stubs”, with upstream repository
<https://github.com/UT-SWT/F05-CA2> (link embedded in the assignment).

## Assignment checklist

| ID | Requirement | Implementation / evidence | Status |
| --- | --- | --- | --- |
| A1 | Test the airplane simulator | Real `Aircraft` in every aircraft test; production validation/recovery fixes explicitly documented after the user's review request | Complete locally |
| A2 | At least six tests using Mockito | `AircraftTest`: 24 test methods / 34 cases; `AircraftRecoveryTest`: seven methods / ten cases | Complete locally |
| A3 | Use every listed Mockito concept at least once across the suite | API matrix below; strict Mockito extension validates used stubbings | Complete locally |
| A4 | Understand mocks, stubs, dependency replacement, and interactions | `TEST-REPORT.md`, explanations and oral-assessment questions | Written preparation complete; each student must learn and explain it |
| A5 | Work in a two-person group | Team membership is outside the source code | Student action required |
| A6 | Fork/upload project and add tests to a private GitHub repository | `SUBMISSION.md` gives the exact handoff process | External submission required |
| A7 | Keep repository private through ۱۴۰۵/۰۸/۲۳, then make public | Privacy timing preserved verbatim in `SUBMISSION.md` | Student action required |
| A8 | Submit repository URL plus final commit hash in eLearn; commit must predate deadline; one member may submit | `SUBMISSION.md` includes checks and commands | External submission required |
| A9 | Be ready for virtual oral assessment; understand AI-generated code | Walkthrough and practice answers in `TEST-REPORT.md` | Student preparation required |
| A10 | Follow course rules on originality and plagiarism | AI assistance disclosed in report; no authorship or team identity fabricated | Students must follow course rules |

The PDF requires code and repository submission; it does not mandate a separate
PDF report. The Markdown report supplies explanations and review evidence without
adding another PDF dependency to the project.

## Required Mockito API matrix

All examples are executed in `src/test/java/airplane/AircraftTest.java`.

| Required concept | Executed example | Test / purpose |
| --- | --- | --- |
| `mock()` | `engine = mock(Engine.class)` | `createFreshAircraft`: replace dependencies, never the SUT |
| `reset()` | `reset(engine, fuel, gear, navigation)` | `landedAircraftCanBePreparedForAnotherFlightUsingFreshComponentReadings`: clear first flight's history/stubs, restub before second flight |
| `when()` | `when(engine.isOperational())` | `allowPreparation`: control indirect inputs |
| `thenReturn()` | `.thenReturn(true)` / `.thenReturn(false)` | Happy preparation and failed engine/fuel/route decisions |
| `verify()` | `verify(engine).start()` | Observe actual outgoing call on the injected dependency |
| `times()` | `verify(engine, times(1)).start()` | Engine starts exactly once per takeoff |
| `any()` | `consumeFuel(any(Double.class))` | Fuel failure injection and no-consumption assertions; typed overload safely handles primitive `double` |
| `thenThrow()` | `when(engine.isOperational()).thenThrow(failure)` | `failedEngineQueryPropagatesBeforeReadinessChanges`: inject query failure |
| `doNothing()` | `doNothing().when(engine).start()` | `takeoffStartsEngineOnceAndRetractsGear`: explicit void-method stubbing |

`any(Double.class)` is the `any` API's typed overload. Bare `any()` returns null,
which would throw during unboxing into `consumeFuel(double)` before the SUT runs.
The tests also use `doThrow()` for void methods, where `when(voidMethod())` cannot
compile. No new production API was added merely to demonstrate a matcher.

## Behavioral requirements traceability matrix

Names below are executable method names; parameterized rows have individual
JUnit results. `AircraftTest` and `AircraftRecoveryTest` use isolated dependencies;
integration and component tests observe real state. The domain review and numeric checks are
recorded in `DOMAIN-REVIEW.md`.

| ID / priority | Requirement or public contract | Test class / methods | Observable expected result |
| --- | --- | --- | --- |
| B1 / medium | A new aircraft starts grounded | `AircraftTest.newAircraftIsGroundedWithoutUsingComponents` | `GROUNDED`; no collaborator calls |
| B2 / high | Preparation requires operational engine, enough fuel, available route | `AircraftTest.preparationWithAllPreconditionsSatisfiedMakesAircraftReady`, `failedPreconditionKeepsAircraftGrounded` | All true gives `READY`; each independently false gives `FlightException`, stays `GROUNDED`, no start/retract |
| B3 / high | Takeoff starts engine and retracts gear | `AircraftTest.takeoffStartsEngineOnceAndRetractsGear` | One start and one retract; `IN_FLIGHT` |
| B4 / high | Takeoff failure aborts and retains cause | `AircraftTest.engineStartFailureAbortsTakeoffAndPreservesCause`, `gearRetractionFailureAbortsTakeoffAndPreservesCause` | `ABORTED`; `FlightException` wraps the identical failure |
| B5 / high | Cruise consumes requested positive fuel | `AircraftTest.cruiseConsumesExactlyRequestedPositiveAmount`, `AircraftIntegrationTest.completeFlightUsesRealComponents` | Exact argument delegated once; real fuel 100 -> 75; still in flight |
| B6 / high | Nonpositive or non-finite cruise amount is invalid | `AircraftTest.invalidCruiseAmountDoesNotConsumeFuelOrLand`, `AircraftIntegrationTest.nonFiniteCruisePreservesRealFlightState` | `IllegalArgumentException`; no consumption, landing or component state changes |
| B7 / high | Failed engine during cruise causes emergency landing | `AircraftTest.engineFailureDuringCruiseLandsWithoutConsumingFuel`, `AircraftIntegrationTest.realEngineFailureDuringFlightCausesEmergencyLanding` | `LANDED`, gear deployed, engine stopped, fuel unchanged; no exception |
| B8 / high | Consumption failure causes emergency landing and is reported | `AircraftTest.fuelConsumptionFailureLandsAndPreservesCause`, `AircraftIntegrationTest.realFuelExhaustionCausesEmergencyLandingAndReportsFailure` | `LANDED`, gear deployed, engine stopped; `FlightException` preserves cause |
| B9 / high | Both landing operations deploy gear and stop engine | `AircraftTest.normalLandingDeploysGearAndStopsEngine`, `emergencyLandingDeploysGearAndStopsEngine` | Each command once; `LANDED` |
| B10 / high | Invalid lifecycle operations throw | `AircraftLifecycleTest.invalidOperationRejectsWithoutChangingAircraftOrComponents` | 19 forbidden state/operation pairs reject; status, fuel, running and gear state unchanged |
| B11 / high | A landed aircraft can prepare again and must recheck conditions | `AircraftTest.landedAircraftCanBePreparedForAnotherFlightUsingFreshComponentReadings`, `landedAircraftRechecksFuelBeforeAnotherFlight`, `landedAircraftRechecksEngineBeforeAnotherFlight`, `landedAircraftRechecksRouteBeforeAnotherFlight` | Fresh good readings allow second takeoff; failed engine/fuel/route check leaves `LANDED` |
| B12 / medium | Constructor dependencies must be non-null | `AircraftLifecycleTest.constructorRejectsEachMissingDependency` | Each of four missing dependencies gives `NullPointerException` |
| B13 / high | Fuel levels nonnegative; takeoff threshold inclusive | `FuelSystemTest.constructorRejectsNegativeFuelValues`, `takeoffThresholdIncludesEquality` | Negative values reject; 19.99 / 20 / 20.01 partition the threshold; zero minimum supported |
| B14 / high | Consumption finite, positive and no greater than remaining fuel | `FuelSystemTest.validConsumptionIncludesExactRemainingFuel`, `invalidConsumptionPreservesFuel`, `consumingMoreThanRemainingFuelFailsWithoutDeductingFuel`, `consumptionCanMoveFuelBelowTakeoffThreshold` | Exact exhaustion allowed; invalid amounts/overdraw fail without mutation; readiness reflects remaining fuel |
| B15 / medium | Engine lifecycle and failure simulation | All five methods in `EngineTest` | Default operational/stopped; start, stop, failure, failed restart checked |
| B16 / medium | Landing gear defaults deployed and can retract/deploy | All three methods in `LandingGearTest` | Real deployed flag matches each operation |
| B17 / medium | Destination nonblank; route availability configurable | All four methods in `NavigationSystemTest`; `AircraftIntegrationTest.unavailableRouteCanBeRestoredBeforeRetryingPreparation` | Null/empty/whitespace reject; destination retained; default, explicit and changed availability work |
| B18 / high | Fuel accumulates across cruise calls and flights; exhausted fuel prevents further cruise/flight | `AircraftIntegrationTest.consecutiveCruiseCallsDeductCumulativeFuel`, `exactFuelExhaustionAllowsConsumptionButNextCruiseTriggersEmergencyLanding`, `consumingBelowTakeoffMinimumPreventsNextFlightAfterLanding`, `fuelStatePersistsAcrossTwoRealFlights` | Exact remaining fuel persists; next invalid operation rejects or lands as specified |
| B19 / high | Real engine failure is honored at subsequent lifecycle steps | `AircraftIntegrationTest.engineFailureBetweenPreparationAndTakeoffAbortsBeforeGearRetraction`, `emergencyLandingAfterRealEngineFailureDoesNotMakeEngineReusable` | Failed start aborts with gear deployed; emergency landing does not repair engine |
| B20 / medium | Representable neighbors and extreme finite fuel values | `FuelSystemTest.finitePositiveExtremesCanBeConsumedExactly`, `smallestRepresentableShortfallFailsTakeoffThreshold`, `smallestRepresentableSurplusPassesTakeoffThreshold` | Smallest/largest finite positive fuel exhaust exactly; immediate threshold neighbors classify correctly |
| C1 / characterization | Query exceptions are not wrapped by preparation | `AircraftTest.failedEngineQueryPropagatesBeforeReadinessChanges`, `failedFuelQueryPropagatesBeforeReadinessChanges`, `failedRouteQueryPropagatesBeforeReadinessChanges` | Same exception; no readiness or later component action |
| R1 / adopted robustness policy | Failed deployment must still attempt shutdown; partial landing is not successful | `AircraftTest.gearDeploymentFailureIsNotReportedAsSuccessfulLanding`, `engineStopFailureIsNotReportedAsSuccessfulLanding`, `emergencyGearFailureStillStopsEngineWithoutReportingSuccessfulLanding`; `AircraftIntegrationTest.failedGearDeploymentStillStopsRealEngineAndCanBeRetried` | Both commands attempted; failure retained; no false `LANDED`; retry can finish landing |
| R2 / adopted robustness policy | Engine-query exception triggers emergency landing without fuel consumption | `AircraftTest.failedEngineQueryDuringCruiseLandsAndPreservesCauseWithoutConsumingFuel`; `AircraftIntegrationTest.engineQueryFailureStopsRealEngineAndPreservesFuel` | Successful recovery gives `LANDED`; `FlightException` retains query cause |
| R3 / adopted robustness policy | Original cause and compound recovery failures must survive | All seven methods in `AircraftRecoveryTest` | Original cause identity retained; deployment/shutdown failures available through suppression; no self-suppression |
| R4 / adopted robustness policy | Aborted takeoff attempts gear deployment and shutdown, including partial effects | `AircraftTest.engineStartFailureAbortsTakeoffAndPreservesCause`, `gearRetractionFailureAbortsTakeoffAndPreservesCause`; integration methods `partialGearRetractionFailureRestoresGearAndStopsRealEngine`, `partialEngineStartFailureStillStopsRealEngine`; recovery method `abortedTakeoffRetainsStartupAndBothCleanupFailures` | `ABORTED`; both cleanup commands attempted; real gear restored/engine stopped when cleanup succeeds; original failure retained |
| R5 / adopted robustness policy | All four numeric boundaries reject NaN and infinities | `FuelSystemTest.constructorRejectsNonFiniteInitialFuel`, `constructorRejectsNonFiniteMinimumFuel`, `invalidConsumptionPreservesFuel`; `AircraftTest.invalidCruiseAmountDoesNotConsumeFuelOrLand`; real-component numeric integration test | `IllegalArgumentException` before mutation; independent checker 12/12 passing |
| R6 / assessment regression | Immediate negative constructor boundaries reject; rejected engine starts preserve failure | `FuelSystemTest.constructorRejectsNegativeFuelValues`; `EngineTest.nonoperationalEngineCannotStart`, `failureStopsRunningEngineAndPreventsRestart` | Formerly surviving tiny-negative and silent-repair faults now detected |

C1 retains the supplied preparation-query behavior. R1-R6 describe the follow-up
fixes authorized after the assessment; finite-input validation and full recovery
policies are not presented as verbatim original PDF requirements. Their regression
tests are included in the 134 Maven cases. The independent twelve-condition
numeric checker also passes; its runs are not added to the Maven count.

## Verification gates used during the work

1. **Read and inspect:** read all assignment pages, review slide principles,
   inspect all seven production classes; inventory external handoff requirements.
2. **Design:** define the decision table, state table, input partitions and
   expected results before implementing tests; map tests above.
3. **Implement and run:** fresh fixtures, public interfaces, strict Mockito,
   all nine APIs, both state and interaction checks; run the full suite.
4. **Challenge:** clean build, randomized ordering, branch/line coverage and
   19 deliberate temporary faults; record evidence in `VERIFICATION.md`.
5. **Audit:** reconcile actual test counts, requirements, API use, limitations
   and handoff checklist. No external submission marked complete by local tests.
