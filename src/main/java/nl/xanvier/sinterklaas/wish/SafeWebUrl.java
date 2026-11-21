package nl.xanvier.sinterklaas.wish;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.annotation.*;
import java.net.URI;
import java.util.Locale;

@Documented
@Constraint(validatedBy = SafeWebUrl.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeWebUrl {
    String message() default "Gebruik een geldige HTTP- of HTTPS-productlink.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<SafeWebUrl, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true;
            }
            try {
                URI uri = URI.create(value.strip());
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                return (scheme.equals("https") || scheme.equals("http"))
                        && uri.getHost() != null && !uri.getHost().isBlank()
                        && uri.getUserInfo() == null && uri.getFragment() == null;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }
}
