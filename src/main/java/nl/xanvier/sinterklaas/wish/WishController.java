package nl.xanvier.sinterklaas.wish;

import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import nl.xanvier.sinterklaas.shared.Images;
import nl.xanvier.sinterklaas.shared.WebForms;
import nl.xanvier.sinterklaas.user.AccountService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class WishController {
    private final WishService wishes;
    private final AccountService accounts;
    private final Images images;
    private final GiftReservations reservations;
    private final ProductMetadataFetcher productMetadata;
    private final Clock clock;
    private final CelebrationSettings celebrationSettings;

    WishController(WishService wishes, AccountService accounts, Images images, GiftReservations reservations,
                   ProductMetadataFetcher productMetadata, Clock clock, CelebrationSettings celebrationSettings) {
        this.wishes = wishes;
        this.accounts = accounts;
        this.images = images;
        this.reservations = reservations;
        this.productMetadata = productMetadata;
        this.clock = clock;
        this.celebrationSettings = celebrationSettings;
    }

    @GetMapping("/")
    String home(Model model, jakarta.servlet.http.HttpServletResponse response) {
        if (accounts.own().role() == nl.xanvier.sinterklaas.user.Role.ADMIN) return "redirect:/beheer";
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("tasks", reservations.tasks());
        var agreement = celebrationSettings.agreement();
        model.addAttribute("agreementHeading", agreement.heading());
        model.addAttribute("agreementHtml", AgreementFormatting.render(agreement.text()));
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Europe/Amsterdam")));
        LocalDate configured = celebrationSettings.configuredDate();
        LocalDate celebration = configured == null ? LocalDate.of(today.getYear(), 12, 5) : configured;
        model.addAttribute("celebrationDateLabel", celebration.format(
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("nl-NL"))));
        long days = ChronoUnit.DAYS.between(today, celebration);
        model.addAttribute("daysUntilSinterklaas", Math.max(0, days));
        model.addAttribute("sinterklaasPassed", days < 0);
        return "overview";
    }

    @GetMapping("/beheer/pakjesavond")
    String celebrationDate(Model model) {
        model.addAttribute("celebrationDate", celebrationSettings.configuredDate());
        return "celebration-settings";
    }

    @PostMapping("/beheer/pakjesavond")
    String saveCelebrationDate(@RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               RedirectAttributes flash) {
        celebrationSettings.saveDate(date);
        return WebForms.redirect("/beheer/pakjesavond", flash, "Datum voor pakjesavond opgeslagen.");
    }

    @GetMapping("/beheer/afspraken")
    String groupAgreement(Model model) {
        model.addAttribute("agreement", celebrationSettings.agreement());
        return "group-agreement";
    }

    @PostMapping("/beheer/afspraken")
    String saveGroupAgreement(@RequestParam String heading, @RequestParam String agreement,
                              Model model, RedirectAttributes flash) {
        try {
            celebrationSettings.saveAgreement(heading, agreement);
            return WebForms.redirect("/beheer/afspraken", flash, "Groepsafspraak opgeslagen.");
        } catch (nl.xanvier.sinterklaas.shared.Problem problem) {
            model.addAttribute("agreement", new CelebrationSettings.Agreement(heading, agreement));
            model.addAttribute("agreementError", problem.getMessage());
            return "group-agreement";
        }
    }

    @GetMapping("/beheer/cadeaus")
    String adminReservations(Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("reservations", reservations.adminReservations());
        return "admin-reservations";
    }

    @PostMapping("/beheer/cadeaus/{id}/annuleren")
    String adminCancel(@PathVariable UUID id, RedirectAttributes flash) {
        boolean removed = reservations.adminCancel(id);
        return WebForms.redirect("/beheer/cadeaus", flash,
                removed ? "Reservering geannuleerd." : "Deze reservering bestaat niet meer.");
    }

    @GetMapping("/deelnemers")
    String participants(Model model) {
        model.addAttribute("participants", wishes.participants());
        return "participants";
    }

    @GetMapping("/wensen")
    String myWishes(@RequestParam(required = false) UUID owner, Model model) {
        UUID id = wishes.managedOwner(owner);
        model.addAttribute("wishes", wishes.own(id));
        model.addAttribute("lockedWishes", reservations.lockedWishes(id));
        model.addAttribute("person", accounts.participant(id));
        model.addAttribute("listOwner", owner);
        return "my-wishes";
    }

    @GetMapping("/wensen/nieuw")
    String newWish(@RequestParam(required = false) UUID owner, Model model) {
        wishes.managedOwner(owner);
        model.addAttribute("listOwner", owner);
        model.addAttribute("wish", emptyForm());
        if (owner == null) model.addAttribute("bookmarkChildren", wishes.managedChildren());
        model.addAttribute("editing", false);
        return "wish-form";
    }

    @PostMapping("/wensen")
    String create(@Valid @ModelAttribute("wish") WishForms.Input form, BindingResult errors,
                  @RequestParam(required = false) MultipartFile photo, @RequestParam(required = false) UUID owner,
                  Model model, RedirectAttributes flash) {
        wishes.managedOwner(owner);
        if (WebForms.submit(errors, () -> wishes.create(owner, form, photo))) {
            return WebForms.redirect(wishListPath(owner), flash, "Wens toegevoegd.");
        }
        model.addAttribute("listOwner", owner);
        model.addAttribute("editing", false);
        return "wish-form";
    }

    @GetMapping("/wensen/{id}/bewerken")
    String edit(@PathVariable UUID id, Model model) {
        var wish = wishes.editForm(id);
        if (reservations.isReserved(id)) {
            return "redirect:/reserveringen/" + id + "/chat";
        }
        model.addAttribute("wish", new WishForms.Input(wish.title(), wish.description(), wish.productUrl(), wish.estimatedPrice(), wish.inStore(), wish.shopName()));
        model.addAttribute("wishId", id);
        model.addAttribute("wishView", wish);
        model.addAttribute("listOwner", wish.ownerId());
        model.addAttribute("editing", true);
        return "wish-form";
    }

    @PostMapping("/wensen/{id}")
    String update(@PathVariable UUID id, @Valid @ModelAttribute("wish") WishForms.Input form,
                  BindingResult errors, @RequestParam(required = false) MultipartFile photo,
                  Model model, RedirectAttributes flash) {
        var wish = wishes.editForm(id);
        if (WebForms.submit(errors, () -> wishes.update(id, form, photo))) {
            return WebForms.redirect(wishListPath(wish.ownerId()), flash, "Wens bijgewerkt.");
        }
        if (reservations.isReserved(id)) {
            return WebForms.redirect("/wensen/" + id + "/bewerken", flash,
                    "De wens is vergrendeld. Je kunt wel een privébericht sturen.");
        }
        model.addAttribute("wishId", id);
        model.addAttribute("editing", true);
        model.addAttribute("wishView", wish);
        model.addAttribute("listOwner", wish.ownerId());
        return "wish-form";
    }

    @GetMapping("/chats/ongelezen")
    String unreadChats(Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("unreadChats", reservations.unreadChats());
        return "chat-unread";
    }

    @GetMapping("/chats")
    String chats(Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("chats", reservations.chats());
        return "chats";
    }

    @GetMapping("/reserveringen/{id}/chat")
    String chat(@PathVariable UUID id, Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("chat", reservations.thread(id));
        return "reservation-chat";
    }

    @GetMapping("/reserveringen/{id}/chat/berichten")
    String chatMessages(@PathVariable UUID id, Model model, jakarta.servlet.http.HttpServletResponse response) {
        chat(id, model, response);
        return "reservation-chat-messages :: items";
    }

    @PostMapping("/reserveringen/{id}/chat")
    String sendChat(@PathVariable UUID id, @RequestParam String body, RedirectAttributes flash) {
        String path = "/reserveringen/" + id + "/chat";
        try {
            reservations.send(id, body);
        } catch (nl.xanvier.sinterklaas.shared.Problem problem) {
            return WebForms.redirect(path, flash, problem.getMessage());
        }
        return "redirect:" + path;
    }

    @PostMapping("/wensen/productfoto")
    @ResponseBody
    ProductMetadataFetcher.ImageData productImage(@RequestParam String imageUrl) {
        return wishes.downloadProductImage(imageUrl);
    }

    @PostMapping("/wensen/productgegevens")
    @ResponseBody
    ResponseEntity<?> metadata(@RequestParam String productUrl) {
        try {
            return ResponseEntity.ok(wishes.fetchMetadata(productUrl));
        } catch (nl.xanvier.sinterklaas.shared.Problem problem) {
            return ResponseEntity.unprocessableContent().body(java.util.Map.of("message", problem.getMessage()));
        }
    }

    @PostMapping("/wensen/{id}/verwijderen")
    String delete(@PathVariable UUID id, RedirectAttributes flash) {
        String path = wishListPath(wishes.editForm(id).ownerId());
        try {
            wishes.delete(id);
        } catch (nl.xanvier.sinterklaas.shared.Problem problem) {
            return WebForms.redirect(path, flash, problem.getMessage());
        }
        return WebForms.redirect(path, flash, "Wens verwijderd.");
    }

    @GetMapping("/deelnemers/{id}/wensen")
    String participantWishes(@PathVariable UUID id, Model model) {
        if (wishes.manages(id)) return "redirect:" + wishListPath(id);
        model.addAttribute("person", accounts.participant(id));
        model.addAttribute("wishes", wishes.forParticipant(id));
        model.addAttribute("availability", reservations.availability(id));
        return "participant-wishes";
    }

    @PostMapping("/deelnemers/{ownerId}/wensen/{wishId}/reserveren")
    String reserve(@PathVariable UUID ownerId, @PathVariable UUID wishId, RedirectAttributes flash) {
        boolean reserved = reservations.reserve(ownerId, wishId);
        return WebForms.redirect("/deelnemers/" + ownerId + "/wensen", flash,
                reserved ? "Cadeau gereserveerd." : "Deze wens is al gereserveerd.");
    }

    @GetMapping("/cadeaus")
    String gifts(@RequestParam(required = false) GiftStatus status,
                 @RequestParam(required = false) Boolean inStore, Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("groups", reservations.trackedGifts(status, inStore));
        model.addAttribute("filter", status);
        model.addAttribute("inStoreFilter", inStore);
        return "gifts";
    }

    @PostMapping("/cadeaus/{id}/status")
    String moveGiftStatus(@PathVariable UUID id, @RequestParam GiftStatus expected,
                          @RequestParam(defaultValue = "false") boolean back,
                          @RequestParam(required = false) GiftStatus status,
                          @RequestParam(required = false) Boolean inStore, RedirectAttributes flash) {
        boolean updated = reservations.moveStatus(id, expected, back);
        retainGiftFilters(status, inStore, flash);
        return WebForms.redirect("/cadeaus", flash,
                updated ? "Cadeaustatus bijgewerkt." : "De cadeaustatus kon niet worden bijgewerkt.");
    }

    @PostMapping("/cadeaus/{id}/tijd")
    String editGiftTime(@PathVariable UUID id, @RequestParam GiftStatus step,
                        @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso =
                                org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
                        java.time.LocalDateTime time, RedirectAttributes flash) {
        boolean updated = reservations.editGiftTime(id, step, time);
        return WebForms.redirect("/cadeaus", flash,
                updated ? "Datum en tijd bijgewerkt." : "De datum en tijd konden niet worden bijgewerkt.");
    }

    @GetMapping("/reserveringen")
    String reservations() {
        return "redirect:/cadeaus";
    }

    @GetMapping("/reserveringen/{wishId}/favicon")
    ResponseEntity<byte[]> reservationFavicon(@PathVariable UUID wishId) {
        return favicon(reservations.productUrl(wishId));
    }

    @GetMapping("/wensen/{wishId}/favicon")
    ResponseEntity<byte[]> wishFavicon(@PathVariable UUID wishId) {
        return favicon(wishes.productUrl(wishId));
    }

    private ResponseEntity<byte[]> favicon(String productUrl) {
        try {
            var icon = productMetadata.favicon(productUrl);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(icon.contentType())).body(icon.content());
        } catch (IOException | IllegalArgumentException unavailable) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/reserveringen/{wishId}/annuleren")
    String cancelReservation(@PathVariable UUID wishId, @RequestParam(required = false) GiftStatus status,
                             @RequestParam(required = false) Boolean inStore, RedirectAttributes flash) {
        reservations.cancel(wishId);
        retainGiftFilters(status, inStore, flash);
        return WebForms.redirect("/cadeaus", flash, "Reservering geannuleerd.");
    }

    private static void retainGiftFilters(GiftStatus status, Boolean inStore, RedirectAttributes redirect) {
        if (status != null) redirect.addAttribute("status", status.name());
        if (inStore != null) redirect.addAttribute("inStore", inStore);
    }

    @GetMapping("/wensen/{id}/foto")
    ResponseEntity<FileSystemResource> wishImage(@PathVariable UUID id) {
        return images.serve(wishes.image(id));
    }

    private static String wishListPath(UUID owner) {
        return owner == null ? "/wensen" : "/wensen?owner=" + owner;
    }

    private static WishForms.Input emptyForm() {
        return new WishForms.Input("", "", "", null, false, null);
    }
}
