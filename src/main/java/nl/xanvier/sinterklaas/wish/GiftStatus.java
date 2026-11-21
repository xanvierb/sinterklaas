package nl.xanvier.sinterklaas.wish;

enum GiftStatus {
    RESERVED("Gereserveerd"),
    ORDERED("Besteld"),
    RECEIVED("Ontvangen"),
    WRAPPED("Ingepakt"),
    GIVEN("Gegeven");

    private final String label;

    GiftStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    static java.util.List<GiftStatus> stages(boolean inStore) {
        return inStore ? java.util.List.of(RESERVED, RECEIVED, WRAPPED, GIVEN) : java.util.List.of(values());
    }

    String label(boolean inStore) {
        return inStore && this == RECEIVED ? "Gekocht" : label;
    }

    GiftStatus adjacent(boolean inStore, boolean back) {
        var stages = stages(inStore);
        int index = stages.indexOf(this);
        int target = index + (back ? -1 : 1);
        return index < 0 || target < 0 || target >= stages.size() ? null : stages.get(target);
    }
}
