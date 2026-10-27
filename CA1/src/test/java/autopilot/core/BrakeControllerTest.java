package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BrakeController Braking Logic and ABS Tests")
class BrakeControllerTest {

    private static final double ZERO_PRESSURE = 0.0;
    private static final double MODERATE_PRESSURE = 50.0;
    private static final double HIGH_PRESSURE = 95.0;
    private static final double EXCESSIVE_PRESSURE = 150.0;
    private static final double MAX_PRESSURE = 100.0;

    private static final double LOW_SPEED_KMH = 20.0;
    private static final double HIGH_SPEED_KMH = 60.0;

    private static final double ABS_HIGH_PULSE = 90.0;
    private static final double ABS_LOW_PULSE = 60.0;

    private BrakeController controller;

    @BeforeEach
    void setUp() {
        controller = new BrakeController();
    }

    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {

        @Test
        @DisplayName("Initial brake pressure should be 0.0% and warning should be inactive")
        void shouldInitializeWithZeroPressureAndNoWarning() {
            assertAll(
                    () -> assertEquals(ZERO_PRESSURE, controller.getPressure(), "Initial pressure should be 0.0%"),
                    () -> assertFalse(controller.isWarningActive(), "Warning indicator should initially be inactive")
            );
        }
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @ParameterizedTest(name = "Pressure {0} is invalid and should throw IllegalArgumentException")
        @ValueSource(doubles = {-0.01, -25.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
        void shouldThrowIllegalArgumentException_whenPressureIsNegativeOrNonFinite(double invalidPressure) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> controller.apply(invalidPressure, HIGH_SPEED_KMH, GearBox.Gear.D));
            assertEquals("pressure must be a finite non-negative value", exception.getMessage());
        }

        @ParameterizedTest(name = "Speed {0} is invalid and should throw IllegalArgumentException")
        @ValueSource(doubles = {-0.01, -50.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
        void shouldThrowIllegalArgumentException_whenSpeedIsNegativeOrNonFinite(double invalidSpeed) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> controller.apply(MODERATE_PRESSURE, invalidSpeed, GearBox.Gear.D));
            assertEquals("speed must be a finite non-negative value", exception.getMessage());
        }

        @Test
        @DisplayName("apply should throw NullPointerException when gear is null")
        void shouldThrowNullPointerException_whenGearIsNullInApply() {
            NullPointerException exception = assertThrows(NullPointerException.class,
                    () -> controller.apply(MODERATE_PRESSURE, HIGH_SPEED_KMH, null));
            assertEquals("gear must not be null", exception.getMessage());
        }

        @Test
        @DisplayName("emergencyBrake should throw NullPointerException when gear is null")
        void shouldThrowNullPointerException_whenGearIsNullInEmergencyBrake() {
            NullPointerException exception = assertThrows(NullPointerException.class,
                    () -> controller.emergencyBrake(null));
            assertEquals("gear must not be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Gear Restriction Tests")
    class GearRestrictionTests {

        @Test
        @DisplayName("apply should throw IllegalStateException when gear is PARK (P)")
        void shouldThrowIllegalStateException_whenApplyingBrakesInParkGear() {
            assertThrows(IllegalStateException.class,
                    () -> controller.apply(MODERATE_PRESSURE, HIGH_SPEED_KMH, GearBox.Gear.P),
                    "Braking must be unavailable when gearbox is in PARK");
        }

        @Test
        @DisplayName("emergencyBrake should throw IllegalStateException when gear is PARK (P)")
        void shouldThrowIllegalStateException_whenEmergencyBrakingInParkGear() {
            assertThrows(IllegalStateException.class,
                    () -> controller.emergencyBrake(GearBox.Gear.P),
                    "Emergency braking must be unavailable when gearbox is in PARK");
        }

        @ParameterizedTest(name = "Braking should be permitted in gear {0}")
        @EnumSource(value = GearBox.Gear.class, names = {"R", "N", "D"})
        void shouldAllowBraking_inAllGearsExceptPark(GearBox.Gear allowedGear) {
            double resultingPressure = controller.apply(MODERATE_PRESSURE, LOW_SPEED_KMH, allowedGear);

            assertEquals(MODERATE_PRESSURE, resultingPressure,
                    "Braking should succeed in R, N, and D gears");
        }
    }

    @Nested
    @DisplayName("Normal (Non-ABS) Braking Tests")
    class NormalBrakingTests {

        @ParameterizedTest(name = "Requested pressure {0}% should apply {1}%")
        @CsvSource({
                "0.0, 0.0",
                "25.5, 25.5",
                "50.0, 50.0",
                "80.0, 80.0"
        })
        void shouldApplyExactPressure_whenPressureIsWithinNormalRange(double requestedPressure, double expectedPressure) {
            double applied = controller.apply(requestedPressure, HIGH_SPEED_KMH, GearBox.Gear.D);

            assertAll(
                    () -> assertEquals(expectedPressure, applied, "Applied pressure must match requested"),
                    () -> assertEquals(expectedPressure, controller.getPressure(), "Stored pressure must match applied"),
                    () -> assertFalse(controller.isWarningActive(), "Normal braking should not activate warning")
            );
        }

        @ParameterizedTest(name = "Requested {0}% at speed {1} km/h (boundary conditions where ABS does not engage)")
        @CsvSource({
                "80.0, 60.0, 80.0",   // pressure == 80.0 (not strictly > 80.0)
                "90.0, 30.0, 90.0",   // speed == 30.0 (not strictly > 30.0)
                "80.0, 30.0, 80.0",   // both at thresholds
                "100.0, 20.0, 100.0", // high pressure but speed below threshold
                "150.0, 10.0, 100.0"  // over 100% capped, but low speed
        })
        void shouldNotEngageAbs_whenEitherPressureOrSpeedDoesNotExceedThreshold(double requestedPressure,
                                                                               double speedKmh,
                                                                               double expectedBoundedPressure) {
            double applied = controller.apply(requestedPressure, speedKmh, GearBox.Gear.D);

            assertEquals(expectedBoundedPressure, applied,
                    "ABS must not activate unless BOTH pressure > 80% and speed > 30 km/h");
        }
    }

    @Nested
    @DisplayName("Anti-Lock Braking System (ABS) Tests")
    class AntiLockBrakingSystemTests {

        @ParameterizedTest(name = "Boundary ABS activation: pressure {0}%, speed {1} km/h")
        @CsvSource({
                "80.01, 30.01",
                "90.0, 40.0",
                "100.0, 80.0",
                "120.0, 100.0"
        })
        void shouldActivateAbs_whenBothPressureAndSpeedExceedThresholds(double requestedPressure, double speedKmh) {
            double applied = controller.apply(requestedPressure, speedKmh, GearBox.Gear.D);

            assertEquals(ABS_HIGH_PULSE, applied,
                    "First pulse of activated ABS must be the HIGH pulse (90.0%)");
        }

        @Test
        @DisplayName("ABS should alternate between HIGH pulse (90%) and LOW pulse (60%) on consecutive ticks")
        void shouldAlternateAbsPulsesOnConsecutiveTicks() {
            // Tick 1: ABS High (90%)
            assertEquals(ABS_HIGH_PULSE, controller.apply(HIGH_PRESSURE, HIGH_SPEED_KMH, GearBox.Gear.D));
            // Tick 2: ABS Low (60%)
            assertEquals(ABS_LOW_PULSE, controller.apply(HIGH_PRESSURE, HIGH_SPEED_KMH, GearBox.Gear.D));
            // Tick 3: ABS High (90%)
            assertEquals(ABS_HIGH_PULSE, controller.apply(HIGH_PRESSURE, HIGH_SPEED_KMH, GearBox.Gear.D));
            // Tick 4: ABS Low (60%)
            assertEquals(ABS_LOW_PULSE, controller.apply(HIGH_PRESSURE, HIGH_SPEED_KMH, GearBox.Gear.D));
        }

        @ParameterizedTest(name = "Sequence check: {0}")
        @MethodSource("absPulseSequenceProvider")
        void shouldFollowExpectedPulseSequence(String description, double[] requestedPressures, double[] expectedOutputs) {
            BrakeController localController = new BrakeController();
            for (int i = 0; i < requestedPressures.length; i++) {
                double result = localController.apply(requestedPressures[i], HIGH_SPEED_KMH, GearBox.Gear.D);
                assertEquals(expectedOutputs[i], result, "Mismatch at step " + i + " in " + description);
            }
        }

        private static Stream<org.junit.jupiter.params.provider.Arguments> absPulseSequenceProvider() {
            return Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of(
                            "Continuous ABS engagement",
                            new double[]{90.0, 90.0, 90.0, 90.0, 90.0},
                            new double[]{90.0, 60.0, 90.0, 60.0, 90.0}
                    )
            );
        }

        @Test
        @DisplayName("ABS pulse sequence should reset to HIGH pulse after a non-ABS brake application")
        void shouldResetAbsPulseToHigh_afterConditionBecomesNonAbs() {
            // Tick 1 (ABS): High pulse 90%
            assertEquals(ABS_HIGH_PULSE, controller.apply(HIGH_PRESSURE, HIGH_SPEED_KMH, GearBox.Gear.D));

            // Tick 2: Non-ABS (moderate pressure 50%)
            assertEquals(MODERATE_PRESSURE, controller.apply(MODERATE_PRESSURE, HIGH_SPEED_KMH, GearBox.Gear.D));

            // Tick 3 (ABS re-engaged): Should start fresh with High pulse 90%, NOT continue to Low pulse 60%
            assertEquals(ABS_HIGH_PULSE, controller.apply(HIGH_PRESSURE, HIGH_SPEED_KMH, GearBox.Gear.D));
        }

        @Test
        @DisplayName("ABS pulse sequence should reset to HIGH pulse when speed drops below threshold")
        void shouldResetAbsPulseToHigh_whenSpeedDropsBelowThreshold() {
            // Tick 1 (ABS): High pulse 90%
            assertEquals(ABS_HIGH_PULSE, controller.apply(HIGH_PRESSURE, HIGH_SPEED_KMH, GearBox.Gear.D));

            // Tick 2: Speed drops below 30 km/h -> ABS disengages, applies requested pressure (95%)
            assertEquals(HIGH_PRESSURE, controller.apply(HIGH_PRESSURE, LOW_SPEED_KMH, GearBox.Gear.D));

            // Tick 3: Speed goes back up -> ABS re-engages, pulse must start at High (90%)
            assertEquals(ABS_HIGH_PULSE, controller.apply(HIGH_PRESSURE, HIGH_SPEED_KMH, GearBox.Gear.D));
        }
    }

    @Nested
    @DisplayName("Emergency Brake Tests")
    class EmergencyBrakeTests {

        @ParameterizedTest(name = "Emergency braking in gear {0} should set 100% pressure and activate warning")
        @EnumSource(value = GearBox.Gear.class, names = {"R", "N", "D"})
        void shouldApplyFullPressureAndActivateWarning_onEmergencyBrake(GearBox.Gear gear) {
            controller.emergencyBrake(gear);

            assertAll(
                    () -> assertEquals(MAX_PRESSURE, controller.getPressure(), "Pressure must be 100%"),
                    () -> assertTrue(controller.isWarningActive(), "Warning indicator must be activated")
            );
        }

        @Test
        @DisplayName("emergencyBrake should reset ABS pulse alternation so subsequent ABS starts with High pulse")
        void shouldResetAbsPulse_whenEmergencyBrakeIsCalled() {
            // Engage ABS once -> next would be Low pulse
            controller.apply(HIGH_PRESSURE, HIGH_SPEED_KMH, GearBox.Gear.D);

            // Trigger emergency brake
            controller.emergencyBrake(GearBox.Gear.D);

            // Subsequent ABS application should start at High pulse (90%)
            assertEquals(ABS_HIGH_PULSE, controller.apply(HIGH_PRESSURE, HIGH_SPEED_KMH, GearBox.Gear.D));
        }

        @Test
        @DisplayName("Warning remains active on a later ordinary brake tick under current implementation")
        void shouldKeepWarningActive_afterEmergencyThenOrdinaryTick() {
            controller.emergencyBrake(GearBox.Gear.D);
            assertTrue(controller.isWarningActive());

            controller.apply(MODERATE_PRESSURE, LOW_SPEED_KMH, GearBox.Gear.D);
            assertTrue(controller.isWarningActive(),
                    "Current implementation does not reset warningActive during apply");
        }

        @Test
        @DisplayName("Pressure above 100% is capped at exactly 100%")
        void shouldCapRequestedPressureAtMaximum() {
            assertEquals(MAX_PRESSURE,
                    controller.apply(EXCESSIVE_PRESSURE, LOW_SPEED_KMH, GearBox.Gear.D));
        }
    }
}
