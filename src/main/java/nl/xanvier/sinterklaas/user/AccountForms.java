package nl.xanvier.sinterklaas.user;

import jakarta.validation.constraints.*;

public final class AccountForms {

    private AccountForms() {}

    private static final String USERNAME = "[a-zA-Z0-9][a-zA-Z0-9._-]{2,31}";

    public record NewAccount(@NotBlank @Pattern(regexp = USERNAME, message = "Gebruik 3 tot 32 letters, cijfers of de tekens . _ -.") String username,
                             @NotBlank @Size(max = 80) String displayName,
                             @Size(max = 500) String bio,
                             @NotBlank @Size(min = 12, max = 72) String password,
                             @NotBlank @Size(min = 12, max = 72) String confirmation) {}

    public record InvitationAcceptance(
            @NotBlank @Pattern(regexp = USERNAME, message = "Gebruik 3 tot 32 letters, cijfers of de tekens . _ -.") String username,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Size(min = 12, max = 72) String confirmation,
            ChocolateFlavor chocolateFlavor) {}

    public record EditAccount(@NotBlank @Pattern(regexp = USERNAME, message = "Gebruik 3 tot 32 letters, cijfers of de tekens . _ -.") String username,
                              @NotBlank @Size(max = 80) String displayName,
                              @Size(max = 500) String bio, @NotNull Role role, boolean enabled) {}

    public record Profile(@NotBlank @Size(max = 80) String displayName,
                          @NotNull ColorMode colorMode, ChocolateFlavor chocolateFlavor) {}

    public record Password(@NotBlank @Size(max = 72) String oldPassword,
                           @NotBlank @Size(min = 12, max = 72) String password,
                           @NotBlank @Size(min = 12, max = 72) String confirmation) {}
}
