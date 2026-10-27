package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("SteeringController Dynamics and Limit Tests")
class SteeringControllerTest {

    private static final double ZERO_DEGREES = 0.0;
    private static final double ZERO_SPEED_KMH = 0.0;
    private static final double LOW_SPEED_KMH = 50.0;
    private static final double HIGH_SPEED_THRESHOLD_KMH = 120.0;
    private static final double HIGH_SPEED_KMH = 140.0;

    private static final double MAX_CHANGE_PER_TICK = 60.0;
    private static final double PHYSICAL_LIMIT_DEGREES = 450.0;
    private static final double HIGH_SPEED_LIMIT_DEGREES = 90.0;
    private static final double DOUBLE_EPSILON = 1e-9;

    private SteeringController controller;

    @BeforeEach
    void setUp() {
        controller = new SteeringController();
    }

    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {

        @Test
        @DisplayName("Initial steering angle and target angle should both be zero")
        void shouldInitializeWithZeroAngles_whenControllerIsCreated() {
            assertAll(
                    () -> assertEquals(ZERO_DEGREES, controller.getCurrentAngleDegrees(), "Current angle should be 0.0°"),
                    () -> assertEquals(ZERO_DEGREES, controller.getTargetAngleDegrees(), "Target angle should be 0.0°")
            );
        }
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @ParameterizedTest(name = "Requested angle {0} is non-finite and should throw IllegalArgumentException")
        @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
        void shouldThrowIllegalArgumentException_whenRequestedAngleIsNotFinite(double invalidAngle) {
            assertThrows(IllegalArgumentException.class,
                    () -> controller.update(invalidAngle, LOW_SPEED_KMH),
                    "Requested angle must be finite");
        }

        @ParameterizedTest(name = "Speed {0} is invalid and should throw IllegalArgumentException")
        @ValueSource(doubles = {-0.01, -10.0, -100.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
        void shouldThrowIllegalArgumentException_whenSpeedIsNegativeOrNonFinite(double invalidSpeed) {
            assertThrows(IllegalArgumentException.class,
                    () -> controller.update(ZERO_DEGREES, invalidSpeed),
                    "Speed must be a finite non-negative value");
        }
    }

    @Nested
    @DisplayName("Angle Clamping and Speed Limits Tests")
    class ClampingTests {

        @ParameterizedTest(name = "At normal speed {0} km/h (<= 120), requested {1}° clamps target to {2}°")
        @CsvSource({
                "0.0, 30.0, 30.0",
                "60.0, -45.0, -45.0",
                "120.0, 450.0, 450.0",
                "120.0, -450.0, -450.0",
                "100.0, 500.0, 450.0",
                "100.0, -600.0, -450.0"
        })
        void shouldClampToPhysicalLimit_whenSpeedIsAtOrBelowHighSpeedThreshold(double speedKmh,
                                                                              double requestedAngle,
                                                                              double expectedTarget) {
            controller.update(requestedAngle, speedKmh);

            assertEquals(expectedTarget, controller.getTargetAngleDegrees(),
                    "At speed <= 120 km/h, target angle must be clamped to [-450°, +450°]");
        }

        @ParameterizedTest(name = "At high speed {0} km/h (> 120), requested {1}° clamps target to {2}°")
        @CsvSource({
                "120.01, 45.0, 45.0",
                "130.0, -80.0, -80.0",
                "150.0, 90.0, 90.0",
                "150.0, -90.0, -90.0",
                "140.0, 100.0, 90.0",
                "140.0, -200.0, -90.0",
                "180.0, 450.0, 90.0",
                "180.0, -450.0, -90.0"
        })
        void shouldClampToHighSpeedLimit_whenSpeedExceedsHighSpeedThreshold(double speedKmh,
                                                                           double requestedAngle,
                                                                           double expectedTarget) {
            controller.update(requestedAngle, speedKmh);

            assertEquals(expectedTarget, controller.getTargetAngleDegrees(),
                    "At speed > 120 km/h, target angle must be restricted to [-90°, +90°]");
        }
    }

    @Nested
    @DisplayName("Rate Limiting (Per-Tick Movement) Tests")
    class RateLimitingTests {

        @Test
        @DisplayName("Increasing speed clamps the target while preserving the rate limit")
        void shouldClampTarget_whenSpeedIncreasesBetweenTicks() {
            assertEquals(MAX_CHANGE_PER_TICK, controller.update(PHYSICAL_LIMIT_DEGREES, LOW_SPEED_KMH), DOUBLE_EPSILON);
            assertEquals(PHYSICAL_LIMIT_DEGREES, controller.getTargetAngleDegrees(), DOUBLE_EPSILON);
            assertEquals(HIGH_SPEED_LIMIT_DEGREES, controller.update(PHYSICAL_LIMIT_DEGREES, HIGH_SPEED_KMH), DOUBLE_EPSILON);
            assertEquals(HIGH_SPEED_LIMIT_DEGREES, controller.getCurrentAngleDegrees(), DOUBLE_EPSILON);
            assertEquals(HIGH_SPEED_LIMIT_DEGREES, controller.getTargetAngleDegrees(), DOUBLE_EPSILON);
        }

        @Test
        @DisplayName("High-speed clamp and per-tick rate limit should compose across two ticks")
        void shouldApplyRateLimit_whenHighSpeedLimitsTargetAngle() {
            assertEquals(MAX_CHANGE_PER_TICK, controller.update(PHYSICAL_LIMIT_DEGREES, HIGH_SPEED_KMH),
                    "First tick must move at most 60 degrees toward the clamped 90-degree target");
            assertEquals(HIGH_SPEED_LIMIT_DEGREES, controller.update(PHYSICAL_LIMIT_DEGREES, HIGH_SPEED_KMH),
                    "Second tick must reach the high-speed clamp of 90 degrees");
            assertEquals(HIGH_SPEED_LIMIT_DEGREES, controller.getTargetAngleDegrees());
        }

        @ParameterizedTest(name = "Target {0}° within 60° should be reached in a single tick")
        @ValueSource(doubles = {0.0, 30.0, -30.0, 60.0, -60.0})
        void shouldReachTargetDirectly_whenAngleDifferenceIsWithinMaxChangePerTick(double requestedAngle) {
            double resultingAngle = controller.update(requestedAngle, LOW_SPEED_KMH);

            assertAll(
                    () -> assertEquals(requestedAngle, resultingAngle, "Resulting angle should match target"),
                    () -> assertEquals(requestedAngle, controller.getCurrentAngleDegrees(), "Current angle should match target")
            );
        }

        @ParameterizedTest(name = "Requested {0}° should change by ±60° on first tick to {1}°")
        @CsvSource({
                "100.0, 60.0",
                "-100.0, -60.0",
                "450.0, 60.0",
                "-450.0, -60.0"
        })
        void shouldLimitAngleChangeToSixtyDegreesPerTick_whenDifferenceExceedsLimit(double requestedAngle,
                                                                                   double expectedFirstTickAngle) {
            double resultingAngle = controller.update(requestedAngle, LOW_SPEED_KMH);

            assertEquals(expectedFirstTickAngle, resultingAngle,
                    "Steering change must be capped at 60° per tick");
        }

        @Test
        @DisplayName("Should smoothly converge to large angle across multiple consecutive ticks")
        void shouldConvergeToTarget_whenAngleDifferenceExceedsTickLimit() {
            double target = 150.0;

            double secondTickAngle = MAX_CHANGE_PER_TICK * 2;
            assertEquals(MAX_CHANGE_PER_TICK, controller.update(target, LOW_SPEED_KMH));
            assertEquals(secondTickAngle, controller.update(target, LOW_SPEED_KMH));
            assertEquals(target, controller.update(target, LOW_SPEED_KMH));
            assertEquals(target, controller.getCurrentAngleDegrees());
        }

        @Test
        @DisplayName("Should smoothly change direction from positive angle to negative angle")
        void shouldReverseDirection_whenTargetAngleIsOpposite() {
            controller.update(MAX_CHANGE_PER_TICK, LOW_SPEED_KMH);
            assertEquals(MAX_CHANGE_PER_TICK, controller.getCurrentAngleDegrees());

            double afterReversalStep = controller.update(-MAX_CHANGE_PER_TICK, LOW_SPEED_KMH);
            assertEquals(ZERO_DEGREES, afterReversalStep);

            double finalStep = controller.update(-MAX_CHANGE_PER_TICK, LOW_SPEED_KMH);
            assertEquals(-MAX_CHANGE_PER_TICK, finalStep);
        }
    }

    @Nested
    @DisplayName("Self-Centering Tests")
    class SelfCenteringTests {

        @Test
        @DisplayName("selfCenter should return immediately to 0.0° when difference is within 60°")
        void shouldCenterInSingleTick_whenAngleDifferenceIsWithinTickLimit() {
            double initialAngle = 45.0;
            controller.update(initialAngle, LOW_SPEED_KMH);
            assertEquals(initialAngle, controller.getCurrentAngleDegrees());

            double centeredAngle = controller.selfCenter();

            assertAll(
                    () -> assertEquals(ZERO_DEGREES, centeredAngle, "Should center to 0.0°"),
                    () -> assertEquals(ZERO_DEGREES, controller.getCurrentAngleDegrees(), "Current angle should be 0.0°"),
                    () -> assertEquals(ZERO_DEGREES, controller.getTargetAngleDegrees(), "Target angle should be 0.0°")
            );
        }

        @Test
        @DisplayName("selfCenter should return immediately to 0.0° from negative angle within 60°")
        void shouldCenterInSingleTick_whenCurrentAngleIsNegative() {
            controller.update(-50.0, LOW_SPEED_KMH);

            double centeredAngle = controller.selfCenter();

            assertEquals(ZERO_DEGREES, centeredAngle);
        }

        @Test
        @DisplayName("selfCenter when already at zero should remain at zero")
        void shouldRemainAtZero_whenAlreadyCentered() {
            assertEquals(ZERO_DEGREES, controller.selfCenter());
            assertEquals(ZERO_DEGREES, controller.getCurrentAngleDegrees());
        }
    }
}
