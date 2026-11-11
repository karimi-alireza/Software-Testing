# Domain review and assessment fixes

Updated 2026-11-10 after the user requested fixes for all weaknesses in the
assessment. The original suite had 103 cases and full structural coverage, yet
three independent faults survived. The current suite has 134 cases across
71 methods in eight test classes.

## Closure of assessment findings

| Finding | Implementation / regression evidence |
| --- | --- |
| Tiny negative constructor inputs escaped detection | `FuelSystemTest.constructorRejectsNegativeFuelValues` now includes `-Double.MIN_VALUE` (CSV literal `-4.9E-324`) and `-0.0001` in each argument position |
| Rejected start could silently repair an engine | Both failed-start tests in `EngineTest` check nonoperational/stopped state after rejection and attempt another rejected start |
| Non-finite fuel accepted or corrupted state | `Double.isFinite` guards at all four input boundaries; ordinary Maven tests plus the independent twelve-case checker and four-example probe |
| Aborted takeoff left engine running | Cleanup attempts gear deployment and engine shutdown; real-component tests exercise partial startup and partial retraction effects |
| Gear deployment failure skipped shutdown | Both commands attempted independently; normal/emergency unit checks and real stopped-engine assertions |
| Recovery masked original fuel failure | Fuel/query error remains the wrapper cause; recovery failure is suppressed; shutdown failure is retained beneath deployment if both fail |
| Engine-query exception in cruise had no recovery | Emergency landing before throwing a wrapper with the identical query cause; real fuel unchanged |
| Documentation defects | Correct assignment link; remove stale CA5 reference; distinguish general Test Spy from Mockito `spy()`; update counts, policies and verification claims |

These numeric and recovery policies were adopted as production improvements in
the follow-up. They are distinguished from the original assignment's Mockito
requirements in `REQUIREMENTS.md`. Production contains no test-only branching.

## Coverage of failure combinations

| Initiating condition | Recovery succeeds | Deployment fails | Shutdown fails | Both fail |
| --- | --- | --- | --- | --- |
| Engine reading is false | Lands without fuel consumption | Shutdown still attempted; deployment exception propagated | Deployment attempted; shutdown exception propagated | Common helper aggregation exercised by direct-landing tests |
| Engine query throws | Lands, then reports original cause | Original cause plus suppressed deployment failure | Original cause plus suppressed shutdown failure | Both recovery errors retained beneath original cause |
| Fuel consumption throws | Lands, then reports original cause | Original cause plus suppressed deployment failure | Original cause plus suppressed shutdown failure | Both recovery errors retained beneath original cause |
| Takeoff start/retraction throws | Remains `ABORTED`; gear restored and engine stopped when commands succeed | Same shared cleanup helper | Same shared cleanup helper | Startup cause and both cleanup failures retained |

An incomplete landing remains `IN_FLIGHT`; it never claims `LANDED` simply because
cleanup was attempted. A transient gear deployment failure can be retried through
the public landing API. Self-suppression tests cover reused exception instances.

## Numeric policy verification

NaN and either infinity are rejected with `IllegalArgumentException` at constructor
initial fuel, constructor minimum fuel, `consumeFuel`, and `cruise`. Consumption
and cruise reject before changing real fuel, engine, gear or flight state.

```sh
mvn clean verify
java --class-path target/classes scripts/CheckFiniteFuelContract.java
java --class-path target/classes scripts/NonFiniteFuelProbe.java
```

The checker reports **12 passed / 0 failed**, and the probe passes all four former
corruption examples. Both exit 0. Equivalent numeric conditions are included in
the 134 normal Maven cases; diagnostic executions are not added to that count.
Finite positive extremes, threshold neighbors, exact exhaustion, cumulative fuel,
repeated flights, and all 19 invalid state/operation pairs remain covered.

## Sensitivity and remaining scope

The sensitivity script now contains 19 independent source faults, including the
three previously missed faults, non-finite guards and recovery regressions. It
runs a green baseline in a temporary copy and requires actual assertion failures
for each fault, zero test errors/skips, and the same number of discovered cases.
A compiler failure cannot count as detection. See `VERIFICATION.md` for the
executed result.

The simulator does not model repairs, refueling, physical aviation certification,
concurrency, multiple routes, capacity limits, or hardware timing. It checks
route/fuel availability at preparation; adding new checks at takeoff would change
that lifecycle policy. Exact exhaustion is allowed; a subsequent failed cruise
triggers emergency landing. Recovery commands can fail persistently: no software
assertion can guarantee that physical gear deploys or an engine stops after such
a failure. The implemented contract guarantees attempts, honest state reporting,
and retained error information.
