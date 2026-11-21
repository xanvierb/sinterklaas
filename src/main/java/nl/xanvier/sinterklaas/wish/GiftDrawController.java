package nl.xanvier.sinterklaas.wish;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import nl.xanvier.sinterklaas.shared.Problem;
import nl.xanvier.sinterklaas.shared.WebForms;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class GiftDrawController {
    private final GiftDraw draws;

    GiftDrawController(GiftDraw draws) {
        this.draws = draws;
    }

    @GetMapping("/lootjes")
    String mine(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("draws", draws.mine());
        return "my-draw";
    }

    @GetMapping("/beheer/lootjes")
    String settings(@RequestParam(required = false) Long reveal, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var rounds = draws.rounds();
        model.addAttribute("rounds", rounds);
        if (reveal != null && rounds.stream().anyMatch(round -> round.id() == reveal && round.drawn())) {
            model.addAttribute("revealedRound", reveal);
            model.addAttribute("assignments", draws.assignments(reveal));
        }
        model.addAttribute("participants", draws.participants());
        return "draw-settings";
    }

    @PostMapping("/beheer/lootjes")
    String create(@RequestParam String name, RedirectAttributes flash) {
        return apply(() -> draws.create(name), flash, "Trekking toegevoegd.");
    }

    @PostMapping("/beheer/lootjes/{id}/trekken")
    String draw(@PathVariable long id, @RequestParam(required = false) List<UUID> participantIds, RedirectAttributes flash) {
        return apply(() -> draws.draw(id, participantIds), flash, "De lootjes zijn getrokken.");
    }

    @PostMapping("/beheer/lootjes/{id}/zichtbaarheid")
    String visibility(@PathVariable long id, @RequestParam boolean visible, RedirectAttributes flash) {
        return apply(() -> draws.setVisible(id, visible), flash,
                visible ? "Trekking weer zichtbaar." : "Trekking verborgen.");
    }

    private String apply(Runnable action, RedirectAttributes flash, String success) {
        try {
            action.run();
            return WebForms.redirect("/beheer/lootjes", flash, success);
        } catch (Problem problem) {
            flash.addFlashAttribute("drawError", problem.getMessage());
            return "redirect:/beheer/lootjes";
        }
    }
}
