package autopilot.core;

import autopilot.exception.SensorFailureException;
import autopilot.sensor.LaneSensor;
import autopilot.sensor.ObstacleSensor;
import autopilot.sensor.SpeedSensor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AutoDriver Coordination Tests")
class AutoDriverTest {

    private static final double TARGET_SPEED_KMH = 80.0;
    private static final double NORMAL_SPEED_KMH = 80.0;
    private static final double LOWER_SPEED_KMH = 60.0;
    private static final double CLEAR_ROAD_DISTANCE_METERS = 100.0;
    private static final double PROPORTIONAL_BRAKE_DISTANCE_METERS = 30.0;
    private static final double EMERGENCY_DISTANCE_METERS = 10.0;
    private static final double CENTER_LANE_OFFSET_METERS = 0.0;

    private static final double ZERO_STEERING_ANGLE = 0.0;
    private static final double ZERO_BRAKE_PRESSURE = 0.0;
    private static final double SAFE_MODE_BRAKE_PRESSURE = 30.0;
    private static final double MAX_BRAKE_PRESSURE = 100.0;

    private SpeedSensor operationalSpeedSensor;
    private ObstacleSensor operationalObstacleSensor;
    private LaneSensor operationalLaneSensor;
    private AutoDriver defaultAutoDriver;

    @BeforeEach
    void setUp() {
        operationalSpeedSensor = () -> NORMAL_SPEED_KMH;
        operationalObstacleSensor = () -> CLEAR_ROAD_DISTANCE_METERS;
        operationalLaneSensor = () -> CENTER_LANE_OFFSET_METERS;

        defaultAutoDriver = new AutoDriver(
                operationalSpeedSensor,
                operationalObstacleSensor,
                operationalLaneSensor,
                TARGET_SPEED_KMH
        );
    }

    @Nested
    @DisplayName("Constructor Input Validation Tests")
    class ConstructorValidationTests {

        @ParameterizedTest(name = "Constructor should throw NullPointerException when {0} is null")
        @MethodSource("nullSensorProvider")
        void shouldThrowNullPointerException_whenAnySensorIsNull(String description,
                                                                 SpeedSensor speed,
                                                                 ObstacleSensor obstacle,
                                                                 LaneSensor lane) {
            assertThrows(NullPointerException.class, () ->
                    new AutoDriver(speed, obstacle, lane, TARGET_SPEED_KMH)
            );
        }

        private static Stream<Arguments> nullSensorProvider() {
            SpeedSensor validSpeed = () -> NORMAL_SPEED_KMH;
            ObstacleSensor validObstacle = () -> CLEAR_ROAD_DISTANCE_METERS;
            LaneSensor validLane = () -> CENTER_LANE_OFFSET_METERS;

            return Stream.of(
                    Arguments.of("speedSensor", null, validObstacle, validLane),
                    Arguments.of("obstacleSensor", validSpeed, null, validLane),
                    Arguments.of("laneSensor", validSpeed, validObstacle, null)
            );
        }

        @ParameterizedTest(name = "Target speed {0} is invalid and should throw IllegalArgumentException")
        @ValueSource(doubles = {-1.0, -50.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
        void shouldThrowIllegalArgumentException_whenTargetSpeedIsInvalid(double invalidTargetSpeed) {
            assertThrows(IllegalArgumentException.class, () ->
                    new AutoDriver(
                            operationalSpeedSensor,
                            operationalObstacleSensor,
                            operationalLaneSensor,
                            invalidTargetSpeed
                    )
            );
        }
    }

    @Nested
    @DisplayName("Steering and Lane Deviation Tests")
    class SteeringControlTests {

        @ParameterizedTest(name = "Lane offset {0}m within threshold [-0.5, 0.5] should keep steering centered (0.0°)")
        @ValueSource(doubles = {0.0, 0.5, -0.5, 0.49, -0.49})
        void shouldKeepSteeringCentered_whenLaneDeviationWithinThreshold(double laneOffsetMeters) {
            LaneSensor laneSensorWithinThreshold = () -> laneOffsetMeters;
            AutoDriver driver = new AutoDriver(
                    operationalSpeedSensor,
                    operationalObstacleSensor,
                    laneSensorWithinThreshold,
                    TARGET_SPEED_KMH
            );

            AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

            assertEquals(ZERO_STEERING_ANGLE, result.steeringAngleDegrees(),
                    "Steering angle should remain centered when offset is within [-0.5, 0.5]");
        }

        @ParameterizedTest(name = "Lane offset {0}m should steer towards {1}° to correct deviation")
        @CsvSource({
                "1.0, -45.0",
                "-1.0, 45.0",
                "0.51, -45.0",
                "-0.51, 45.0"
        })
        void shouldCorrectSteering_whenLaneDeviationExceedsThreshold(double laneOffsetMeters, double expectedSteeringAngle) {
            LaneSensor deviatingLaneSensor = () -> laneOffsetMeters;
            AutoDriver driver = new AutoDriver(
                    operationalSpeedSensor,
                    operationalObstacleSensor,
                    deviatingLaneSensor,
                    TARGET_SPEED_KMH
            );

            AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

            assertEquals(expectedSteeringAngle, result.steeringAngleDegrees(),
                    "Steering angle should adjust in the opposite direction of lane deviation");
        }
    }

    @Nested
    @DisplayName("Cruise and Braking Dynamics Tests")
    class CruiseAndBrakingTests {

        @Test
        @DisplayName("Should maintain speed (HOLD) with zero braking when running at target speed on clear road")
        void shouldMaintainNormalDrivingState_whenSensorsOperationalAndInLane() {
            AutoDriver.DriveResult result = defaultAutoDriver.tick(GearBox.Gear.D);

            assertAll(
                    () -> assertFalse(result.safeMode(), "Vehicle should not be in safe mode"),
                    () -> assertEquals(AdaptiveCruise.Mode.HOLD, result.cruiseMode(), "Cruise mode should be HOLD"),
                    () -> assertEquals(ZERO_BRAKE_PRESSURE, result.brakePressure(), "Brake pressure should be zero"),
                    () -> assertEquals(ZERO_STEERING_ANGLE, result.steeringAngleDegrees(), "Steering angle should be centered"),
                    () -> assertFalse(result.warningActive(), "Warning indicator should be inactive")
            );
        }

        @Test
        @DisplayName("Should accelerate with zero braking when current speed is below target speed and road is clear")
        void shouldAccelerate_whenCurrentSpeedIsBelowTargetSpeedAndRoadIsClear() {
            SpeedSensor lowerSpeedSensor = () -> LOWER_SPEED_KMH;
            AutoDriver driver = new AutoDriver(
                    lowerSpeedSensor,
                    operationalObstacleSensor,
                    operationalLaneSensor,
                    TARGET_SPEED_KMH
            );

            AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

            assertAll(
                    () -> assertEquals(AdaptiveCruise.Mode.ACCELERATE, result.cruiseMode(), "Cruise mode should be ACCELERATE"),
                    () -> assertEquals(ZERO_BRAKE_PRESSURE, result.brakePressure(), "Braking should not be applied when accelerating"),
                    () -> assertFalse(result.warningActive(), "Warning should not be active"),
                    () -> assertFalse(result.safeMode(), "Vehicle should not enter safe mode")
            );
        }

        @Test
        @DisplayName("Should apply proportional braking when an obstacle is detected within safe distance")
        void shouldApplyProportionalBraking_whenObstacleWithinSafeDistance() {
            ObstacleSensor closeObstacleSensor = () -> PROPORTIONAL_BRAKE_DISTANCE_METERS;
            AutoDriver driver = new AutoDriver(
                    operationalSpeedSensor,
                    closeObstacleSensor,
                    operationalLaneSensor,
                    TARGET_SPEED_KMH
            );

            AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

            assertAll(
                    () -> assertEquals(AdaptiveCruise.Mode.BRAKE, result.cruiseMode(), "Cruise mode should be BRAKE"),
                    () -> assertEquals(25.0, result.brakePressure(), 0.001,
                            "At 80 km/h and 30 m, proportional pressure is (40-30)/40*100 = 25"),
                    () -> assertFalse(result.warningActive(), "Normal braking should not activate emergency warning"),
                    () -> assertFalse(result.safeMode(), "Normal braking should not trigger safe mode")
            );
        }

        @Test
        @DisplayName("Should trigger emergency braking with 100% pressure and activate warning when obstacle is critically close")
        void shouldTriggerEmergencyBrakingAndWarning_whenObstacleWithinEmergencyDistance() {
            ObstacleSensor criticalObstacleSensor = () -> EMERGENCY_DISTANCE_METERS;
            AutoDriver driver = new AutoDriver(
                    operationalSpeedSensor,
                    criticalObstacleSensor,
                    operationalLaneSensor,
                    TARGET_SPEED_KMH
            );

            AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

            assertAll(
                    () -> assertEquals(AdaptiveCruise.Mode.EMERGENCY_BRAKE, result.cruiseMode(), "Cruise mode should be EMERGENCY_BRAKE"),
                    () -> assertEquals(MAX_BRAKE_PRESSURE, result.brakePressure(), "Emergency brake pressure must be 100%"),
                    () -> assertTrue(result.warningActive(), "Emergency braking must activate warning indicator"),
                    () -> assertFalse(result.safeMode(), "Emergency braking is an intentional control action, not a safe mode failure")
            );
        }

        @Test
        @DisplayName("Should transition from emergency braking to safe road while retaining current warning state")
        void shouldTransitionAcrossTicksFromEmergencyBrakeToSafeRoad() {
            final double[] distance = {10.0};
            ObstacleSensor changingObstacle = () -> distance[0];
            AutoDriver driver = new AutoDriver(operationalSpeedSensor, changingObstacle,
                    operationalLaneSensor, TARGET_SPEED_KMH);

            AutoDriver.DriveResult emergency = driver.tick(GearBox.Gear.D);
            distance[0] = CLEAR_ROAD_DISTANCE_METERS;
            AutoDriver.DriveResult clear = driver.tick(GearBox.Gear.D);

            assertAll(
                    () -> assertEquals(AdaptiveCruise.Mode.EMERGENCY_BRAKE, emergency.cruiseMode()),
                    () -> assertEquals(MAX_BRAKE_PRESSURE, emergency.brakePressure()),
                    () -> assertTrue(emergency.warningActive()),
                    () -> assertEquals(AdaptiveCruise.Mode.HOLD, clear.cruiseMode()),
                    () -> assertEquals(ZERO_BRAKE_PRESSURE, clear.brakePressure()),
                    () -> assertTrue(clear.warningActive(),
                            "Warning remains active because BrakeController.apply does not reset it")
            );
        }

        @Test
        @DisplayName("Invalid sensor values propagate controller validation instead of becoming SensorFailureException")
        void shouldPropagateIllegalArgumentException_forInvalidSensorValue() {
            SpeedSensor invalidSpeed = () -> Double.NaN;
            AutoDriver driver = new AutoDriver(invalidSpeed, operationalObstacleSensor,
                    operationalLaneSensor, TARGET_SPEED_KMH);

            assertThrows(IllegalArgumentException.class, () -> driver.tick(GearBox.Gear.D));
        }
    }

    @Nested
    @DisplayName("Gear Constraints Tests")
    class GearConstraintsTests {

        @Test
        @DisplayName("Should throw IllegalStateException when braking is required while gearbox is in PARK (P)")
        void shouldThrowIllegalStateException_whenBrakingAttemptedWhileInParkGear() {
            ObstacleSensor closeObstacleSensor = () -> PROPORTIONAL_BRAKE_DISTANCE_METERS;
            AutoDriver driver = new AutoDriver(
                    operationalSpeedSensor,
                    closeObstacleSensor,
                    operationalLaneSensor,
                    TARGET_SPEED_KMH
            );

            assertThrows(IllegalStateException.class, () -> driver.tick(GearBox.Gear.P),
                    "Braking while in PARK must throw IllegalStateException");
        }

        @Test
        @DisplayName("Should allow tick without exception in PARK (P) gear when road is clear and no braking is requested")
        void shouldAllowTickWithoutException_whenInParkGearAndNoBrakingRequired() {
            AutoDriver.DriveResult result = defaultAutoDriver.tick(GearBox.Gear.P);

            assertAll(
                    () -> assertEquals(AdaptiveCruise.Mode.HOLD, result.cruiseMode()),
                    () -> assertEquals(ZERO_BRAKE_PRESSURE, result.brakePressure()),
                    () -> assertFalse(result.safeMode())
            );
        }
    }

    @Nested
    @DisplayName("Sensor Failure and Safe Mode Tests")
    class SafeModeTests {

        @ParameterizedTest(name = "Safe mode should activate when {0}")
        @MethodSource("sensorFailureProvider")
        void shouldEnterSafeMode_whenAnySensorFails(String failureDescription,
                                                    SpeedSensor speedSensor,
                                                    ObstacleSensor obstacleSensor,
                                                    LaneSensor laneSensor) {
            AutoDriver driver = new AutoDriver(speedSensor, obstacleSensor, laneSensor, TARGET_SPEED_KMH);

            AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

            assertAll(
                    () -> assertTrue(result.safeMode(), "Vehicle must enter safe mode on " + failureDescription),
                    () -> assertEquals(SAFE_MODE_BRAKE_PRESSURE, result.brakePressure(), "Safe mode must apply gentle 30% brake pressure"),
                    () -> assertEquals(ZERO_STEERING_ANGLE, result.steeringAngleDegrees(), "Steering must be centered in safe mode"),
                    () -> assertEquals(AdaptiveCruise.Mode.BRAKE, result.cruiseMode(), "Cruise mode must switch to BRAKE in safe mode"),
                    () -> assertFalse(result.warningActive(), "Warning indicator should be inactive in safe mode")
            );
        }

        private static Stream<Arguments> sensorFailureProvider() {
            SpeedSensor operationalSpeed = () -> NORMAL_SPEED_KMH;
            ObstacleSensor operationalObstacle = () -> CLEAR_ROAD_DISTANCE_METERS;
            LaneSensor operationalLane = () -> CENTER_LANE_OFFSET_METERS;

            SpeedSensor failingSpeed = () -> {
                throw new SensorFailureException("Speed sensor hardware failure");
            };
            ObstacleSensor failingObstacle = () -> {
                throw new SensorFailureException("Obstacle sensor hardware failure");
            };
            LaneSensor failingLane = () -> {
                throw new SensorFailureException("Lane sensor hardware failure");
            };

            return Stream.of(
                    Arguments.of("speed sensor fails", failingSpeed, operationalObstacle, operationalLane),
                    Arguments.of("obstacle sensor fails", operationalSpeed, failingObstacle, operationalLane),
                    Arguments.of("lane sensor fails", operationalSpeed, operationalObstacle, failingLane)
            );
        }

        @Test
        @DisplayName("Multiple simultaneous sensor failures still complete the tick in safe mode")
        void shouldEnterSafeMode_whenMoreThanOneSensorFails() {
            SpeedSensor failingSpeed = () -> { throw new SensorFailureException("speed"); };
            ObstacleSensor failingObstacle = () -> { throw new SensorFailureException("obstacle"); };
            AutoDriver driver = new AutoDriver(failingSpeed, failingObstacle,
                    operationalLaneSensor, TARGET_SPEED_KMH);

            AutoDriver.DriveResult result = assertDoesNotThrow(() -> driver.tick(GearBox.Gear.D));
            assertAll(
                    () -> assertTrue(result.safeMode()),
                    () -> assertEquals(SAFE_MODE_BRAKE_PRESSURE, result.brakePressure()),
                    () -> assertEquals(AdaptiveCruise.Mode.BRAKE, result.cruiseMode()),
                    () -> assertEquals(ZERO_STEERING_ANGLE, result.steeringAngleDegrees())
            );
        }

        @Test
        @DisplayName("Sensor failure in PARK enters safe mode and returns safe-mode braking result")
        void shouldEnterSafeMode_whenSensorFailsInParkGear() {
            SpeedSensor failingSpeed = () -> { throw new SensorFailureException("speed"); };
            AutoDriver driver = new AutoDriver(failingSpeed, operationalObstacleSensor,
                    operationalLaneSensor, TARGET_SPEED_KMH);

            AutoDriver.DriveResult result = assertDoesNotThrow(() -> driver.tick(GearBox.Gear.P));
            assertAll(
                    () -> assertTrue(result.safeMode()),
                    () -> assertEquals(SAFE_MODE_BRAKE_PRESSURE, result.brakePressure()),
                    () -> assertEquals(AdaptiveCruise.Mode.BRAKE, result.cruiseMode()),
                    () -> assertFalse(result.warningActive())
            );
        }

        @Test
        @DisplayName("Sensor failure in PARK must enter Safe Mode without PARK braking exception")
        void shouldEnterSafeModeWithoutIllegalStateException_whenSensorsFailInParkGear() {
            ObstacleSensor failingObstacle = () -> {
                throw new SensorFailureException("obstacle sensor failure");
            };
            AutoDriver driver = new AutoDriver(operationalSpeedSensor, failingObstacle,
                    operationalLaneSensor, TARGET_SPEED_KMH);

            AutoDriver.DriveResult result = assertDoesNotThrow(() -> driver.tick(GearBox.Gear.P));

            assertAll(
                    () -> assertTrue(result.safeMode()),
                    () -> assertEquals(SAFE_MODE_BRAKE_PRESSURE, result.brakePressure()),
                    () -> assertEquals(AdaptiveCruise.Mode.BRAKE, result.cruiseMode()),
                    () -> assertFalse(result.warningActive())
            );
        }
    }
}
