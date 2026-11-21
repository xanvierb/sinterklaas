package nl.xanvier.sinterklaas.invitation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class InvitationTokens {
    private final Path keyPath;
    private SecretKeySpec signingKey;

    InvitationTokens(@Value("${sinterklaas.upload-dir}") Path storage,
                     @Value("${sinterklaas.invitation-key-file:}") String configuredPath) {
        keyPath = configuredPath.isBlank() ? storage.resolve(".invitation-key") : Path.of(configuredPath);
    }

    String forInvitation(UUID id) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key());
            byte[] token = mac.doFinal(("sinterklaas/invite/v1/" + id).getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Unable to generate an invitation token", exception);
        }
    }

    private synchronized SecretKeySpec key() throws IOException {
        if (signingKey == null) {
            Path path = keyPath.toAbsolutePath();
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                byte[] bytes = new byte[32];
                new SecureRandom().nextBytes(bytes);
                Path temp = Files.createTempFile(path.getParent(), ".invite-key-", ".tmp");
                try {
                    Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING);
                    try {
                        Files.move(temp, path);
                    } catch (java.nio.file.FileAlreadyExistsException ignored) {
                        // Another instance created the shared key.
                    }
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length != 32) throw new IOException("Invalid invitation signing key file");
            signingKey = new SecretKeySpec(bytes, "HmacSHA256");
        }
        return signingKey;
    }
}
