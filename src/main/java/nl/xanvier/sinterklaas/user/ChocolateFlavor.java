package nl.xanvier.sinterklaas.user;

public enum ChocolateFlavor {
    PUUR("Puur"), MELK("Melk"), HAZELNOOT("Hazelnoot"), WIT("Wit");

    private final String label;

    ChocolateFlavor(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
