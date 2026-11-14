package com.luisda.fin_app_back;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.luisda.fin_app_back.Services.UsuarioActivoInterceptor;

@Configuration
public class CorsConfig {
    private final UsuarioActivoInterceptor usuarioActivoInterceptor;
    public CorsConfig(UsuarioActivoInterceptor usuarioActivoInterceptor) { this.usuarioActivoInterceptor = usuarioActivoInterceptor; }
    @Value("${finapp.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5500}")
    private String origenesPermitidos;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        String[] origenes = origenesPermitidos.split(",");
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(usuarioActivoInterceptor);
                registry.addInterceptor(new ValidacionOrigenInterceptor(origenes));
            }
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(origenes)
                        .allowedMethods(
                                "GET",
                                "POST",
                                "PUT",
                                "PATCH",
                                "DELETE",
                                "OPTIONS"
                        )
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
