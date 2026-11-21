package nl.xanvier.sinterklaas.wish;

import java.time.LocalDate;
import nl.xanvier.sinterklaas.shared.Problem;
import nl.xanvier.sinterklaas.user.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class CelebrationSettings {
    private final JdbcTemplate jdbc;
    private final CurrentUser current;

    CelebrationSettings(JdbcTemplate jdbc, CurrentUser current) {
        this.jdbc = jdbc;
        this.current = current;
    }

    LocalDate configuredDate() {
        return jdbc.queryForObject("SELECT celebration_date FROM celebration_settings WHERE id = 1",
                (row, number) -> row.getObject("celebration_date", LocalDate.class));
    }

    record Agreement(String heading, String text) {}

    Agreement agreement() {
        return jdbc.queryForObject("SELECT agreement_heading, budget_agreement FROM celebration_settings WHERE id = 1",
                (row, number) -> new Agreement(row.getString("agreement_heading"), row.getString("budget_agreement")));
    }

    void saveAgreement(String heading, String agreement) {
        current.requireAdmin();
        String title = heading.strip();
        String text = agreement.strip();
        if (title.length() > 80) throw new Problem("Gebruik maximaal 80 tekens voor de titel.");
        if (text.length() > 5000) throw new Problem("Gebruik maximaal 5.000 tekens voor de tekst.");
        jdbc.update("UPDATE celebration_settings SET agreement_heading = ?, budget_agreement = ? WHERE id = 1",
                title.isEmpty() ? "Afspraken" : title, text.isEmpty() ? null : text);
    }

    void saveDate(LocalDate date) {
        current.requireAdmin();
        jdbc.update("UPDATE celebration_settings SET celebration_date = ? WHERE id = 1", date);
    }
}
