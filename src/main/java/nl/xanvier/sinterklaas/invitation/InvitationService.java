package nl.xanvier.sinterklaas.invitation;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import nl.xanvier.sinterklaas.shared.Problem;
import nl.xanvier.sinterklaas.shared.Images;
import nl.xanvier.sinterklaas.user.AccountForms.InvitationAcceptance;
import nl.xanvier.sinterklaas.user.ChocolateFlavor;
import org.springframework.web.multipart.MultipartFile;
import nl.xanvier.sinterklaas.user.AccountForms.NewAccount;
import nl.xanvier.sinterklaas.user.AccountService;
import nl.xanvier.sinterklaas.user.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InvitationService {

    record Entry(UUID id, ZonedDateTime expiresAt, String status, boolean cancellable, String displayName, boolean copyable) {}

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private final InvitationRepository invitations;
    private final AccountService accounts;
    private final CurrentUser current;
    private final Images images;
    private final InvitationTokens tokens;
    private final Clock clock;
    private final Duration lifetime;
    private final String baseUrl;

    InvitationService(InvitationRepository invitations, AccountService accounts, CurrentUser current, Clock clock, Images images, InvitationTokens tokens,
                      @Value("${sinterklaas.invite-lifetime}") Duration lifetime,
                      @Value("${sinterklaas.base-url}") URI baseUrl) {
        this.invitations = invitations;
        this.accounts = accounts;
        this.current = current;
        this.images = images;
        this.tokens = tokens;
        this.clock = clock;
        if (lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("SINT_INVITE_LIFETIME must be positive and at most seven days");
        }
        if (!Set.of("https", "http").contains(baseUrl.getScheme()) || baseUrl.getHost() == null
                || baseUrl.getUserInfo() != null || baseUrl.getFragment() != null || baseUrl.getQuery() != null
                || !Set.of("", "/").contains(baseUrl.getPath())) {
            throw new IllegalArgumentException("SINT_BASE_URL must be the application origin");
        }
        this.lifetime = lifetime;
        this.baseUrl = baseUrl.resolve("/").toString();
    }

    @Transactional
    public String issue(String displayName, ChocolateFlavor flavor, MultipartFile photo) {
        UUID admin = current.requireAdmin().id();
        UUID id = UUID.randomUUID();
        String token = tokens.forInvitation(id);
        Instant now = clock.instant();
        UUID avatar = images.replace(null, photo);
        invitations.save(new Invitation(id, hash(token), admin, now, now.plus(lifetime),
                displayName.strip(), avatar, flavor));
        return baseUrl + "uitnodiging#" + token;
    }

    @Transactional(readOnly = true)
    public List<Entry> entries() {
        current.requireAdmin();
        Instant now = clock.instant();
        return invitations.findAllByOrderByCreatedAtDesc().stream().map(invitation -> {
            String status = invitation.getRedeemedAt() != null ? "Gebruikt" : invitation.getCancelledAt() != null
                    ? "Ingetrokken" : invitation.usable(now) ? "Actief" : "Verlopen";
            return new Entry(invitation.getId(), invitation.getExpiresAt().atZone(AMSTERDAM), status,
                    invitation.usable(now), invitation.getDisplayName(),
                    invitation.usable(now) && hasCopyableToken(invitation));
        }).toList();
    }

    @Transactional
    public String link(UUID id, boolean replaceOldLink) {
        current.requireAdmin();
        Invitation invitation = invitations.findLockedById(id)
                .filter(entry -> entry.usable(clock.instant())).orElseThrow(this::invalidInvitation);
        String token = tokens.forInvitation(id);
        String tokenHash = hash(token);
        if (!tokenHash.equals(invitation.getTokenHash())) {
            if (!replaceOldLink) throw new Problem("Vervang eerst de oude uitnodigingslink.");
            invitation.replaceTokenHash(tokenHash);
        }
        return baseUrl + "uitnodiging#" + token;
    }

    private boolean hasCopyableToken(Invitation invitation) {
        return hash(tokens.forInvitation(invitation.getId())).equals(invitation.getTokenHash());
    }

    @Transactional
    public void cancel(UUID id) {
        current.requireAdmin();
        invitations.findLockedById(id).filter(invitation -> invitation.usable(clock.instant()))
                .ifPresent(invitation -> {
                    images.remove(invitation.getAvatar());
                    invitation.cancel(clock.instant());
                });
    }

    @Transactional
    public void redeem(String submittedToken, InvitationAcceptance form) {
        String token = submittedToken.strip();
        token = token.substring(token.lastIndexOf('#') + 1);
        if (!token.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidInvitation();
        }
        Invitation invitation = invitations.findByTokenHash(hash(token)).orElseThrow(this::invalidInvitation);
        Instant now = clock.instant();
        if (!invitation.usable(now)) {
            throw invalidInvitation();
        }
        boolean prepared = invitation.getDisplayName() != null;
        String name = prepared ? invitation.getDisplayName() : form.username();
        ChocolateFlavor flavor = form.chocolateFlavor() == null ? invitation.getChocolateFlavor() : form.chocolateFlavor();
        accounts.createFromInvitation(new NewAccount(form.username(), name,
                form.password(), form.confirmation()), invitation.getAvatar(), flavor);
        invitation.redeem(now);
    }

    private Problem invalidInvitation() {
        return new Problem("Deze uitnodiging is ongeldig, verlopen of al gebruikt.");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
