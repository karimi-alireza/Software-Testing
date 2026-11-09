package airplane.components;

public class LandingGear {
    private boolean deployed = true;

    public void deploy() {
        deployed = true;
    }

    public void retract() {
        deployed = false;
    }

    public boolean isDeployed() {
        return deployed;
    }
}
