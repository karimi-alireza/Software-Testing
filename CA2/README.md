# Airplane Simulator

1. A new aircraft starts in `GROUNDED` state.
2. `prepareForTakeoff()` checks that the engine is operational, enough fuel is
   available, and a route is available. On success, the state becomes `READY`.
3. `takeOff()` starts the engine, retracts the landing gear, and changes the
   state to `IN_FLIGHT`.
4. `cruise(amount)` consumes fuel. If the engine is no longer operational, the
   aircraft makes an emergency landing. A fuel-consumption failure also causes
   an emergency landing and is reported as a `FlightException`.
5. `land()` and `emergencyLand()` deploy the landing gear, stop the engine,
   and change the state to `LANDED`.

An invalid lifecycle operation (for example, calling `takeOff()` before
preparation) throws `FlightException`.

- `FlightException` is an unchecked exception.
- `IllegalArgumentException` is used for invalid numeric input.
- `Engine.fail()` can simulate an engine failure during a flight.
- `NavigationSystem.setRouteAvailable(...)` can change route availability.
- Mockito and JUnit 5 dependencies are already configured with test scope in `pom.xml`.

## Completed CA2 test suite

Requires Java 17+ and Maven. From this folder:

```sh
mvn clean verify
```

Maven downloads the pinned test/build dependencies on the first run. `verify`
runs all **103 cases** (57 test methods, including parameterized cases), packages
the application, and generates coverage. There are no disabled tests.

- JUnit results: `target/surefire-reports/`
- Coverage: `target/site/jacoco/index.html` (HTML) and `jacoco.xml` (machine-readable)
- Mockito-focused tests only: `mvn -Dtest=AircraftTest test`
- Optional sensitivity check after a successful build: `python3 scripts/check-test-sensitivity.py`

The sensitivity script uses eight faults in temporary project copies, runs Maven
offline, and fails if a fault escapes assertion detection. It requires Python 3
and an already populated Maven cache. For a custom cache, pass
`--maven-repo /path/to/cache` to the script, and use the same
`-Dmaven.repo.local=/path/to/cache` in Maven commands.

Tests use Mockito's subclass mock maker: these non-final collaborators do not
need dynamic JVM agent attachment. Mockito and JUnit remain test-scoped;
production source code has not been changed.

Read the submission materials:

- [Requirements and traceability](docs/REQUIREMENTS.md): every assignment requirement, required Mockito API, and behavioral test mapping.
- [Test design and explanations](docs/TEST-REPORT.md): mock/stub answers, design decisions, oral-assessment preparation, and known gaps.
- [Verification results](docs/VERIFICATION.md): executed checks and coverage/sensitivity evidence.
- [Submission checklist](docs/SUBMISSION.md): team, GitHub visibility, deadline, commit hash, and eLearn handoff.

The regression suite covers the finite-input contract. The supplied simulator
still accepts non-finite fuel values such as `NaN`; this defect and landing-failure
limitations are documented in the report. Reproduce the numeric defect after
building with:

```sh
java --class-path target/classes scripts/NonFiniteFuelProbe.java
```

The [second domain review](docs/DOMAIN-REVIEW.md) adds sequence, recovery, and
numeric-extreme cases beyond the initial suite. A separate diagnostic checks a
proposed finite-only input policy:

```sh
java --class-path target/classes scripts/CheckFiniteFuelContract.java
```

Against the unchanged starter this diagnostic reports **4 passed / 8 failed**
and exits with code 1. These unresolved policy violations are separate from the
103 passing Maven cases. No Maven test is disabled or excluded.
