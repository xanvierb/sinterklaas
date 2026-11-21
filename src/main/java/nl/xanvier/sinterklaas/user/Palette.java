package nl.xanvier.sinterklaas.user;

public enum Palette {
    SINTERKLAAS("Sinterklaas"), NEUTRAAL("Neutraal");

    private final String label;

    Palette(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
