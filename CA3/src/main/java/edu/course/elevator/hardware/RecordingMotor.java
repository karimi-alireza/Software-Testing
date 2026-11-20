package edu.course.elevator.hardware;

public class RecordingMotor implements Motor {
    private int lastDestination = -1;

    @Override
    public void moveTo(int floor) {
        lastDestination = floor;
    }

    public int getLastDestination() {
        return lastDestination;
    }
}
