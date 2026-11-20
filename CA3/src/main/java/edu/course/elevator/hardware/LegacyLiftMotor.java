package edu.course.elevator.hardware;

public class LegacyLiftMotor {
    private int lastSelectedFloor = 1;

    public void selectDestinationAndStart(int destination) {
        lastSelectedFloor = destination;
    }

    public int getLastSelectedFloor() {
        return lastSelectedFloor;
    }
}
