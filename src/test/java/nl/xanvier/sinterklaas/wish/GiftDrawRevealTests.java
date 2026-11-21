package nl.xanvier.sinterklaas.wish;

import java.util.UUID;
import nl.xanvier.sinterklaas.user.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GiftDrawRevealTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accounts;

    @Test
    void onlyAdminCanRevealAssignmentsForASelectedRound() throws Exception {
        UUID first = insertAccount("test-trekker", "Testtrekker", "USER");
        UUID second = insertAccount("test-ontvanger", "Testontvanger", "USER");
        insertAccount("test-draw-admin", "Testbeheerder", "ADMIN");
        jdbc.update("INSERT INTO gift_draw_round (name, visible) VALUES ('Testtrekking', FALSE)");
        long round = jdbc.queryForObject("SELECT id FROM gift_draw_round WHERE name = 'Testtrekking'", Long.class);
        jdbc.update("INSERT INTO gift_draw_entry (round_id, giver_id, recipient_id) VALUES (?, ?, ?)",
                round, first, second);
        var admin = user(accounts.loadUserByUsername("test-draw-admin"));

        mvc.perform(get("/beheer/lootjes").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Toon wie wie heeft")))
                .andExpect(content().string(not(containsString("Uitslag van de trekking"))));
        mvc.perform(get("/beheer/lootjes").param("reveal", Long.toString(round)).with(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("Uitslag van de trekking")))
                .andExpect(content().string(containsString(">Testtrekker</th>")))
                .andExpect(content().string(containsString(">Testontvanger</td>")));
        mvc.perform(get("/beheer/lootjes").param("reveal", Long.toString(round))
                        .with(user(accounts.loadUserByUsername("test-trekker"))))
                .andExpect(status().isForbidden());
    }

    private UUID insertAccount(String username, String displayName, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, username, password_hash, display_name, bio, role, enabled,
                                      color_mode, palette, created_at, updated_at)
                VALUES (?, ?, 'unused', ?, '', ?, TRUE, 'SYSTEM', 'SINTERKLAAS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, username, displayName, role);
        return id;
    }
}
