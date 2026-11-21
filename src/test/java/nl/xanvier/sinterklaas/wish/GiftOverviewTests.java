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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GiftOverviewTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accounts;

    @Test
    void buyerCanTrackAndCancelFromTheSameOverview() throws Exception {
        UUID recipient = insertParticipant("overview-recipient");
        UUID buyerId = insertParticipant("overview-buyer");
        UUID wish = insertWish(recipient);
        jdbc.update("INSERT INTO gift_reservation (wish_id, buyer_id) VALUES (?, ?)", wish, buyerId);
        var buyer = user(accounts.loadUserByUsername("overview-buyer"));
        var owner = user(accounts.loadUserByUsername("overview-recipient"));
        String cancelUrl = "/reserveringen/" + wish + "/annuleren";

        mvc.perform(get("/reserveringen").with(buyer)).andExpect(redirectedUrl("/cadeaus"));
        mvc.perform(get("/cadeaus").with(buyer))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cadeauvoortgang")))
                .andExpect(content().string(containsString(cancelUrl)));
        mvc.perform(get("/cadeaus").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Testcadeau"))));
        mvc.perform(post(cancelUrl).with(owner).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post(cancelUrl).with(buyer).with(csrf())).andExpect(redirectedUrl("/cadeaus"));
        mvc.perform(get("/cadeaus").with(buyer))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Testcadeau"))));
    }

    @Test
    void giftTaskShowsPhotoAndKeepsItsFilterAfterUpdatingStatus() throws Exception {
        UUID recipient = insertParticipant("filter-recipient");
        UUID buyerId = insertParticipant("filter-buyer");
        UUID wish = insertWish(recipient);
        jdbc.update("UPDATE wish SET image_id = ? WHERE id = ?", UUID.randomUUID(), wish);
        jdbc.update("INSERT INTO gift_reservation (wish_id, buyer_id) VALUES (?, ?)", wish, buyerId);
        var buyer = user(accounts.loadUserByUsername("filter-buyer"));

        mvc.perform(get("/cadeaus").param("status", "RESERVED").param("inStore", "false").with(buyer))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/wensen/" + wish + "/foto")))
                .andExpect(content().string(containsString("Markeer als besteld")))
                .andExpect(content().string(containsString("Annulering bevestigen")));
        mvc.perform(post("/cadeaus/" + wish + "/status").with(buyer).with(csrf())
                        .param("expected", "RESERVED").param("status", "RESERVED").param("inStore", "false"))
                .andExpect(redirectedUrl("/cadeaus?status=RESERVED&inStore=false"));
        mvc.perform(get("/cadeaus").param("status", "RESERVED").param("inStore", "false").with(buyer))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Testcadeau"))));
    }

    @Test
    void overviewKeepsPersonalTasksWithoutFamilyProgress() throws Exception {
        insertParticipant("overview-viewer");
        mvc.perform(get("/").with(user(accounts.loadUserByUsername("overview-viewer"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nog te doen")))
                .andExpect(content().string(not(containsString("Samen naar pakjesavond"))));
    }

    @Test
    void chatListShowsConversationsOnlyAfterTheFirstMessage() throws Exception {
        UUID recipient = insertParticipant("chat-recipient");
        UUID buyerId = insertParticipant("chat-buyer");
        UUID wish = insertWish(recipient);
        jdbc.update("INSERT INTO gift_reservation (wish_id, buyer_id) VALUES (?, ?)", wish, buyerId);
        var buyer = user(accounts.loadUserByUsername("chat-buyer"));
        var owner = user(accounts.loadUserByUsername("chat-recipient"));

        mvc.perform(get("/chats").with(buyer))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Testcadeau"))));
        mvc.perform(get("/chats").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Testcadeau"))));
        mvc.perform(get("/reserveringen/" + wish + "/chat").with(buyer))
                .andExpect(status().isOk());

        jdbc.update("INSERT INTO reservation_message (id, wish_id, sender_id, body) VALUES (?, ?, ?, 'Hallo')",
                UUID.randomUUID(), wish, buyerId);
        mvc.perform(get("/chats").with(buyer))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Testcadeau")));
        mvc.perform(get("/chats").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sint en Piet")));
    }

    @Test
    void familyMemberPagesOmitAdminLinks() throws Exception {
        insertParticipant("profile-viewer");
        UUID member = insertParticipant("profile-member");
        var viewer = user(accounts.loadUserByUsername("profile-viewer"));

        for (String path : new String[]{"/profiel", "/deelnemers/" + member + "/wensen"}) {
            mvc.perform(get(path).with(viewer))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("href=\"/beheer"))));
        }
    }

    private UUID insertWish(UUID recipient) {
        UUID wish = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO wish (id, owner_id, title, description, notes, priority, variants, created_at, updated_at)
                VALUES (?, ?, 'Testcadeau', '', '', 'NORMAL', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, wish, recipient);
        return wish;
    }

    private UUID insertParticipant(String username) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, username, password_hash, display_name, bio, role, enabled,
                                      color_mode, palette, created_at, updated_at)
                VALUES (?, ?, 'unused', ?, '', 'USER', TRUE, 'SYSTEM', 'SINTERKLAAS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, username, username);
        return id;
    }
}
