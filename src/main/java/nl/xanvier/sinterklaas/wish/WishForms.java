package nl.xanvier.sinterklaas.wish;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class WishForms {
    private WishForms() {}

    public record Notes(@Size(max = 1000) String text) {}

    public record Input(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 2000) String description,
            @Size(max = 2048) @SafeWebUrl String productUrl,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal estimatedPrice,
            boolean inStore,
            @Size(max = 120) String shopName
    ) {}
}
