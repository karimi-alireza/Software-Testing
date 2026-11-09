# Verification record

Executed on 2026-11-09, using OpenJDK 17.0.14 and Maven. Dependencies were
installed and the final build used Maven's normal local repository. Tests do not
require network access once dependencies are cached.

## Final standard build

```sh
mvn -B -o clean verify
```

Result: **BUILD SUCCESS. 103 test cases, 0 failures, 0 errors, 0 skipped.**
There are 57 test methods in seven classes; parameterized methods produce the
additional independently reported cases. The Mockito-focused class alone has
24 methods / 33 cases, exceeding the six-test requirement.

| Test class | Cases | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- |
| `airplane.AircraftIntegrationTest` | 10 | 0 | 0 | 0 |
| `airplane.AircraftLifecycleTest` | 23 | 0 | 0 | 0 |
| `airplane.AircraftTest` | 33 | 0 | 0 | 0 |
| `airplane.components.EngineTest` | 5 | 0 | 0 | 0 |
| `airplane.components.FuelSystemTest` | 21 | 0 | 0 | 0 |
| `airplane.components.LandingGearTest` | 3 | 0 | 0 | 0 |
| `airplane.components.NavigationSystemTest` | 8 | 0 | 0 | 0 |

The second domain review added 19 cases to the initial 84-case suite. Both the
updated normal-order and randomized-order clean builds ran all 103 cases.
All dependency artifacts were already cached, so the latest runs used offline
mode (`-o`) with the normal Maven repository. The final source contains no
`@Disabled`, ignored tests, or expected-failure exclusions. Surefire fails if no tests are discovered.

## Coverage measured by JaCoCo 0.8.12

| Production class | Covered / total lines | Covered / total branches |
| --- | --- | --- |
| `airplane.Aircraft` | 52/52 | 14/14 |
| `airplane.components.NavigationSystem` | 12/12 | 4/4 |
| `airplane.components.FuelSystem` | 14/14 | 10/10 |
| `airplane.components.Engine` | 17/17 | 2/2 |
| `airplane.components.LandingGear` | 7/7 | No branches |
| `airplane.models.FlightStatus` | 6/6 | No branches |
| `airplane.exceptions.FlightException` | 4/4 | No branches |
| **Total** | **112/112 (100%)** | **30/30 (100%)** |

All 401 instructions, 32 methods and seven classes were also covered. These
figures come from `target/site/jacoco/jacoco.xml`, not an estimate. Open
`target/site/jacoco/index.html` for the generated source-level HTML report.
`target/` is intentionally ignored by Git; `mvn clean verify` regenerates it.
Coverage does not include test code and does not establish correctness over
uncovered input domains such as non-finite numbers.

## Independence and repeatability check

A separate clean build randomized both methods and classes:

```sh
mvn -o '-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random' \
    '-Djunit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer$Random' \
    -Djunit.jupiter.execution.order.random.seed=4711 clean verify
```

Result: **103 cases, no failures/errors/skips**, using the ordinary Maven cache.
The single seed and ordinary run
provide evidence of independence, not proof for every possible test schedule.
Fresh in-memory fixtures and absence of shared mutable state support that result.

## Assertion-sensitivity experiment

Command used:

```sh
python3 scripts/check-test-sensitivity.py
```

The normal Maven cache was used. The script first requires a green unmodified
baseline, introduces exactly one source fault at a time in a temporary copy,
requires actual JUnit assertion failures with no test errors, and deletes the
copy afterward. Compiler/build errors do not count as detected faults.

```text
PASS: unmodified baseline
DETECTED: missing engine start (15 assertion failures)
DETECTED: gear deploys during takeoff (24 assertion failures)
DETECTED: cruise fails to deduct fuel (12 assertion failures)
DETECTED: failed engine keeps flying (5 assertion failures)
DETECTED: fuel failure is swallowed (3 assertion failures)
DETECTED: takeoff ignores state guard (4 assertion failures)
DETECTED: exact takeoff fuel is rejected (2 assertion failures)
DETECTED: zero fuel consumption is accepted (2 assertion failures)
PASS: all 8 representative faults detected
```

The original working production sources were never mutated. These eight
representative faults are a targeted quality check, **not a comprehensive
mutation-testing score**.

## Exploratory defect reproduction

Executed against compiled original production classes:

```sh
java --class-path target/classes scripts/NonFiniteFuelProbe.java
```

```text
consumeFuel(NaN): remaining=NaN
infinity - infinity: remaining=NaN
NaN initial fuel accepted: remaining=NaN
cruise(NaN), status=IN_FLIGHT: remaining=NaN
Known validation gap reproduced; these outcomes are defects, not acceptance criteria.
```

The probe reports the known defect; a successful reproduction is not acceptance
of those outcomes. It exits unsuccessfully if the NaN behavior ceases to
reproduce. It is outside `src/test` so this diagnostic cannot convert an invalid
business outcome into a passing regression requirement. See D1/D2 in
`TEST-REPORT.md` for input-validation and partial-recovery limits.


## Proposed finite-only contract diagnostic

```sh
java --class-path target/classes scripts/CheckFiniteFuelContract.java
```

Actual result on the unchanged starter: **4 passed, 8 failed, exit code 1**.
Three non-finite values are checked at four boundaries. Negative infinity is
rejected correctly; NaN and positive infinity do not satisfy the proposed
IllegalArgumentException/no-mutation policy. This policy is not explicit in the
assignment. These diagnostics are **not included** in the 103 passing Maven cases,
and their failures remain unresolved. See `DOMAIN-REVIEW.md` for the per-case
matrix and distinctions between specified behavior and proposed robustness.

## Final requirement review

- Assignment pages and relevant slides reviewed; every local assignment item
  linked to code or explanatory material in `REQUIREMENTS.md`.
- All nine Mockito APIs used in executed tests, with explicit rationale for
  `reset`, `doNothing` and typed `any`.
- Positive/negative cases, boundaries, decisions, public state transitions,
  indirect inputs, outgoing calls and real-component effects checked.
- No production test branches or source modifications; test dependencies stay
  test-scoped; the produced application JAR contains no test classes.
- Documentation links, measured counts, and absence of disabled tests audited.
- Repository creation/privacy, two-person review, publication timing, commit/hash
  and eLearn submission remain external steps, explicitly listed in
  `SUBMISSION.md`. No external completion is claimed.
