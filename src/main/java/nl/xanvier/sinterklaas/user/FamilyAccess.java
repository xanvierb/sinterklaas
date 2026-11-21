package nl.xanvier.sinterklaas.user;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FamilyAccess {
    // Used in recipient-side queries where w is the wish.
    public static final String RECIPIENT = """
            (? = w.owner_id OR ? IN (SELECT p.parent_id FROM child_parent p
                WHERE p.child_id = w.owner_id AND p.accepted = TRUE))
            """;

    private final JdbcTemplate jdbc;

    public FamilyAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> children(UUID parent) {
        return jdbc.queryForList("""
                SELECT p.child_id FROM child_parent p JOIN app_user u ON u.id = p.child_id
                WHERE p.parent_id = ? AND p.accepted = TRUE AND u.enabled = TRUE AND u.child_profile = TRUE
                ORDER BY u.display_name, u.id
                """, UUID.class, parent);
    }

    public boolean manages(LoginUser user, UUID owner) {
        return !user.admin() && (user.id().equals(owner) || children(user.id()).contains(owner));
    }
}
