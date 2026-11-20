package edu.course.elevator.model;

public class Elevator {
    private int currentFloor;
    private final int minFloor;
    private final int maxFloor;
    private ElevatorState state = ElevatorState.IDLE;

    public Elevator(int currentFloor, int minFloor, int maxFloor) {
        if (minFloor > maxFloor || currentFloor < minFloor || currentFloor > maxFloor)
            throw new IllegalArgumentException("Invalid floor configuration");
        this.currentFloor = currentFloor;
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public int getMinFloor() {
        return minFloor;
    }

    public int getMaxFloor() {
        return maxFloor;
    }

    public ElevatorState getState() {
        return state;
    }

    public void setState(ElevatorState state) {
        this.state = state;
    }

    public void moveTo(int floor) {
        if (floor < minFloor || floor > maxFloor) throw new IllegalArgumentException("Floor out of range");
        currentFloor = floor;
    }
}
