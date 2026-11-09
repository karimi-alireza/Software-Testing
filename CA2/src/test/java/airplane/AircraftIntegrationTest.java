package airplane;

import airplane.components.*;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
