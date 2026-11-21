package nl.xanvier.sinterklaas.wish;

public enum Priority {
    LOW("Laag"), NORMAL("Normaal"), HIGH("Hoog");

    private final String label;

    Priority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
