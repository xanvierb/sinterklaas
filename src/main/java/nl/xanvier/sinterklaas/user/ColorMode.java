package nl.xanvier.sinterklaas.user;

public enum ColorMode {
    SYSTEM("Volg apparaat"), LIGHT("Licht"), DARK("Donker");

    private final String label;

    ColorMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
