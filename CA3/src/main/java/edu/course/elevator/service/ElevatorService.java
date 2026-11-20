package edu.course.elevator.service;

import edu.course.elevator.events.ElevatorEventPublisher;
import edu.course.elevator.hardware.DoorOpener;
import edu.course.elevator.hardware.FloorSensor;
import edu.course.elevator.hardware.Motor;
import edu.course.elevator.model.Elevator;
import edu.course.elevator.model.ElevatorState;

public class ElevatorService {
    private final Elevator elevator;
    private final Motor motor;
    private final DoorOpener door;
    private final FloorSensor sensor;
    private final ElevatorEventPublisher events;

    public ElevatorService(Elevator elevator, Motor motor, DoorOpener door,
                           FloorSensor sensor, ElevatorEventPublisher events) {
        this.elevator = elevator;
        this.motor = motor;
        this.door = door;
        this.sensor = sensor;
        this.events = events;
    }

    public void moveTo(int destination) {
        if (door instanceof edu.course.elevator.hardware.DoorStatusReader status && status.isOpen())
            throw new IllegalStateException("Close the door before moving");
        if (destination < elevator.getMinFloor() || destination > elevator.getMaxFloor())
            throw new IllegalArgumentException("Floor out of range");
        elevator.setState(ElevatorState.MOVING);
        motor.moveTo(destination);
        elevator.moveTo(destination);
        events.publish("ARRIVED:" + destination);
        elevator.setState(ElevatorState.IDLE);
    }

    public void openDoor() {
        door.open();
        elevator.setState(ElevatorState.DOOR_OPEN);
        events.publish("DOOR_OPENED");
    }

    public void closeDoor() {
        door.close();
        elevator.setState(ElevatorState.IDLE);
        events.publish("DOOR_CLOSED");
    }

    public int getCurrentFloor() {
        return sensor.readCurrentFloor();
    }

    public Elevator getElevator() {
        return elevator;
    }
}
