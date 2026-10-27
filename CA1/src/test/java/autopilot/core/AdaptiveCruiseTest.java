package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    private static final double MAX_ACCELERATION_STEP_KMH = 5.0;
    private static final double PERCENT_SCALE = 100.0;

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
        void shouldCalculateSafeDistance_whenSpeedIsValid(double speedKmh, double expectedSafeDistance) {
            assertEquals(expectedSafeDistance, cruise.safeDistanceFor(speedKmh), DOUBLE_EPSILON,
                    "Safe distance must be exactly half the speed in meters");
        }

        @ParameterizedTest(name = "Representative speed {0} km/h has safe distance {1} m")
        @CsvSource({"1.0, 0.5", "50.0, 25.0", "200.0, 100.0"})
        void shouldCalculateSafeDistance_whenSpeedIsRepresentativeOrBoundaryValue(double speedKmh, double expectedSafeDistance) {
            assertEquals(expectedSafeDistance, cruise.safeDistanceFor(speedKmh), DOUBLE_EPSILON);
        }
    }

    @Nested
    @DisplayName("Cruise Decision Rules Tests via External CSV File")
    class CsvFileDecisionsTests {

        @ParameterizedTest(name = "[CSV] Speed {0} km/h, Distance {1} m => Mode {2}, Amount {3}")
        @CsvFileSource(resources = "/cruise_test_cases.csv", numLinesToSkip = 1)
        void shouldMakeExpectedDecision_whenInputsComeFromCsvDataset(double speedKmh,
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

        @Test
        @DisplayName("Exact safe distance without prior braking should hold with zero amount")
        void shouldHoldAtExactSafeDistance_whenNoPriorBrakingStateExists() {
            AdaptiveCruise.Decision decision = cruise.update(100.0, 50.0);

            assertAll(
                    () -> assertEquals(AdaptiveCruise.Mode.HOLD, decision.mode()),
                    () -> assertEquals(ZERO_AMOUNT, decision.amount(), DOUBLE_EPSILON)
            );
        }

        @Test
        @DisplayName("Zero speed and zero distance should safely request emergency braking")
        void shouldRequestEmergencyBraking_whenSpeedAndDistanceAreZero() {
            AdaptiveCruise zeroSpeedCruise = new AdaptiveCruise(50.0);

            AdaptiveCruise.Decision decision = zeroSpeedCruise.update(0.0, 0.0);

            assertAll(
                    () -> assertEquals(ZERO_AMOUNT, zeroSpeedCruise.safeDistanceFor(ZERO_SPEED_KMH), DOUBLE_EPSILON),
                    () -> assertEquals(AdaptiveCruise.Mode.EMERGENCY_BRAKE, decision.mode()),
                    () -> assertEquals(EMERGENCY_BRAKE_PRESSURE, decision.amount(), DOUBLE_EPSILON)
            );
        }

        @ParameterizedTest(name = "At speed {0}, distance {1} should produce {2}")
        @CsvSource({
                "100.0, 25.0, EMERGENCY_BRAKE",
                "100.0, 25.001, BRAKE",
                "100.0, 60.0, HOLD"
        })
        void shouldSelectExpectedMode_whenDistanceIsAtOrNearThreshold(double speedKmh,
                                                                  double distanceMeters,
                                                                  AdaptiveCruise.Mode expectedMode) {
            AdaptiveCruise.Decision decision = cruise.update(speedKmh, distanceMeters);
            assertEquals(expectedMode, decision.mode());
        }

        @Test
        @DisplayName("Small positive distance above emergency threshold produces proportional braking")
        void shouldCalculateProportionalPressure_whenDistanceIsSmallAndAboveEmergencyThreshold() {
            double expectedProportionalPressure = 25.0;
            AdaptiveCruise.Decision decision = cruise.update(0.002, 0.00075);
            assertAll(
                    () -> assertEquals(AdaptiveCruise.Mode.BRAKE, decision.mode()),
                    () -> assertEquals(expectedProportionalPressure, decision.amount(), DOUBLE_EPSILON)
            );
        }

        @ParameterizedTest(name = "Speed {0} accelerates by {1}")
        @CsvSource({"97.0, 3.0", "90.0, 5.0"})
        void shouldLimitAccelerationAmount_whenSpeedGapIsSmallOrLarge(double speed, double amount) {
            AdaptiveCruise.Decision decision = cruise.update(speed, 100.0);
            assertEquals(AdaptiveCruise.Mode.ACCELERATE, decision.mode());
            assertEquals(amount, decision.amount(), 1e-9);
        }

        @ParameterizedTest(name = "Speed {0} below target by {1} should accelerate by {1}")
        @CsvSource({"97.0, 3.0", "99.9, 0.1", "95.0, 5.0"})
        void shouldUseExactAccelerationAmount_whenGapIsBelowOrAtMaximumStep(double speedKmh, double expectedAmount) {
            AdaptiveCruise.Decision decision = cruise.update(speedKmh, 100.0);
            assertAll(
                    () -> assertEquals(AdaptiveCruise.Mode.ACCELERATE, decision.mode()),
                    () -> assertEquals(expectedAmount, decision.amount(), DOUBLE_EPSILON)
            );
        }

        @ParameterizedTest(name = "Prior braking retains gentle pressure at distance {0} m")
        @ValueSource(doubles = {40.0, 45.0, 47.99})
        void shouldMaintainHysteresisBraking_whenDistanceIsBetweenSafeAndReleaseThresholds(double distanceMeters) {
            enterBrakingState(LOWER_SPEED_KMH);
            AdaptiveCruise.Decision decision = cruise.update(LOWER_SPEED_KMH, distanceMeters);

            assertEquals(AdaptiveCruise.Mode.BRAKE, decision.mode());
            assertEquals(HYSTERESIS_BRAKE_PRESSURE, decision.amount(), DOUBLE_EPSILON);
        }

        @Test
        @DisplayName("Hysteresis releases at the release distance and permits acceleration")
        void shouldReleaseHysteresisAndAccelerate_whenDistanceReachesReleaseThreshold() {
            enterBrakingState(LOWER_SPEED_KMH);
            double releaseDistanceMeters = 48.0;
            AdaptiveCruise.Decision decision = cruise.update(LOWER_SPEED_KMH, releaseDistanceMeters);

            assertEquals(AdaptiveCruise.Mode.ACCELERATE, decision.mode());
            assertEquals(MAX_ACCELERATION_STEP_KMH, decision.amount(), DOUBLE_EPSILON);
        }

        @Test
        @DisplayName("Without prior braking, distance between safe and release should accelerate immediately")
        void shouldAccelerateImmediately_whenNoPriorBrakingStateExists() {
            double distanceBetweenThresholdsMeters = 45.0;
            AdaptiveCruise.Decision decision = cruise.update(LOWER_SPEED_KMH, distanceBetweenThresholdsMeters);

            assertEquals(AdaptiveCruise.Mode.ACCELERATE, decision.mode());
            assertEquals(MAX_ACCELERATION_STEP_KMH, decision.amount(), DOUBLE_EPSILON);
        }

        @Test
        @DisplayName("Emergency braking progresses through proportional braking and hysteresis to HOLD")
        void shouldTransitionThroughBrakingModes_whenObstacleDistanceIncreasesAcrossTicks() {
            double emergencyDistanceMeters = 20.0;
            double proportionalDistanceMeters = 35.0;
            double hysteresisDistanceMeters = 52.0;
            double releaseDistanceMeters = 60.0;
            double safeDistanceMeters = 50.0;
            double expectedProportionalPressure =
                    (safeDistanceMeters - proportionalDistanceMeters) / safeDistanceMeters * PERCENT_SCALE;

            AdaptiveCruise.Decision emergency = cruise.update(TARGET_SPEED_KMH, emergencyDistanceMeters);
            assertEquals(AdaptiveCruise.Mode.EMERGENCY_BRAKE, emergency.mode());
            assertEquals(EMERGENCY_BRAKE_PRESSURE, emergency.amount(), DOUBLE_EPSILON);

            AdaptiveCruise.Decision proportional = cruise.update(TARGET_SPEED_KMH, proportionalDistanceMeters);
            assertEquals(AdaptiveCruise.Mode.BRAKE, proportional.mode());
            assertEquals(expectedProportionalPressure, proportional.amount(), DOUBLE_EPSILON);

            AdaptiveCruise.Decision hysteresis = cruise.update(TARGET_SPEED_KMH, hysteresisDistanceMeters);
            assertEquals(AdaptiveCruise.Mode.BRAKE, hysteresis.mode());
            assertEquals(HYSTERESIS_BRAKE_PRESSURE, hysteresis.amount(), DOUBLE_EPSILON);

            AdaptiveCruise.Decision released = cruise.update(TARGET_SPEED_KMH, releaseDistanceMeters);
            assertEquals(AdaptiveCruise.Mode.HOLD, released.mode());
            assertEquals(ZERO_AMOUNT, released.amount(), DOUBLE_EPSILON);
        }
    }

    private void enterBrakingState(double speedKmh) {
        double distanceBelowSafeThresholdMeters = speedKmh * 0.5 * 0.75;
        cruise.update(speedKmh, distanceBelowSafeThresholdMeters);
    }
}
