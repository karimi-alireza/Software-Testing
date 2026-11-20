package edu.course.elevator.hardware;

public class SimpleDoor implements DoorOpener, DoorStatusReader {
    private boolean open;

    @Override
    public void open() {
        open = true;
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }
}
