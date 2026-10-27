package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("AdaptiveCruise Control Decisions and Hysteresis Tests")
class AdaptiveCruiseTest {

    private static final double TARGET_SPEED_KMH = 100.0;
    private static final double LOWER_SPEED_KMH = 80.0;
    private static final double ZERO_SPEED_KMH = 0.0;

    private static final double HYSTERESIS_BRAKE_PRESSURE = 10.0;
    private static final double EMERGENCY_BRAKE_PRESSURE = 100.0;
    private static final double ZERO_AMOUNT = 0.0;
    private static final double DOUBLE_EPSILON = 0.001;

    private AdaptiveCruise cruise;

    @BeforeEach
    void setUp() {
        cruise = new AdaptiveCruise(TARGET_SPEED_KMH);
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @ParameterizedTest(name = "Target speed {0} is invalid and should throw IllegalArgumentException")
        @ValueSource(doubles = {-0.01, -50.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
        void shouldThrowIllegalArgumentException_whenTargetSpeedIsNegativeOrNonFinite(double invalidTargetSpeed) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AdaptiveCruise(invalidTargetSpeed),
                    "Target speed must be a finite non-negative value");
        }

        @ParameterizedTest(name = "Speed {0} in safeDistanceFor is invalid and should throw IllegalArgumentException")
        @ValueSource(doubles = {-0.01, -100.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
        void shouldThrowIllegalArgumentException_whenSpeedInSafeDistanceForIsInvalid(double invalidSpeed) {
            assertThrows(IllegalArgumentException.class,
                    () -> cruise.safeDistanceFor(invalidSpeed),
                    "Speed must be a finite non-negative value");
        }

        @ParameterizedTest(name = "Speed {0} in update is invalid and should throw IllegalArgumentException")
        @ValueSource(doubles = {-0.01, -100.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
        void shouldThrowIllegalArgumentException_whenSpeedInUpdateIsInvalid(double invalidSpeed) {
            assertThrows(IllegalArgumentException.class,
                    () -> cruise.update(invalidSpeed, 50.0),
                    "Speed must be a finite non-negative value");
        }

        @ParameterizedTest(name = "Distance {0} in update is invalid and should throw IllegalArgumentException")
        @ValueSource(doubles = {-0.01, -10.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
        void shouldThrowIllegalArgumentException_whenDistanceInUpdateIsInvalid(double invalidDistance) {
            assertThrows(IllegalArgumentException.class,
                    () -> cruise.update(TARGET_SPEED_KMH, invalidDistance),
                    "Distance must be a finite non-negative value");
        }
    }

    @Nested
    @DisplayName("Safe Distance Calculation Tests")
    class SafeDistanceCalculationTests {

        @ParameterizedTest(name = "Speed {0} km/h should yield safe distance of {1} meters")
        @CsvSource({
                "0.0, 0.0",
                "40.0, 20.0",
                "80.0, 40.0",
                "100.0, 50.0",
                "120.0, 60.0"
        })
        void shouldCalculateSafeDistanceAccurately(double speedKmh, double expectedSafeDistance) {
            assertEquals(expectedSafeDistance, cruise.safeDistanceFor(speedKmh), DOUBLE_EPSILON,
                    "Safe distance must be exactly half the speed in meters");
        }

        @ParameterizedTest(name = "Representative speed {0} km/h has safe distance {1} m")
        @CsvSource({"1.0, 0.5", "50.0, 25.0", "200.0, 100.0"})
        void shouldCalculateRepresentativeAndBoundarySafeDistances(double speedKmh, double expectedSafeDistance) {
            assertEquals(expectedSafeDistance, cruise.safeDistanceFor(speedKmh), DOUBLE_EPSILON);
        }
    }

    @Nested
    @DisplayName("Cruise Decision Rules Tests via External CSV File")
    class CsvFileDecisionsTests {

        @ParameterizedTest(name = "[CSV] Speed {0} km/h, Distance {1} m => Mode {2}, Amount {3}")
        @CsvFileSource(resources = "/cruise_test_cases.csv", numLinesToSkip = 1)
        void shouldMakeCorrectDecision_fromCsvDataset(double speedKmh,
                                                      double distanceMeters,
                                                      AdaptiveCruise.Mode expectedMode,
                                                      double expectedAmount) {
            AdaptiveCruise.Decision decision = cruise.update(speedKmh, distanceMeters);

            assertAll(
                    () -> assertEquals(expectedMode, decision.mode(), "Decision mode mismatch"),
                    () -> assertEquals(expectedAmount, decision.amount(), DOUBLE_EPSILON, "Decision amount mismatch")
            );
        }
    }

    @Nested
    @DisplayName("Hysteresis and State Transition Dynamics Tests")
    class HysteresisTransitionTests {

        @ParameterizedTest(name = "At speed {0}, distance {1} should produce {2}")
        @CsvSource({
                "100.0, 25.0, EMERGENCY_BRAKE",
                "100.0, 25.001, BRAKE",
                "100.0, 50.0, BRAKE",
                "100.0, 60.0, HOLD"
        })
        void shouldRespectExactEmergencySafeAndReleaseBoundaries(double speedKmh,
                                                                  double distanceMeters,
                                                                  AdaptiveCruise.Mode expectedMode) {
            if (distanceMeters == 50.0) {
                cruise.update(speedKmh, 30.0);
            }
            AdaptiveCruise.Decision decision = cruise.update(speedKmh, distanceMeters);
            assertEquals(expectedMode, decision.mode());
        }

        @ParameterizedTest(name = "Speed {0} below target by {1} should accelerate by {1}")
        @CsvSource({"97.0, 3.0", "99.9, 0.1", "95.0, 5.0"})
        void shouldUseExactAccelerationAmountWhenGapIsBelowOrAtMaximumStep(double speedKmh, double expectedAmount) {
            AdaptiveCruise.Decision decision = cruise.update(speedKmh, 100.0);
            assertAll(
                    () -> assertEquals(AdaptiveCruise.Mode.ACCELERATE, decision.mode()),
                    () -> assertEquals(expectedAmount, decision.amount(), DOUBLE_EPSILON)
            );
        }

        @Test
        @DisplayName("Hysteresis should keep gentle 10% brake active when distance is between safeDistance and releaseDistance")
        void shouldApplyHysteresisBraking_whenDistanceExpandsBetweenSafeAndReleaseThresholds() {
            // Safe distance for 100 km/h = 50m, release distance = 60m
            // Step 1: Car detects close vehicle at 30m (< 50m safe) -> enters braking state
            AdaptiveCruise.Decision step1 = cruise.update(TARGET_SPEED_KMH, 30.0);
            assertEquals(AdaptiveCruise.Mode.BRAKE, step1.mode());
            assertEquals(40.0, step1.amount(), DOUBLE_EPSILON); // (50 - 30)/50 * 100 = 40%

            // Step 2: Distance expands to 55m (between safe 50m and release 60m).
            // Normal mode without hysteresis would accelerate, but hysteresis keeps gentle 10% brake
            AdaptiveCruise.Decision step2 = cruise.update(TARGET_SPEED_KMH, 55.0);
            assertAll(
                    () -> assertEquals(AdaptiveCruise.Mode.BRAKE, step2.mode(),
                            "Hysteresis must maintain BRAKE mode below release distance"),
                    () -> assertEquals(HYSTERESIS_BRAKE_PRESSURE, step2.amount(), DOUBLE_EPSILON,
                            "Hysteresis brake pressure must be 10.0%")
            );

            // Step 3: Distance reaches 60m (>= release distance 60m) -> hysteresis is released
            AdaptiveCruise.Decision step3 = cruise.update(TARGET_SPEED_KMH, 60.0);
            assertAll(
                    () -> assertEquals(AdaptiveCruise.Mode.HOLD, step3.mode(),
                            "At target speed and >= release distance, mode should transition to HOLD"),
                    () -> assertEquals(ZERO_AMOUNT, step3.amount(), DOUBLE_EPSILON)
            );
        }

        @Test
        @DisplayName("Hysteresis should transition to ACCELERATE once release distance is surpassed and speed is below target")
        void shouldAccelerate_afterHysteresisReleasedWhenSpeedBelowTarget() {
            // Speed 80 km/h: safeDistance = 40m, releaseDistance = 48m
            // Step 1: Close obstacle at 25m (< 40m) -> enters braking state
            cruise.update(LOWER_SPEED_KMH, 25.0);

            // Step 2: Distance opens up to 45m (between 40m and 48m) -> still braking due to hysteresis
            AdaptiveCruise.Decision step2 = cruise.update(LOWER_SPEED_KMH, 45.0);
            assertEquals(AdaptiveCruise.Mode.BRAKE, step2.mode());
            assertEquals(HYSTERESIS_BRAKE_PRESSURE, step2.amount(), DOUBLE_EPSILON);

            // Step 3: Distance reaches 50m (>= releaseDistance 48m) -> hysteresis cleared, vehicle accelerates!
            AdaptiveCruise.Decision step3 = cruise.update(LOWER_SPEED_KMH, 50.0);
            assertAll(
                    () -> assertEquals(AdaptiveCruise.Mode.ACCELERATE, step3.mode(),
                            "Vehicle must accelerate once hysteresis is released"),
                    () -> assertEquals(5.0, step3.amount(), DOUBLE_EPSILON,
                            "Acceleration step is capped at 5.0 km/h")
            );
        }

        @Test
        @DisplayName("Without prior braking state, distance between safe and release should accelerate immediately")
        void shouldAccelerateImmediately_whenNoPriorBrakingStateExists() {
            // Speed 80 km/h: safeDistance = 40m, releaseDistance = 48m
            // Initial tick with distance 45m (already > safeDistance 40m)
            AdaptiveCruise.Decision decision = cruise.update(LOWER_SPEED_KMH, 45.0);

            assertAll(
                    () -> assertEquals(AdaptiveCruise.Mode.ACCELERATE, decision.mode(),
                            "Without prior braking, distance > safeDistance should accelerate immediately"),
                    () -> assertEquals(5.0, decision.amount(), DOUBLE_EPSILON)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("multiStepDrivingSequenceProvider")
        void shouldExecuteMultiStepTrajectoryAccurately(String scenarioName,
                                                        List<TickInput> inputs,
                                                        List<AdaptiveCruise.Decision> expectedDecisions) {
            AdaptiveCruise localCruise = new AdaptiveCruise(TARGET_SPEED_KMH);

            for (int i = 0; i < inputs.size(); i++) {
                TickInput input = inputs.get(i);
                AdaptiveCruise.Decision expected = expectedDecisions.get(i);
                AdaptiveCruise.Decision actual = localCruise.update(input.speed(), input.distance());

                assertEquals(expected.mode(), actual.mode(),
                        scenarioName + " at tick " + i + " mode mismatch");
                assertEquals(expected.amount(), actual.amount(), DOUBLE_EPSILON,
                        scenarioName + " at tick " + i + " amount mismatch");
            }
        }

        private static Stream<Arguments> multiStepDrivingSequenceProvider() {
            return Stream.of(
                    Arguments.of(
                            "Emergency brake into hysteresis release sequence",
                            List.of(
                                    new TickInput(100.0, 20.0), // emergency (< 25m)
                                    new TickInput(100.0, 35.0), // still < safe 50m -> proportional brake
                                    new TickInput(100.0, 52.0), // in hysteresis [50, 60) -> 10% brake
                                    new TickInput(100.0, 65.0)  // released (>= 60m) -> HOLD
                            ),
                            List.of(
                                    new AdaptiveCruise.Decision(AdaptiveCruise.Mode.EMERGENCY_BRAKE, 100.0),
                                    new AdaptiveCruise.Decision(AdaptiveCruise.Mode.BRAKE, 30.0), // (50-35)/50*100 = 30%
                                    new AdaptiveCruise.Decision(AdaptiveCruise.Mode.BRAKE, 10.0),
                                    new AdaptiveCruise.Decision(AdaptiveCruise.Mode.HOLD, 0.0)
                            )
                    )
            );
        }
    }

    private record TickInput(double speed, double distance) {
    }
}
