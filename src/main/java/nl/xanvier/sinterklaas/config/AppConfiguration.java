package nl.xanvier.sinterklaas.config;

import java.time.Clock;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@Configuration
class AppConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    LocaleResolver localeResolver() {
        return new FixedLocaleResolver(Locale.forLanguageTag("nl-NL"));
    }
}
