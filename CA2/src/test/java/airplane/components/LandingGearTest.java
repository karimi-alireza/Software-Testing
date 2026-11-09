package airplane.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LandingGearTest {
    @Test
    void newGearIsDeployed() {
        assertTrue(new LandingGear().isDeployed());
    }

    @Test
    void retractingGearMakesItUndeployed() {
        LandingGear gear = new LandingGear();
        gear.retract();
        assertFalse(gear.isDeployed());
    }

    @Test
    void deployingRetractedGearMakesItDeployed() {
        LandingGear gear = new LandingGear();
        gear.retract();
        gear.deploy();
        assertTrue(gear.isDeployed());
    }
}
