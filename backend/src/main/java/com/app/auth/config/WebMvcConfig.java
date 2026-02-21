package com.app.auth.config;

import com.app.auth.security.AdminApiInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminApiInterceptor adminApiInterceptor;

    public WebMvcConfig(AdminApiInterceptor adminApiInterceptor) {
        this.adminApiInterceptor = adminApiInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminApiInterceptor)
            .addPathPatterns("/admin/api/**")
            .excludePathPatterns(
                "/admin/api/session/login",
                "/admin/api/bcrypt/generate"
            );
    }
}
