package nl.xanvier.sinterklaas.wish;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import nl.xanvier.sinterklaas.shared.Problem;
import nl.xanvier.sinterklaas.user.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GiftDraw {
    record Recipient(UUID id, String name, boolean enabled) {}
    record PersonalDraw(String name, String childName, Recipient recipient) {}
    record Round(long id, String name, boolean visible, boolean drawn) {}

    record Participant(UUID id, String name, boolean child, boolean hasAvatar) {}
    record Assignment(String giver, String recipient) {}

    private static final String ELIGIBLE_PARTICIPANT = """
            u.enabled = TRUE AND u.role = 'USER' AND (u.child_profile = FALSE OR EXISTS (
                SELECT 1 FROM child_parent p JOIN app_user parent ON parent.id = p.parent_id
                WHERE p.child_id = u.id AND p.accepted = TRUE
                  AND parent.enabled = TRUE AND parent.role = 'USER' AND parent.child_profile = FALSE
            ))
            """;

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final SecureRandom random = new SecureRandom();

    GiftDraw(JdbcTemplate jdbc, CurrentUser current) {
        this.jdbc = jdbc;
        this.current = current;
    }

    List<Round> rounds() {
        current.requireAdmin();
        return jdbc.query("""
                SELECT r.id, r.name, r.visible,
                       EXISTS (SELECT 1 FROM gift_draw_entry e WHERE e.round_id = r.id) AS drawn
                FROM gift_draw_round r ORDER BY r.visible DESC, r.id DESC
                """, (row, number) -> new Round(row.getLong("id"), row.getString("name"),
                row.getBoolean("visible"), row.getBoolean("drawn")));
    }

    void create(String name) {
        current.requireAdmin();
        String clean = name.strip();
        if (clean.isEmpty() || clean.length() > 100) throw new Problem("Gebruik een naam van 1 tot 100 tekens.");
        jdbc.update("INSERT INTO gift_draw_round (name) VALUES (?)", clean);
    }

    void setVisible(long id, boolean visible) {
        current.requireAdmin();
        if (jdbc.update("UPDATE gift_draw_round SET visible = ? WHERE id = ?", visible, id) == 0) {
            throw new Problem("Deze trekking bestaat niet.");
        }
    }

    List<Participant> participants() {
        current.requireAdmin();
        return jdbc.query("""
                SELECT u.id, u.display_name, u.child_profile, u.avatar IS NOT NULL AS has_avatar FROM app_user u
                WHERE %s ORDER BY u.display_name, u.id
                """.formatted(ELIGIBLE_PARTICIPANT),
                (row, number) -> new Participant(row.getObject("id", UUID.class),
                        row.getString("display_name"), row.getBoolean("child_profile"), row.getBoolean("has_avatar")));
    }

    List<Assignment> assignments(long id) {
        current.requireAdmin();
        return jdbc.query("""
                SELECT giver.display_name AS giver_name, recipient.display_name AS recipient_name
                FROM gift_draw_entry entry
                JOIN app_user giver ON giver.id = entry.giver_id
                JOIN app_user recipient ON recipient.id = entry.recipient_id
                WHERE entry.round_id = ?
                ORDER BY giver.display_name, giver.id
                """, (row, number) -> new Assignment(row.getString("giver_name"),
                row.getString("recipient_name")), id);
    }

    List<PersonalDraw> mine() {
        var giver = current.require();
        if (giver.admin()) throw new AccessDeniedException("Alleen deelnemers trekken een lootje.");
        return jdbc.query("""
                SELECT r.name AS round_name, u.id, u.display_name, u.enabled AND u.role = 'USER' AS available,
                       CASE WHEN child.child_profile THEN child.display_name ELSE NULL END AS child_name
                FROM gift_draw_entry a JOIN app_user u ON u.id = a.recipient_id
                JOIN gift_draw_round r ON r.id = a.round_id
                JOIN app_user child ON child.id = a.giver_id
                WHERE r.visible = TRUE AND child.enabled = TRUE AND child.role = 'USER'
                  AND (a.giver_id = ? OR (child.child_profile = TRUE AND EXISTS (
                      SELECT 1 FROM child_parent p
                      WHERE p.child_id = a.giver_id AND p.parent_id = ? AND p.accepted = TRUE
                  )))
                ORDER BY r.id DESC, child.child_profile, child.display_name, child.id
                """, (row, number) -> new PersonalDraw(row.getString("round_name"), row.getString("child_name"),
                new Recipient(row.getObject("id", UUID.class), row.getString("display_name"),
                        row.getBoolean("available"))), giver.id(), giver.id());
    }

    @Transactional
    public void draw(long id, List<UUID> selected) {
        current.requireAdmin();
        var visibility = jdbc.queryForList("SELECT visible FROM gift_draw_round WHERE id = ? FOR UPDATE",
                Boolean.class, id);
        if (visibility.isEmpty() || !visibility.getFirst()) throw new Problem("Deze trekking is niet zichtbaar.");
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM gift_draw_entry WHERE round_id = ?)", Boolean.class, id))) {
            throw new Problem("De lootjes voor deze trekking zijn al getrokken.");
        }
        Set<UUID> selection = selected == null ? Set.of() : Set.copyOf(selected);
        if (selection.size() < 2) throw new Problem("Selecteer minstens twee deelnemers.");
        List<UUID> givers = participants().stream().map(Participant::id).filter(selection::contains).toList();
        if (givers.size() != selection.size()) {
            throw new Problem("Een geselecteerde deelnemer is niet meer beschikbaar. Kies opnieuw.");
        }

        List<UUID> recipients = new ArrayList<>(givers);
        boolean match;
        do {
            Collections.shuffle(recipients, random);
            match = false;
            for (int i = 0; i < givers.size(); i++) {
                if (givers.get(i).equals(recipients.get(i))) {
                    match = true;
                    break;
                }
            }
        } while (match);
        jdbc.batchUpdate("INSERT INTO gift_draw_entry (round_id, giver_id, recipient_id) VALUES (?, ?, ?)",
                java.util.stream.IntStream.range(0, givers.size())
                        .mapToObj(i -> new Object[]{id, givers.get(i), recipients.get(i)})
                        .toList());
    }
}
