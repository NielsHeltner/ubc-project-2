package dk.sdu.ubc.ubc_project_2.domain;

public class Fingerprint {

    private final String name;
    private final int signal;

    public Fingerprint(String name, int signal) {
        this.name = name;
        this.signal = signal;
    }

    public String getName() {
        return name;
    }

    public int getSignal() {
        return signal;
    }

    @Override
    public String toString() {
        return "fingerprint: " + name + ", signal strength: " + signal;
    }

}
