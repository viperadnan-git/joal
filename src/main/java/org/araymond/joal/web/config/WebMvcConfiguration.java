package org.araymond.joal.web.config;

import org.araymond.joal.web.annotations.ConditionalOnWebUi;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.*;

@ConditionalOnWebUi
// Do not use @EnableWebMvc as it will remove all the default springboot config.
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/ui/**")
                .addResourceLocations("classpath:/public/");
    }

    @Override
    public void addViewControllers(final ViewControllerRegistry registry) {
        // Redirect root to /ui/
        registry.addRedirectViewController("/", "/ui/");
        // The webui passes the credentials along with ui call, redirect them as well
        registry.addRedirectViewController("/ui", "/ui/").setKeepQueryParams(true);
        // Forward /ui/ to serve index.html
        registry.addViewController("/ui/").setViewName("forward:/ui/index.html");
        registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
    }
}
