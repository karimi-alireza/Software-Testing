package airplane;

import airplane.components.*;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Complements mocked interaction tests with observable effects on real components. */
class AircraftIntegrationTest {
    private Engine engine;
    private FuelSystem fuel;
    private LandingGear gear;
    private NavigationSystem navigation;
    private Aircraft aircraft;

    @BeforeEach
    void createFreshAircraft() {
        engine = new Engine();
        fuel = new FuelSystem(100.0, 20.0);
        gear = new LandingGear();
        navigation = new NavigationSystem("Shiraz");
        aircraft = new Aircraft(engine, fuel, gear, navigation);
    }

    @Test
    void completeFlightUsesRealComponents() {
        aircraft.prepareForTakeoff();
        assertEquals(FlightStatus.READY, aircraft.getStatus());
        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        assertTrue(engine.isRunning());
        assertFalse(gear.isDeployed());

        aircraft.cruise(25.0);
        assertEquals(75.0, fuel.getRemainingFuel());
        aircraft.land();

        assertLandedSafely();
    }

    @Test
    void realEngineFailureDuringFlightCausesEmergencyLanding() {
        startFlight();
        engine.fail();

        aircraft.cruise(5.0);

        assertLandedSafely();
        assertEquals(100.0, fuel.getRemainingFuel());
    }

    @Test
    void realFuelExhaustionCausesEmergencyLandingAndReportsFailure() {
        startFlight();

        FlightException thrown = assertThrows(FlightException.class, () -> aircraft.cruise(101.0));

        assertInstanceOf(IllegalStateException.class, thrown.getCause());
        assertLandedSafely();
        assertEquals(100.0, fuel.getRemainingFuel());
    }

    @Test
    void unavailableRouteCanBeRestoredBeforeRetryingPreparation() {
        navigation.setRouteAvailable(false);
        assertThrows(FlightException.class, aircraft::prepareForTakeoff);
        navigation.setRouteAvailable(true);

        aircraft.prepareForTakeoff();

        assertEquals(FlightStatus.READY, aircraft.getStatus());
    }

    @Test
    void consecutiveCruiseCallsDeductCumulativeFuel() {
        startFlight();

        aircraft.cruise(25.0);
        aircraft.cruise(15.0);

        assertEquals(60.0, fuel.getRemainingFuel());
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        assertTrue(engine.isRunning());
        assertFalse(gear.isDeployed());
    }

    @Test
    void exactFuelExhaustionAllowsConsumptionButNextCruiseTriggersEmergencyLanding() {
        startFlight();
        aircraft.cruise(100.0);
        assertEquals(0.0, fuel.getRemainingFuel());
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        FlightException thrown = assertThrows(FlightException.class, () -> aircraft.cruise(1.0));

        assertInstanceOf(IllegalStateException.class, thrown.getCause());
        assertLandedSafely();
        assertEquals(0.0, fuel.getRemainingFuel());
    }

    @Test
    void consumingBelowTakeoffMinimumPreventsNextFlightAfterLanding() {
        startFlight();
        aircraft.cruise(81.0);
        aircraft.land();

        assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertLandedSafely();
        assertEquals(19.0, fuel.getRemainingFuel());
    }

    @Test
    void fuelStatePersistsAcrossTwoRealFlights() {
        startFlight();
        aircraft.cruise(25.0);
        aircraft.land();

        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        aircraft.cruise(15.0);
        aircraft.land();

        assertLandedSafely();
        assertEquals(60.0, fuel.getRemainingFuel());
    }

    @Test
    void engineFailureBetweenPreparationAndTakeoffAbortsBeforeGearRetraction() {
        aircraft.prepareForTakeoff();
        engine.fail();

        FlightException thrown = assertThrows(FlightException.class, aircraft::takeOff);

        assertInstanceOf(IllegalStateException.class, thrown.getCause());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        assertFalse(engine.isRunning());
        assertTrue(gear.isDeployed());
        assertEquals(100.0, fuel.getRemainingFuel());
    }

    @Test
    void emergencyLandingAfterRealEngineFailureDoesNotMakeEngineReusable() {
        startFlight();
        engine.fail();
        aircraft.cruise(5.0);

        assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertLandedSafely();
        assertFalse(engine.isOperational());
    }

    @ParameterizedTest(name = "reject non-finite cruise {0} without changing real components")
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void nonFiniteCruisePreservesRealFlightState(double amount) {
        startFlight();

        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(amount));

        assertEquals(100.0, fuel.getRemainingFuel());
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        assertTrue(engine.isRunning());
        assertFalse(gear.isDeployed());
    }

    @Test
    void partialGearRetractionFailureRestoresGearAndStopsRealEngine() {
        RuntimeException failure = new IllegalStateException("retraction failed after moving gear");
        LandingGear failingGear = new LandingGear() {
            @Override
            public void retract() {
                super.retract();
                throw failure;
            }
        };
        Aircraft recovering = new Aircraft(engine, fuel, failingGear, navigation);
        recovering.prepareForTakeoff();

        FlightException thrown = assertThrows(FlightException.class, recovering::takeOff);

        assertSame(failure, thrown.getCause());
        assertEquals(FlightStatus.ABORTED, recovering.getStatus());
        assertFalse(engine.isRunning());
        assertTrue(failingGear.isDeployed());
        assertEquals(100.0, fuel.getRemainingFuel());
    }

    @Test
    void partialEngineStartFailureStillStopsRealEngine() {
        Engine failingEngine = new Engine() {
            @Override
            public void start() {
                super.start();
                throw new IllegalStateException("startup failed after engine began running");
            }
        };
        Aircraft recovering = new Aircraft(failingEngine, fuel, gear, navigation);
        recovering.prepareForTakeoff();

        assertThrows(FlightException.class, recovering::takeOff);

        assertEquals(FlightStatus.ABORTED, recovering.getStatus());
        assertFalse(failingEngine.isRunning());
        assertTrue(gear.isDeployed());
    }

    @Test
    void failedGearDeploymentStillStopsRealEngineAndCanBeRetried() {
        LandingGear failingOnce = new LandingGear() {
            private boolean firstDeployment = true;
            @Override
            public void deploy() {
                if (firstDeployment) {
                    firstDeployment = false;
                    throw new IllegalStateException("temporary deployment failure");
                }
                super.deploy();
            }
        };
        Aircraft recovering = new Aircraft(engine, fuel, failingOnce, navigation);
        recovering.prepareForTakeoff();
        assertDoesNotThrow(recovering::takeOff, "fixture must reach flight before deployment can fail");

        assertThrows(IllegalStateException.class, recovering::land);

        assertFalse(engine.isRunning());
        assertFalse(failingOnce.isDeployed());
        assertEquals(FlightStatus.IN_FLIGHT, recovering.getStatus());
        recovering.emergencyLand();
        assertTrue(failingOnce.isDeployed());
        assertFalse(engine.isRunning());
        assertEquals(FlightStatus.LANDED, recovering.getStatus());
    }

    @Test
    void engineQueryFailureStopsRealEngineAndPreservesFuel() {
        Engine failingQuery = new Engine() {
            @Override
            public boolean isOperational() {
                if (isRunning()) {
                    throw new IllegalStateException("engine sensor failed during flight");
                }
                return super.isOperational();
            }
        };
        Aircraft recovering = new Aircraft(failingQuery, fuel, gear, navigation);
        recovering.prepareForTakeoff();
        recovering.takeOff();

        FlightException thrown = assertThrows(FlightException.class, () -> recovering.cruise(5.0));

        assertInstanceOf(IllegalStateException.class, thrown.getCause());
        assertFalse(failingQuery.isRunning());
        assertTrue(gear.isDeployed());
        assertEquals(100.0, fuel.getRemainingFuel());
        assertEquals(FlightStatus.LANDED, recovering.getStatus());
    }

    private void startFlight() {
        aircraft.prepareForTakeoff();
        aircraft.takeOff();
    }

    private void assertLandedSafely() {
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        assertTrue(gear.isDeployed());
        assertFalse(engine.isRunning());
    }
}
