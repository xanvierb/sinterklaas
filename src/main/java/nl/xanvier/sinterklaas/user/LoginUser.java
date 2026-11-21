package nl.xanvier.sinterklaas.user;

import java.util.UUID;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public final class LoginUser extends User {

    private final UUID id;
    private final long revision;

    LoginUser(Account account) {
        super(account.getUsername(), account.getPasswordHash(), account.isEnabled(), true, true, true,
                AuthorityUtils.createAuthorityList("ROLE_" + account.getRole().name()));
        id = account.getId();
        revision = account.getAuthRevision();
    }

    public UUID id() {
        return id;
    }

    public long revision() {
        return revision;
    }

    public boolean admin() {
        return getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"));
    }
}
