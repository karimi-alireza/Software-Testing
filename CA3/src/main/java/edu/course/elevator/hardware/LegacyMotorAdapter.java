package edu.course.elevator.hardware;

public class LegacyMotorAdapter implements Motor {
    private final LegacyLiftMotor legacyMotor;

    public LegacyMotorAdapter(LegacyLiftMotor legacyMotor) {
        this.legacyMotor = legacyMotor;
    }

    @Override
    public void moveTo(int floor) {
        legacyMotor.selectDestinationAndStart(floor);
    }
}
