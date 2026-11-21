package nl.xanvier.sinterklaas.user;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public LoginUser require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof LoginUser user) {
            return user;
        }
        throw new AccessDeniedException("Aanmelden is vereist.");
    }

    public LoginUser requireAdmin() {
        LoginUser user = require();
        if (!user.admin()) {
            throw new AccessDeniedException("Beheerrechten zijn vereist.");
        }
        return user;
    }
}
