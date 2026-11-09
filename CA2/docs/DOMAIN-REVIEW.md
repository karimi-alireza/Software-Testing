# Second domain review

The initial 84-case suite met the explicit CA2 Mockito requirements and covered
every production branch. A second review on 2026-11-09 found meaningful sequence
and combined-failure cases that structural coverage alone did not exercise.

## Added regression and characterization cases

| Domain concern | Added cases | Expected result / classification |
| --- | --- | --- |
| Engine/route changes after landing | 2 | Preparation rechecks the readings and rejects without another takeoff |
| Fuel and route queries throw during preparation | 2 | Original query exception propagates; remains grounded; characterization |
| Engine query throws during cruise | 1 | No fuel use or landing; remains in flight; characterization of missing recovery policy |
| Emergency recovery also fails | 4 | Each engine/fuel emergency combined with gear/shutdown failure; recovery exception propagates, no false LANDED status; characterization |
| Multiple cruise calls | 1 | Remaining fuel reflects cumulative consumption |
| Exact exhaustion followed by another cruise | 1 | Exact consumption reaches zero; next positive consumption causes emergency landing and FlightException |
| Insufficient fuel on the next flight | 1 | Real fuel below minimum prevents preparation after landing |
| Two real flights | 1 | Fuel state persists across preparation/takeoff/landing cycles |
| Engine fails after preflight | 1 | Takeoff aborts, engine stays stopped and gear stays deployed |
| Engine fails in flight, then another preparation attempted | 1 | Landing does not repair the engine; preparation rejects |
| Smallest/largest positive finite fuel | 2 | Exact consumption succeeds and leaves zero |
| Immediate floating-point neighbors around takeoff threshold | 2 | `Math.nextDown(20)` rejects; `Math.nextUp(20)` accepts |
| **Total additional Maven cases** | **19** | **103 cases overall, 57 test methods** |

Existing takeoff and normal-landing tests also now check command order using
Mockito `InOrder`: start before retract; deploy before stop. These are the
sequence described by the starter README. Setup queries are not ordered merely
to freeze incidental internal implementation.

No production source was changed, no Maven test was excluded/disabled, and no
assertion was weakened to obtain green results. Characterization cases explicitly
record the starter's current failure behavior; they do not certify safe recovery.

## Separate finite-input policy checks

Physical fuel quantities should be finite, but the assignment does not explicitly
state a non-finite policy. Rather than silently assuming one or treating accepted
NaN as correct, the diagnostic below checks the proposed contract: NaN and either
infinity must be rejected with `IllegalArgumentException`, without fuel or flight
state changes.

```sh
mvn clean verify
java --class-path target/classes scripts/CheckFiniteFuelContract.java
```

The checker creates fresh fixtures for each scenario. It covers three values at
four boundaries: constructor initial fuel, constructor takeoff minimum,
`consumeFuel`, and `cruise`.

| Input | Initial fuel | Minimum fuel | Consumption | Cruise |
| --- | --- | --- | --- | --- |
| NaN | Accepted: FAIL | Accepted: FAIL | Accepted/corrupts fuel: FAIL | Accepted/corrupts fuel: FAIL |
| Positive infinity | Accepted: FAIL | Accepted: FAIL | Wrong exception type: FAIL | Lands with FlightException: FAIL |
| Negative infinity | Rejected: PASS | Rejected: PASS | Rejected without mutation: PASS | Rejected without mutation: PASS |

Observed result: **4 passed, 8 failed; process exits 1**. These 12 diagnostics are
separate from the 103 passing Maven cases. A green Maven run does not mean the
finite-only contract passes. Correcting these issues would require specifying
the policy, changing production validation, and converting the checks into normal
regression requirements. That production behavior change is outside this review.

## Remaining policy questions and limits

- If emergency landing fails during fuel-error handling, the original fuel
  exception is masked by the landing exception. The new cases reproduce this;
  the assignment defines no fallback/exception-aggregation policy.
- If an engine-status query throws during cruise, no emergency landing is
  attempted. An exception is different from the documented false reading.
- Preflight can become stale: `takeOff()` starts the engine but does not reread
  route/fuel availability. Engine-start failure is tested. Requiring fresh route
  or fuel readings at takeoff would add a requirement absent from the README.
- Exact fuel exhaustion is permitted by `consumeFuel`; automatic landing happens
  on a subsequent failed cruise, not merely on reaching zero. The added sequence
  test follows that contract rather than assuming extra simulator behavior.
- Concurrency, physical aviation certification, repairs, refueling, multiple
  routes, capacity limits and real hardware timing are not defined by this small
  model and are not claimed as covered requirements.

The suite now covers the identified finite-input requirements and high-value
sequences more thoroughly. No finite suite can establish that every possible
input, sequence, or unspecified recovery policy has been tested. Review should
continue whenever the contract or implementation changes.
