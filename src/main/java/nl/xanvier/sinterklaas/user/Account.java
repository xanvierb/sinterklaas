package nl.xanvier.sinterklaas.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "app_user")
@Getter
class Account {
    @Id
    private UUID id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String bio;
    private UUID avatar;
    @Enumerated(EnumType.STRING)
    private Role role;
    private boolean enabled;
    private boolean childProfile;
    private long authRevision;
    @Enumerated(EnumType.STRING)
    private ColorMode colorMode;
    @Enumerated(EnumType.STRING)
    private Palette palette;
    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private ChocolateFlavor chocolateFlavor;
    private Instant createdAt;
    private Instant updatedAt;

    protected Account() {}

    Account(String username, String passwordHash, String displayName, String bio, Role role, Instant now) {
        id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.bio = bio;
        this.role = role;
        enabled = true;
        colorMode = ColorMode.DARK;
        palette = Palette.SINTERKLAAS;
        createdAt = now;
        updatedAt = now;
    }

    void markAsChild() {
        childProfile = true;
    }

    void edit(String username, String name, String bio, Role role, boolean enabled, UUID avatar, Instant now) {
        if (!this.username.equals(username) || this.role != role || this.enabled != enabled) {
            authRevision++;
        }
        this.username = username;
        this.displayName = name;
        this.bio = bio;
        this.role = role;
        this.enabled = enabled;
        this.avatar = avatar;
        updatedAt = now;
    }

    void profile(String name, String bio, ColorMode mode, Palette palette, ChocolateFlavor chocolateFlavor, UUID avatar, Instant now) {
        displayName = name;
        this.bio = bio;
        colorMode = mode;
        this.palette = palette;
        this.chocolateFlavor = chocolateFlavor;
        this.avatar = avatar;
        updatedAt = now;
    }

    void password(String hash, Instant now) {
        passwordHash = hash;
        authRevision++;
        updatedAt = now;
    }
}
