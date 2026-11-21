package nl.xanvier.sinterklaas.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import nl.xanvier.sinterklaas.shared.Images;
import nl.xanvier.sinterklaas.shared.Problem;
import nl.xanvier.sinterklaas.shared.WebForms;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class FamilyController {
    record ChildForm(@NotBlank @Size(max = 80) String displayName, @Size(max = 500) String bio,
                     ChocolateFlavor chocolateFlavor) {}
    record ParentLink(UUID childId, UUID parentId, String name, boolean accepted) {}
    record ParentChoice(UUID id, String name) {}
    record ChildCard(AccountService.Person person, List<ParentLink> parents, List<ParentChoice> choices) {}
    record Request(UUID childId, String name) {}

    private final CurrentUser current;
    private final AccountRepository accounts;
    private final AccountService profiles;
    private final FamilyAccess access;
    private final JdbcTemplate jdbc;
    private final Images images;
    private final Clock clock;

    FamilyController(CurrentUser current, AccountRepository accounts, AccountService profiles, FamilyAccess access,
                     JdbcTemplate jdbc, Images images, Clock clock) {
        this.current = current;
        this.accounts = accounts;
        this.profiles = profiles;
        this.access = access;
        this.jdbc = jdbc;
        this.images = images;
        this.clock = clock;
    }

    @GetMapping("/gezin")
    String index(Model model) {
        var parent = parent();
        if (!model.containsAttribute("child")) model.addAttribute("child", new ChildForm("", "", null));
        var parents = jdbc.query("""
                SELECT p.child_id, p.parent_id, u.display_name, p.accepted
                FROM child_parent p JOIN app_user u ON u.id = p.parent_id
                WHERE p.child_id IN (SELECT child_id FROM child_parent WHERE parent_id = ? AND accepted = TRUE)
                ORDER BY u.display_name, u.id
                """, (r, n) -> new ParentLink(r.getObject(1, UUID.class), r.getObject(2, UUID.class),
                r.getString(3), r.getBoolean(4)), parent.id()).stream()
                .collect(Collectors.groupingBy(ParentLink::childId));
        var choices = jdbc.query("""
                SELECT id, display_name FROM app_user
                WHERE enabled = TRUE AND role = 'USER' AND child_profile = FALSE AND id <> ?
                ORDER BY display_name, id
                """, (r, n) -> new ParentChoice(r.getObject(1, UUID.class), r.getString(2)), parent.id());
        var children = access.children(parent.id()).stream().map(id -> {
            var links = parents.getOrDefault(id, List.of());
            var linkedIds = links.stream().map(ParentLink::parentId).collect(Collectors.toSet());
            return new ChildCard(profiles.participant(id), links,
                    choices.stream().filter(choice -> !linkedIds.contains(choice.id())).toList());
        }).toList();
        model.addAttribute("children", children);
        model.addAttribute("requests", jdbc.query("""
                SELECT u.id, u.display_name FROM child_parent p JOIN app_user u ON u.id = p.child_id
                WHERE p.parent_id = ? AND p.accepted = FALSE AND u.enabled = TRUE ORDER BY u.display_name
                """, (r, n) -> new Request(r.getObject(1, UUID.class), r.getString(2)), parent.id()));
        return "family";
    }

    @PostMapping("/gezin")
    @Transactional
    public String create(@Valid @ModelAttribute("child") ChildForm form, BindingResult errors,
                         @RequestParam(required = false) MultipartFile photo, Model model, RedirectAttributes flash) {
        var parent = parent();
        if (WebForms.submit(errors, () -> {
            var now = clock.instant();
            String name = form.displayName().strip();
            String bio = form.bio() == null ? "" : form.bio().strip();
            Account child = new Account("kind-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24),
                    "!disabled", name, bio, Role.USER, now);
            child.markAsChild();
            child.profile(name, bio, ColorMode.DARK, Palette.SINTERKLAAS,
                    form.chocolateFlavor(), images.replace(null, photo), now);
            accounts.saveAndFlush(child);
            jdbc.update("INSERT INTO child_parent (child_id, parent_id, accepted) VALUES (?, ?, TRUE)",
                    child.getId(), parent.id());
        })) return WebForms.redirect("/gezin", flash, "Kinderprofiel toegevoegd.");
        return index(model);
    }

    @PostMapping("/gezin/{id}/ouder")
    @Transactional
    public String invite(@PathVariable UUID id, @RequestParam UUID parentId, RedirectAttributes flash) {
        var parent = parent();
        if (!access.children(parent.id()).contains(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        jdbc.queryForList("SELECT id FROM app_user WHERE id = ? FOR UPDATE", UUID.class, id);
        int created = jdbc.update("""
                INSERT INTO child_parent (child_id, parent_id)
                SELECT ?, id FROM app_user WHERE id = ? AND enabled = TRUE AND role = 'USER' AND child_profile = FALSE
                  AND NOT EXISTS (SELECT 1 FROM child_parent WHERE child_id = ? AND parent_id = ?)
                """, id, parentId, id, parentId);
        return WebForms.redirect("/gezin", flash, created == 1 ? "Uitnodiging voor de andere ouder verstuurd."
                : "Deze deelnemer kan niet opnieuw worden uitgenodigd.");
    }

    @PostMapping("/gezin/{id}/reageren")
    @Transactional
    public String respond(@PathVariable UUID id, @RequestParam boolean accept, RedirectAttributes flash) {
        UUID viewer = parent().id();
        jdbc.queryForList("SELECT id FROM app_user WHERE id = ? FOR UPDATE", UUID.class, id);
        if (accept && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM gift_reservation r JOIN wish w ON w.id = r.wish_id
                               WHERE w.owner_id = ? AND r.buyer_id = ?)
                """, Boolean.class, id, viewer))) {
            return WebForms.redirect("/gezin", flash, "Je kunt deze koppeling nog niet accepteren zolang je een cadeau voor dit kind hebt gereserveerd.");
        }
        int changed = accept
                ? jdbc.update("UPDATE child_parent SET accepted = TRUE WHERE child_id = ? AND parent_id = ? AND accepted = FALSE", id, viewer)
                : jdbc.update("DELETE FROM child_parent WHERE child_id = ? AND parent_id = ? AND accepted = FALSE", id, viewer);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return WebForms.redirect("/gezin", flash, accept ? "Kinderprofiel gekoppeld." : "Uitnodiging geweigerd.");
    }

    private LoginUser parent() {
        LoginUser parent = current.require();
        if (parent.admin()) throw new AccessDeniedException("Alleen deelnemers kunnen een gezin beheren.");
        return parent;
    }
}
