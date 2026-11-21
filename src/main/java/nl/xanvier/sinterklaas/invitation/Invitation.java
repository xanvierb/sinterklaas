package nl.xanvier.sinterklaas.invitation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import nl.xanvier.sinterklaas.user.ChocolateFlavor;

@Entity
@Getter
class Invitation {
    @Id
    private UUID id;
    private String tokenHash;
    private UUID createdBy;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant redeemedAt;
    private Instant cancelledAt;
    private String displayName;
    private UUID avatar;
    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private ChocolateFlavor chocolateFlavor;

    protected Invitation() {}

    Invitation(UUID id, String hash, UUID creator, Instant now, Instant expires, String displayName, UUID avatar, ChocolateFlavor chocolateFlavor) {
        this.id = id;
        tokenHash = hash;
        createdBy = creator;
        createdAt = now;
        expiresAt = expires;
        this.displayName = displayName;
        this.avatar = avatar;
        this.chocolateFlavor = chocolateFlavor;
    }

    void replaceTokenHash(String hash) {
        tokenHash = hash;
    }

    boolean usable(Instant now) {
        return redeemedAt == null && cancelledAt == null && expiresAt.isAfter(now);
    }

    void redeem(Instant now) {
        redeemedAt = now;
        avatar = null;
    }

    void cancel(Instant now) {
        cancelledAt = now;
        avatar = null;
    }
}
