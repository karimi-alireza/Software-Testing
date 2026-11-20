package edu.course.elevator;

import edu.course.elevator.hardware.LegacyLiftMotor;
import edu.course.elevator.hardware.LegacyMotorAdapter;
import edu.course.elevator.hardware.Motor;
import edu.course.elevator.hardware.RecordingMotor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyPanelTest {
    @Test
    void testLegacyMotorAdapterMovesToRequestedFloor() {
        LegacyLiftMotor vendorMotor = new LegacyLiftMotor();
        Motor motor = new LegacyMotorAdapter(vendorMotor);
        motor.moveTo(8);
        assertEquals(8, vendorMotor.getLastSelectedFloor());
    }

    @Test
    void testRecordingMotorMovesToRequestedFloor() {
        RecordingMotor motor = new RecordingMotor();
        motor.moveTo(3);
        assertEquals(3, motor.getLastDestination());
    }

    @Test
    void testAnotherRecordingMotorScenario() {
        RecordingMotor motor = new RecordingMotor();
        motor.moveTo(5);
        assertEquals(5, motor.getLastDestination());
    }
}
