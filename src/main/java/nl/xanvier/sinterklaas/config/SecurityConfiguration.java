package nl.xanvier.sinterklaas.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import nl.xanvier.sinterklaas.user.AccountService;
import nl.xanvier.sinterklaas.user.LoginUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    ApplicationRunner bootstrapAdmin(AccountService accounts,
                                     @Value("${sinterklaas.bootstrap.username}") String username,
                                     @Value("${sinterklaas.bootstrap.password}") String password) {
        return args -> accounts.bootstrap(username, password);
    }

    @Bean
    SecurityFilterChain webSecurity(HttpSecurity http, AccountService accounts) {
        var sessionCheck = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof LoginUser user
                        && !accounts.sessionValid(user)) {
                    new SecurityContextLogoutHandler().logout(request, response, authentication);
                }
                chain.doFilter(request, response);
            }
        };
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/assets/**", "/inloggen", "/uitnodiging", "/actuator/health", "/error").permitAll()
                        .requestMatchers("/beheer", "/beheer/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/inloggen").loginProcessingUrl("/inloggen")
                        .failureUrl("/inloggen?fout").defaultSuccessUrl("/", true))
                .logout(logout -> logout.logoutUrl("/uitloggen").logoutSuccessUrl("/inloggen?uitgelogd"))
                .addFilterBefore(sessionCheck, AuthorizationFilter.class)
                .headers(headers -> headers.referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                                        + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")))
                .build();
    }
}
