package com.bootsandcats.oauth2.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration to expose the embedded MkDocs static site.
 *
 * <p>The Docker image build copies the generated MkDocs site into {@code
 * BOOT-INF/classes/static/docs}. By default, Spring Boot serves everything under {@code static}
 * from "/". This configuration adds an explicit handler for clarity and to make the intent obvious.
 */
@Configuration
public class DocsWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Map /docs/** to the static docs resources on the classpath
        registry.addResourceHandler("/docs/**").addResourceLocations("classpath:/static/docs/");
    }
}
