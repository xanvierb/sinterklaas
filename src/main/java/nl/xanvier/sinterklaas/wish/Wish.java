package nl.xanvier.sinterklaas.wish;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "wish", indexes = @Index(name = "wish_owner_updated_idx", columnList = "owner_id, updated_at"))
@Getter
class Wish {
    @Id
    private UUID id;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(nullable = false, length = 1000)
    private String notes;
    @Column(name = "product_url", length = 2048)
    private String productUrl;
    @Column(name = "estimated_price", precision = 10, scale = 2)
    private BigDecimal estimatedPrice;
    private boolean inStore;
    @Column(length = 120)
    private String shopName;
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;
    @Column(nullable = false, length = 500)
    private String variants;
    @Column(name = "image_id")
    private UUID imageId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wish() {}

    Wish(UUID ownerId, WishForms.Input input, UUID imageId, Instant now) {
        id = UUID.randomUUID();
        this.ownerId = ownerId;
        currency = "EUR";
        notes = "";
        variants = "";
        createdAt = now;
        update(input, imageId, now);
    }

    void update(WishForms.Input input, UUID imageId, Instant now) {
        title = input.title().strip();
        description = clean(input.description());
        priority = Priority.NORMAL;
        productUrl = input.productUrl() == null || input.productUrl().isBlank()
                ? null : input.productUrl().strip();
        estimatedPrice = input.estimatedPrice();
        inStore = input.inStore();
        shopName = inStore && !clean(input.shopName()).isEmpty() ? clean(input.shopName()) : null;
        if (imageId != null) {
            this.imageId = imageId;
        }
        updatedAt = now;
    }

    void updateNotes(String value) {
        notes = clean(value);
    }

    void removeImage() {
        imageId = null;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
