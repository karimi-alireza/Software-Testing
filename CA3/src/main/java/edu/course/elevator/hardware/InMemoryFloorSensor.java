package edu.course.elevator.hardware;

public class InMemoryFloorSensor implements FloorSensor {
    private int floor;

    public InMemoryFloorSensor(int initialFloor) {
        floor = initialFloor;
    }

    public void setFloor(int floor) {
        this.floor = floor;
    }

    @Override
    public int readCurrentFloor() {
        return floor;
    }
}
