package nl.xanvier.sinterklaas.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.support.MultipartFilter;

@Configuration
class MultipartConfiguration {

    @Bean
    FilterRegistrationBean<MultipartFilter> multipartBeforeSecurity() {
        var filter = new MultipartFilter();
        filter.setMultipartResolverBeanName("multipartResolver");
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(-110);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.addUrlPatterns("/gezin", "/profiel", "/wensen/*", "/beheer/wensen/*", "/beheer/deelnemers/*", "/beheer/uitnodigingen");
        return registration;
    }
}
