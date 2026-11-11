# Verification record

Executed on 2026-11-10 against the fixed `HW/2/CA2`, using OpenJDK 17.0.14,
Maven and Python 3. This record supersedes the earlier 103-case assessment.
Production changes are finite-input validation and failure recovery, explicitly
authorized by the follow-up request. Fault injection only mutates temporary
copies; it never modifies the working project.

## Clean build

```sh
mvn clean verify
```

The actual sandbox command used an offline copy of the existing dependency cache:

```sh
mvn -B -o -Dmaven.repo.local=/tmp/ca2-assessment/m2 clean verify
```

Result: **BUILD SUCCESS: 134 cases, 0 failures, 0 errors, 0 skipped.** There are
71 test methods across eight classes. `AircraftTest` has 24 methods / 34 cases;
`AircraftRecoveryTest` adds seven methods / ten cases. All nine assignment-required
Mockito APIs remain exercised. No test is disabled, ignored or excluded.

| Test class | Cases | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- |
| `airplane.AircraftIntegrationTest` | 17 | 0 | 0 | 0 |
| `airplane.AircraftLifecycleTest` | 23 | 0 | 0 | 0 |
| `airplane.AircraftRecoveryTest` | 10 | 0 | 0 | 0 |
| `airplane.AircraftTest` | 34 | 0 | 0 | 0 |
| `airplane.components.EngineTest` | 5 | 0 | 0 | 0 |
| `airplane.components.FuelSystemTest` | 34 | 0 | 0 | 0 |
| `airplane.components.LandingGearTest` | 3 | 0 | 0 | 0 |
| `airplane.components.NavigationSystemTest` | 8 | 0 | 0 | 0 |

## Measured coverage

JaCoCo 0.8.12 generated `target/site/jacoco/index.html` and `jacoco.xml` from the
clean build. Values below are parsed from that XML, not inferred from test counts.

| Production class | Lines | Branches |
| --- | --- | --- |
| `airplane.Aircraft` | 79/79 | 22/22 |
| `airplane.components.NavigationSystem` | 12/12 | 4/4 |
| `airplane.components.FuelSystem` | 14/14 | 16/16 |
| `airplane.components.Engine` | 17/17 | 2/2 |
| `airplane.components.LandingGear` | 7/7 | No branches |
| `airplane.models.FlightStatus` | 6/6 | No branches |
| `airplane.exceptions.FlightException` | 4/4 | No branches |
| **Total** | **139/139 (100%)** | **44/44 (100%)** |

All 476 instructions, 35 methods and seven production classes are covered.
Coverage does not establish correctness for every input or execution sequence;
that is why the formerly surviving faults were added to sensitivity checks.

## Randomized-order check

After the final test edit, methods and classes were randomized using seed 4711:

```sh
mvn -o '-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random' \
    '-Djunit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer$Random' \
    -Djunit.jupiter.execution.order.random.seed=4711 test
```

The sandbox run also supplied the same `-Dmaven.repo.local` option. Result:
**134 cases, zero failures/errors/skips, BUILD SUCCESS**. One seed provides
supporting evidence of independence; fresh transient fixtures and lack of shared
mutable state support the design further.

## Assertion sensitivity

```sh
python3 scripts/check-test-sensitivity.py
```

The executed sandbox command supplied
`--maven-repo /tmp/ca2-assessment/m2`. The script requires a green baseline,
changes exactly one production fault per run in a temporary copy, and requires
actual JUnit assertion failures, no test errors/skips, and all 134 discovered
cases. Compiler/build errors cannot count as detection.

```text
PASS: unmodified baseline
DETECTED: missing engine start (21 assertion failures)
DETECTED: gear deploys during takeoff (34 assertion failures)
DETECTED: cruise fails to deduct fuel (14 assertion failures)
DETECTED: failed engine keeps flying (5 assertion failures)
DETECTED: fuel failure is swallowed (7 assertion failures)
DETECTED: takeoff ignores state guard (4 assertion failures)
DETECTED: exact takeoff fuel is rejected (2 assertion failures)
DETECTED: zero fuel consumption is accepted (2 assertion failures)
DETECTED: tiny negative initial fuel is accepted (2 assertion failures)
DETECTED: tiny negative minimum fuel is accepted (2 assertion failures)
DETECTED: failed start silently repairs engine (2 assertion failures)
DETECTED: non-finite initial fuel is accepted (2 assertion failures)
DETECTED: non-finite minimum fuel is accepted (2 assertion failures)
DETECTED: non-finite consumption is accepted (2 assertion failures)
DETECTED: non-finite cruise is accepted (4 assertion failures)
DETECTED: aborted takeoff skips cleanup (5 assertion failures)
DETECTED: engine query failure skips emergency landing (5 assertion failures)
DETECTED: gear failure skips engine shutdown (11 assertion failures)
DETECTED: recovery masks original failure (8 assertion failures)
PASS: all 19 representative faults detected
```

The three previously surviving faults now cause two assertion failures each.
All 19 selected faults are detected. This is a targeted experiment, not an
exhaustive mutation-testing score.

## Numeric regression checks

```sh
java --class-path target/classes scripts/CheckFiniteFuelContract.java
java --class-path target/classes scripts/NonFiniteFuelProbe.java
```

- Finite-input checker: **12 passed / 0 failed, exit 0**. NaN and both infinities
  are rejected with `IllegalArgumentException` at constructor initial fuel,
  constructor minimum fuel, consumption and cruise. Mutable fuel/flight state is
  preserved on rejection.
- Former-corruption probe: **all four examples rejected, exit 0**, with fuel and
  status unchanged. This script now verifies the fix instead of requiring the old
  defect to remain reproducible.
- Equivalent conditions execute in the normal Maven regression suite. The
  diagnostic executions are not added to the 134-case Maven count.

## Recovery and documentation audit

Real-component integration cases verify engine shutdown and gear restoration
after partial startup/retraction failures, shutdown despite failed gear deployment,
retry after transient landing failure, and emergency recovery after an exceptional
engine reading. Unit cases cover fuel/query failures combined with deployment,
shutdown, both commands failing, and reused exception instances. Original causes
and suppressed cleanup failures are asserted by identity.

All local Markdown links resolve. The stale assignment link and CA5 text were
corrected, spy terminology clarified, and traceability updated to actual method
names. The packaged application JAR contains no test classes or Mockito code;
JUnit/Mockito dependencies remain test-scoped.

## Remaining handoff

The implementation fixes are in `HW/2/CA2`. The sibling `Software-Testing/CA2`
repository still has the earlier version; update the submission repository before
creating/pushing the new final commit. Team review, oral understanding, remote
visibility timing and eLearn URL/hash delivery remain external steps described in
`SUBMISSION.md`. No publication or delivery is claimed by these local checks.
