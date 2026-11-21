package nl.xanvier.sinterklaas.user;

public enum Role {
    USER("Deelnemer"), ADMIN("Beheerder");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
