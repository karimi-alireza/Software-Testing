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
        void shouldInitializeWithZeroAngles() {
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
        @DisplayName("High-speed clamp and per-tick rate limit should compose across two ticks")
        void shouldApplyRateLimitBeforeReachingHighSpeedAngleLimit() {
            assertEquals(60.0, controller.update(450.0, 140.0),
                    "First tick must move at most 60 degrees toward the clamped 90-degree target");
            assertEquals(90.0, controller.update(450.0, 140.0),
                    "Second tick must reach the high-speed clamp of 90 degrees");
            assertEquals(90.0, controller.getTargetAngleDegrees());
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
        void shouldConvergeToTargetAcrossMultipleTicks() {
            double target = 150.0;

            // Tick 1: 0 -> 60
            assertEquals(60.0, controller.update(target, LOW_SPEED_KMH));
            // Tick 2: 60 -> 120
            assertEquals(120.0, controller.update(target, LOW_SPEED_KMH));
            // Tick 3: 120 -> 150 (remaining diff is 30 <= 60)
            assertEquals(150.0, controller.update(target, LOW_SPEED_KMH));
            assertEquals(target, controller.getCurrentAngleDegrees());
        }

        @Test
        @DisplayName("Should smoothly change direction from positive angle to negative angle")
        void shouldReverseDirectionSmoothly() {
            // First reach 60°
            controller.update(60.0, LOW_SPEED_KMH);
            assertEquals(60.0, controller.getCurrentAngleDegrees());

            // Next request -60°: difference is -120°, first step should move down by 60° to 0°
            double afterReversalStep = controller.update(-60.0, LOW_SPEED_KMH);
            assertEquals(ZERO_DEGREES, afterReversalStep);

            // Second step should reach -60°
            double finalStep = controller.update(-60.0, LOW_SPEED_KMH);
            assertEquals(-60.0, finalStep);
        }
    }

    @Nested
    @DisplayName("Self-Centering Tests")
    class SelfCenteringTests {

        @Test
        @DisplayName("selfCenter should return immediately to 0.0° when difference is within 60°")
        void shouldCenterInSingleTick_whenAngleDifferenceIsWithinTickLimit() {
            controller.update(45.0, LOW_SPEED_KMH);
            assertEquals(45.0, controller.getCurrentAngleDegrees());

            double centeredAngle = controller.selfCenter();

            assertAll(
                    () -> assertEquals(ZERO_DEGREES, centeredAngle, "Should center to 0.0°"),
                    () -> assertEquals(ZERO_DEGREES, controller.getCurrentAngleDegrees(), "Current angle should be 0.0°"),
                    () -> assertEquals(ZERO_DEGREES, controller.getTargetAngleDegrees(), "Target angle should be 0.0°")
            );
        }

        @Test
        @DisplayName("selfCenter should return immediately to 0.0° from negative angle within 60°")
        void shouldCenterFromNegativeAngle_inSingleTick() {
            controller.update(-50.0, LOW_SPEED_KMH);

            double centeredAngle = controller.selfCenter();

            assertEquals(ZERO_DEGREES, centeredAngle);
        }

        @Test
        @DisplayName("selfCenter should step toward 0.0° by 60° per tick when angle is large")
        void shouldCenterAcrossMultipleTicks_whenAngleIsLarge() {
            // Set angle to 150° in 3 ticks
            controller.update(150.0, LOW_SPEED_KMH);
            controller.update(150.0, LOW_SPEED_KMH);
            controller.update(150.0, LOW_SPEED_KMH);
            assertEquals(150.0, controller.getCurrentAngleDegrees());

            // Centering: 150 -> 90 -> 30 -> 0
            assertEquals(90.0, controller.selfCenter());
            assertEquals(30.0, controller.selfCenter());
            assertEquals(ZERO_DEGREES, controller.selfCenter());
        }

        @Test
        @DisplayName("selfCenter when already at zero should remain at zero")
        void shouldRemainAtZero_whenAlreadyCentered() {
            assertEquals(ZERO_DEGREES, controller.selfCenter());
            assertEquals(ZERO_DEGREES, controller.getCurrentAngleDegrees());
        }
    }
}
