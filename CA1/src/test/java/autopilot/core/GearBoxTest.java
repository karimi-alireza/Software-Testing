package autopilot.core;

import autopilot.exception.IllegalGearChangeException;
import autopilot.sensor.SpeedSensor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GearBox State Machine and Transition Tests")
class GearBoxTest {

    private static final double ZERO_SPEED_KMH = 0.0;
    private static final double LOW_SPEED_KMH = 3.0;
    private static final double REVERSE_LIMIT_SPEED_KMH = 5.0;
    private static final double HIGH_SPEED_KMH = 60.0;
    private static final double CRUISE_SPEED_KMH = 100.0;

    private MutableSpeedSensor speedSensor;
    private GearBox gearBox;

    @BeforeEach
    void setUp() {
        speedSensor = new MutableSpeedSensor(ZERO_SPEED_KMH);
        gearBox = new GearBox(speedSensor);
    }

    @Nested
    @DisplayName("Initialization and Dependency Tests")
    class InitializationTests {

        @Test
        @DisplayName("Initial gear should always be PARK (P)")
        void shouldInitializeInParkGear() {
            assertEquals(GearBox.Gear.P, gearBox.getCurrentGear(),
                    "GearBox must default to P (Park) upon initialization");
        }

        @Test
        @DisplayName("Constructor should throw NullPointerException when SpeedSensor is null")
        void shouldThrowNullPointerException_whenSpeedSensorIsNullInConstructor() {
            assertThrows(NullPointerException.class, () -> new GearBox(null),
                    "Constructor must reject null SpeedSensor");
        }

        @Test
        @DisplayName("setSpeedSensor should throw NullPointerException when sensor is null")
        void shouldThrowNullPointerException_whenSpeedSensorIsNullInSetter() {
            assertThrows(NullPointerException.class, () -> gearBox.setSpeedSensor(null),
                    "setSpeedSensor must reject null SpeedSensor");
        }

        @Test
        @DisplayName("setSpeedSensor should successfully update sensor reference")
        void shouldUpdateSpeedSensor_whenValidSensorProvided() {
            MutableSpeedSensor newSensor = new MutableSpeedSensor(HIGH_SPEED_KMH);
            gearBox.setSpeedSensor(newSensor);

            // Shifting P -> R at 60 km/h should fail using newSensor
            assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.R),
                    "GearBox should query the updated speed sensor");
        }

        @Test
        @DisplayName("changeTo should throw NullPointerException when target gear is null")
        void shouldThrowNullPointerException_whenTargetGearIsNull() {
            assertThrows(NullPointerException.class, () -> gearBox.changeTo(null),
                    "changeTo must reject null target gear");
        }
    }

    @Nested
    @DisplayName("Same Gear Transition Tests")
    class SameGearTransitionTests {

        @ParameterizedTest(name = "Selecting current gear {0} should be a no-op and never query sensor")
        @EnumSource(GearBox.Gear.class)
        void shouldDoNothing_whenTargetGearIsEqualToCurrentGear(GearBox.Gear gear) {
            transitionTo(gear);

            // Configure sensor to throw if queried; same-gear shift must return immediately
            gearBox.setSpeedSensor(() -> {
                throw new AssertionError("SpeedSensor must not be queried when shifting to the current gear");
            });

            gearBox.changeTo(gear);
            assertEquals(gear, gearBox.getCurrentGear(), "Current gear should remain unchanged");
        }
    }

    @Nested
    @DisplayName("Speed Validation Tests")
    class SpeedValidationTests {

        @ParameterizedTest(name = "Speed {0} is invalid and should throw IllegalArgumentException")
        @ValueSource(doubles = {-0.01, -5.0, -100.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
        void shouldThrowIllegalArgumentException_whenSpeedSensorReportsInvalidSpeed(double invalidSpeed) {
            speedSensor.setSpeedKmh(invalidSpeed);

            assertThrows(IllegalArgumentException.class, () -> gearBox.changeTo(GearBox.Gear.R),
                    "Gear change must validate speed is finite and non-negative");
        }
    }

    @Nested
    @DisplayName("Allowed Gear Transitions Tests")
    class AllowedTransitionsTests {

        @ParameterizedTest(name = "Shift P -> R at speed {0} km/h (< 5.0) should succeed")
        @ValueSource(doubles = {ZERO_SPEED_KMH, LOW_SPEED_KMH, 4.99})
        void shouldAllowShiftFromParkToReverse_whenSpeedIsBelowFiveKmh(double allowedSpeed) {
            speedSensor.setSpeedKmh(allowedSpeed);

            gearBox.changeTo(GearBox.Gear.R);

            assertEquals(GearBox.Gear.R, gearBox.getCurrentGear(),
                    "P -> R shift must succeed when speed is strictly below 5.0 km/h");
        }

        @Test
        @DisplayName("Shift R -> D should succeed when vehicle is stationary (0.0 km/h)")
        void shouldAllowShiftFromReverseToDrive_whenVehicleIsStationary() {
            transitionTo(GearBox.Gear.R);
            speedSensor.setSpeedKmh(ZERO_SPEED_KMH);

            gearBox.changeTo(GearBox.Gear.D);

            assertEquals(GearBox.Gear.D, gearBox.getCurrentGear(),
                    "R -> D shift must succeed at 0.0 km/h");
        }

        @Test
        @DisplayName("Shift D -> R should succeed when vehicle is stationary (0.0 km/h)")
        void shouldAllowShiftFromDriveToReverse_whenVehicleIsStationary() {
            transitionTo(GearBox.Gear.D);
            speedSensor.setSpeedKmh(ZERO_SPEED_KMH);

            gearBox.changeTo(GearBox.Gear.R);

            assertEquals(GearBox.Gear.R, gearBox.getCurrentGear(),
                    "D -> R shift must succeed at 0.0 km/h");
        }

        @ParameterizedTest(name = "Shift R -> N at speed {0} km/h should succeed")
        @ValueSource(doubles = {ZERO_SPEED_KMH, LOW_SPEED_KMH, HIGH_SPEED_KMH, CRUISE_SPEED_KMH})
        void shouldAllowShiftFromReverseToNeutral_atAnyValidSpeed(double speed) {
            transitionTo(GearBox.Gear.R);
            speedSensor.setSpeedKmh(speed);

            gearBox.changeTo(GearBox.Gear.N);

            assertEquals(GearBox.Gear.N, gearBox.getCurrentGear(),
                    "R -> N shift must be permitted at any valid speed");
        }

        @ParameterizedTest(name = "Shift N -> D at speed {0} km/h should succeed")
        @ValueSource(doubles = {ZERO_SPEED_KMH, LOW_SPEED_KMH, HIGH_SPEED_KMH, CRUISE_SPEED_KMH})
        void shouldAllowShiftFromNeutralToDrive_atAnyValidSpeed(double speed) {
            transitionTo(GearBox.Gear.N);
            speedSensor.setSpeedKmh(speed);

            gearBox.changeTo(GearBox.Gear.D);

            assertEquals(GearBox.Gear.D, gearBox.getCurrentGear(),
                    "N -> D shift must be permitted at any valid speed");
        }
    }

    @Nested
    @DisplayName("Disallowed Gear Transitions and Speed Violations Tests")
    class DisallowedTransitionsTests {

        @ParameterizedTest(name = "Shift P -> R at speed {0} km/h (>= 5.0) should throw IllegalGearChangeException")
        @ValueSource(doubles = {REVERSE_LIMIT_SPEED_KMH, 5.01, 10.0, HIGH_SPEED_KMH})
        void shouldDisallowShiftFromParkToReverse_whenSpeedIsFiveOrAbove(double invalidSpeed) {
            speedSensor.setSpeedKmh(invalidSpeed);

            IllegalGearChangeException exception = assertThrows(IllegalGearChangeException.class,
                    () -> gearBox.changeTo(GearBox.Gear.R));

            assertTrue(exception.getMessage().contains("Cannot change from P to R"),
                    "Exception message must specify invalid transition from P to R");
        }

        @ParameterizedTest(name = "Shift R -> D at speed {0} km/h (> 0.0) should throw IllegalGearChangeException")
        @ValueSource(doubles = {0.01, 1.0, 5.0, HIGH_SPEED_KMH})
        void shouldDisallowShiftFromReverseToDrive_whenVehicleIsNotStationary(double nonZeroSpeed) {
            transitionTo(GearBox.Gear.R);
            speedSensor.setSpeedKmh(nonZeroSpeed);

            assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.D),
                    "R -> D must be rejected if speed is greater than 0.0 km/h");
        }

        @ParameterizedTest(name = "Shift D -> R at speed {0} km/h (> 0.0) should throw IllegalGearChangeException")
        @ValueSource(doubles = {0.01, 1.0, 5.0, HIGH_SPEED_KMH})
        void shouldDisallowShiftFromDriveToReverse_whenVehicleIsNotStationary(double nonZeroSpeed) {
            transitionTo(GearBox.Gear.D);
            speedSensor.setSpeedKmh(nonZeroSpeed);

            assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.R),
                    "D -> R must be rejected if speed is greater than 0.0 km/h");
        }

        @ParameterizedTest(name = "Direct invalid transition from {0} to {1} at speed {2} km/h should be rejected")
        @CsvSource({
                "P, N, 0.0",
                "P, D, 0.0",
                "R, P, 0.0",
                "N, P, 0.0",
                "N, R, 0.0",
                "D, P, 0.0",
                "D, N, 0.0",
                "D, N, 50.0"
        })
        void shouldDisallowUnsupportedTransitions(GearBox.Gear fromGear, GearBox.Gear targetGear, double speed) {
            transitionTo(fromGear);
            speedSensor.setSpeedKmh(speed);

            IllegalGearChangeException exception = assertThrows(IllegalGearChangeException.class,
                    () -> gearBox.changeTo(targetGear));

            assertTrue(exception.getMessage().contains("Cannot change from " + fromGear + " to " + targetGear),
                    "Exception message must describe rejected gear change");
        }
    }

    private void transitionTo(GearBox.Gear target) {
        speedSensor.setSpeedKmh(ZERO_SPEED_KMH);
        switch (target) {
            case P -> {
                // If already at P, nothing needed
            }
            case R -> gearBox.changeTo(GearBox.Gear.R);
            case N -> {
                gearBox.changeTo(GearBox.Gear.R);
                gearBox.changeTo(GearBox.Gear.N);
            }
            case D -> {
                gearBox.changeTo(GearBox.Gear.R);
                gearBox.changeTo(GearBox.Gear.D);
            }
        }
    }

    private static class MutableSpeedSensor implements SpeedSensor {
        private double speedKmh;

        MutableSpeedSensor(double speedKmh) {
            this.speedKmh = speedKmh;
        }

        void setSpeedKmh(double speedKmh) {
            this.speedKmh = speedKmh;
        }

        @Override
        public double getSpeedKmh() {
            return speedKmh;
        }
    }
}
