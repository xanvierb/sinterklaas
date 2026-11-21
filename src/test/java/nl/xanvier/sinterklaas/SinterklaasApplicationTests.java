package nl.xanvier.sinterklaas;

import nl.xanvier.sinterklaas.user.AccountService;
import nl.xanvier.sinterklaas.user.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class SinterklaasApplicationTests {

    @Autowired AccountService accounts;
    @Autowired MockMvc mvc;

    @Test
    void contextLoads() {
    }

    @Test
    @Transactional
    void accountFormsUseEightCharacterPasswordsAndOmitBiography() throws Exception {
        accounts.bootstrap("test-form-admin", "acht1234");
        var administrator = (LoginUser) accounts.loadUserByUsername("test-form-admin");
        var admin = user(administrator);
        for (String path : new String[]{"/beheer/deelnemers/nieuw", "/beheer/deelnemers/" + administrator.id(),
                "/beheer/uitnodigingen", "/profiel"}) {
            mvc.perform(get(path).with(admin))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("Over mij"))))
                    .andExpect(content().string(not(containsString("name=\"bio\""))));
        }

        String createAccount = "/beheer/deelnemers/nieuw";
        mvc.perform(post(createAccount).with(admin).with(csrf())
                        .param("username", "test-form-user").param("displayName", "Testdeelnemer")
                        .param("role", "USER").param("password", "zeven12").param("confirmation", "zeven12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gebruik minimaal 8 en maximaal 72 tekens.")));
        mvc.perform(post(createAccount).with(admin).with(csrf())
                        .param("username", "test-form-user").param("displayName", "Testdeelnemer")
                        .param("role", "USER").param("password", "acht1234").param("confirmation", "acht1234"))
                .andExpect(redirectedUrl("/beheer"));

        var participant = user(accounts.loadUserByUsername("test-form-user"));
        mvc.perform(get("/gezin").with(participant))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"bio\""))));
        mvc.perform(get("/uitnodiging"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("minlength=\"8\"")));
        mvc.perform(post("/profiel/wachtwoord").with(participant).with(csrf())
                        .param("oldPassword", "acht1234").param("password", "nieuw123").param("confirmation", "nieuw123"))
                .andExpect(redirectedUrl("/inloggen?wachtwoord"));
    }

}
