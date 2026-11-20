package edu.course.elevator;

import edu.course.elevator.config.ElevatorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FactoryTest {
    @Test
    void factoryBuildsAWorkingElevatorService() throws Exception {
        var service = new ElevatorFactory().createService();
        service.moveTo(6);
        assertEquals(6, service.getElevator().getCurrentFloor());
    }
}
