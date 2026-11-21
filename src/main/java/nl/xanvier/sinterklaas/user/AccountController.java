package nl.xanvier.sinterklaas.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import nl.xanvier.sinterklaas.shared.Images;
import nl.xanvier.sinterklaas.shared.WebForms;
import nl.xanvier.sinterklaas.user.AccountForms.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class AccountController {

    private final AccountService accounts;
    private final Images images;

    AccountController(AccountService accounts, Images images) {
        this.accounts = accounts;
        this.images = images;
    }

    @GetMapping("/inloggen")
    String login() {
        return "login";
    }

    @GetMapping("/profiel")
    String profile(Model model) {
        var user = accounts.own();
        model.addAttribute("profile", new Profile(user.person().displayName(), user.colorMode(), user.person().chocolateFlavor()));
        model.addAttribute("password", new Password("", "", ""));
        return profilePage(model);
    }

    @PostMapping("/profiel")
    String saveProfile(@Valid @ModelAttribute("profile") Profile form, BindingResult errors,
                       @RequestParam(required = false) MultipartFile photo, Model model, RedirectAttributes flash) {
        if (WebForms.submit(errors, () -> accounts.editProfile(form, photo))) {
            return WebForms.redirect("/profiel", flash, "Profiel opgeslagen.");
        }
        model.addAttribute("password", new Password("", "", ""));
        return profilePage(model);
    }

    @PostMapping("/profiel/wachtwoord")
    String changePassword(@Valid @ModelAttribute("password") Password password, BindingResult errors,
                          Model model, RedirectAttributes flash, HttpServletRequest request, HttpServletResponse response) {
        if (WebForms.submit(errors, () -> accounts.changePassword(password))) {
            new SecurityContextLogoutHandler().logout(request, response, null);
            return "redirect:/inloggen?wachtwoord";
        }
        var user = accounts.own();
        model.addAttribute("profile", new Profile(user.person().displayName(), user.colorMode(), user.person().chocolateFlavor()));
        return profilePage(model);
    }

    @GetMapping("/deelnemers/{id}/foto")
    ResponseEntity<FileSystemResource> avatar(@PathVariable UUID id) {
        return images.serve(accounts.avatar(id));
    }

    @GetMapping("/beheer")
    String admin(Model model) {
        var all = accounts.all();
        model.addAttribute("accounts", all);
        model.addAttribute("activeParticipants", all.stream()
                .filter(account -> account.role() == Role.USER && account.person().enabled()).count());
        model.addAttribute("disabledAccounts", all.stream().filter(account -> !account.person().enabled()).count());
        return "admin";
    }

    @GetMapping("/beheer/deelnemers/nieuw")
    String newAccount(Model model) {
        model.addAttribute("account", new NewAccount("", "", "", ""));
        model.addAttribute("role", Role.USER);
        return "account-new";
    }

    @PostMapping("/beheer/deelnemers/nieuw")
    String create(@Valid @ModelAttribute("account") NewAccount form, BindingResult errors,
                  @RequestParam Role role, Model model, RedirectAttributes flash) {
        if (WebForms.submit(errors, () -> accounts.create(form, role))) {
            return WebForms.redirect("/beheer", flash, "Deelnemer aangemaakt.");
        }
        model.addAttribute("role", role);
        return "account-new";
    }

    @GetMapping("/beheer/deelnemers/{id}")
    String edit(@PathVariable UUID id, Model model) {
        var account = accounts.managed(id);
        model.addAttribute("account", new EditAccount(account.username(), account.person().displayName(),
                account.role(), account.person().enabled()));
        return editPage(account.person(), model);
    }

    @PostMapping("/beheer/deelnemers/{id}")
    String update(@PathVariable UUID id, @Valid @ModelAttribute("account") EditAccount form, BindingResult errors,
                  @RequestParam(required = false) MultipartFile photo, Model model, RedirectAttributes flash) {
        if (WebForms.submit(errors, () -> accounts.edit(id, form, photo))) {
            return WebForms.redirect("/beheer", flash, "Deelnemer bijgewerkt.");
        }
        return editPage(accounts.managed(id).person(), model);
    }

    private String editPage(AccountService.Person person, Model model) {
        model.addAttribute("accountId", person.id());
        model.addAttribute("hasAvatar", person.hasAvatar());
        return "account-edit";
    }

    private String profilePage(Model model) {
        model.addAttribute("person", accounts.own().person());
        return "profile";
    }
}
