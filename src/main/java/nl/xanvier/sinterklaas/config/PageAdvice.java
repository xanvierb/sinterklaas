package nl.xanvier.sinterklaas.config;

import nl.xanvier.sinterklaas.user.AccountService;
import nl.xanvier.sinterklaas.user.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
class PageAdvice {

    private final AccountService accounts;

    PageAdvice(AccountService accounts) {
        this.accounts = accounts;
    }

    @ModelAttribute("signedIn")
    boolean signedIn(@AuthenticationPrincipal LoginUser user) {
        return user != null;
    }

    @ModelAttribute("viewer")
    AccountService.AccountView viewer(@AuthenticationPrincipal LoginUser user) {
        return user == null ? null : accounts.own();
    }
}
