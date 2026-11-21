package nl.xanvier.sinterklaas.invitation;

import jakarta.validation.Valid;
import java.util.UUID;
import nl.xanvier.sinterklaas.shared.WebForms;
import nl.xanvier.sinterklaas.user.AccountForms.InvitationAcceptance;
import nl.xanvier.sinterklaas.user.ChocolateFlavor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class InvitationController {

    record ProfileDraft(@NotBlank @Size(max = 80) String displayName, @Size(max = 500) String bio,
                        ChocolateFlavor chocolateFlavor) {}

    private final InvitationService invitations;

    InvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @GetMapping("/beheer/uitnodigingen")
    String manage(Model model) {
        if (!model.containsAttribute("draft")) model.addAttribute("draft", new ProfileDraft("", "", null));
        model.addAttribute("invitations", invitations.entries());
        return "invitations";
    }

    @PostMapping("/beheer/uitnodigingen")
    String issue(@Valid @ModelAttribute("draft") ProfileDraft draft, BindingResult errors,
                 @RequestParam(required = false) MultipartFile photo, Model model,
                 jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        WebForms.submit(errors, () -> model.addAttribute("invitationLink",
                invitations.issue(draft.displayName(), draft.bio(), draft.chocolateFlavor(), photo)));
        return manage(model);
    }

    @PostMapping("/beheer/uitnodigingen/{id}/link")
    String showLink(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean replace,
                    Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        try {
            model.addAttribute("invitationLink", invitations.link(id, replace));
        } catch (nl.xanvier.sinterklaas.shared.Problem problem) {
            model.addAttribute("linkError", problem.getMessage());
        }
        return manage(model);
    }

    @PostMapping("/beheer/uitnodigingen/{id}/kopieren")
    @ResponseBody
    org.springframework.http.ResponseEntity<String> copyLink(@PathVariable UUID id) {
        try {
            return org.springframework.http.ResponseEntity.ok()
                    .cacheControl(org.springframework.http.CacheControl.noStore())
                    .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                    .body(invitations.link(id, false));
        } catch (nl.xanvier.sinterklaas.shared.Problem problem) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/beheer/uitnodigingen/{id}/intrekken")
    String cancel(@PathVariable UUID id, RedirectAttributes flash) {
        invitations.cancel(id);
        return WebForms.redirect("/beheer/uitnodigingen", flash, "Uitnodiging ingetrokken.");
    }

    @GetMapping("/uitnodiging")
    String accept(Model model) {
        model.addAttribute("account", new InvitationAcceptance("", "", "", null));
        return "accept";
    }

    @PostMapping("/uitnodiging")
    String redeem(@RequestParam(defaultValue = "") String token, @Valid @ModelAttribute("account") InvitationAcceptance form,
                  BindingResult errors, Model model, RedirectAttributes flash) {
        if (WebForms.submit(errors, () -> invitations.redeem(token, form))) {
            return WebForms.redirect("/inloggen", flash, "Je account is aangemaakt. Je kunt nu inloggen.");
        }
        model.addAttribute("token", token.length() <= 512 ? token : "");
        return "accept";
    }
}
