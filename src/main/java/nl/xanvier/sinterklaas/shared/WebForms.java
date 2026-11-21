package nl.xanvier.sinterklaas.shared;

import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

public final class WebForms {

    private WebForms() {}

    public static boolean submit(BindingResult errors, Runnable action) {
        if (errors.hasErrors()) {
            return false;
        }
        try {
            action.run();
            return true;
        } catch (Problem problem) {
            errors.reject("business", problem.getMessage());
            return false;
        }
    }

    public static String redirect(String path, RedirectAttributes flash, String notice) {
        flash.addFlashAttribute("notice", notice);
        return "redirect:" + path;
    }
}
