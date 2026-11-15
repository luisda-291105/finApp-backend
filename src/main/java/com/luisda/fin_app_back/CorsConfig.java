package com.luisda.fin_app_back;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.luisda.fin_app_back.Services.UsuarioActivoInterceptor;

import java.util.List;

/**
 * @uso               Configura CORS y los interceptores de sesión y origen
 * @capa              Web
 * @responsabilidades Permitir los orígenes configurados con credenciales y validar el origen de las escrituras
 * @datos             Orígenes permitidos desde finapp.cors.allowed-origins
 * @dependencias      UsuarioActivoInterceptor, ValidacionOrigenInterceptor
 * @usadoPor          Web
 */
@Configuration
public class CorsConfig {
    private final UsuarioActivoInterceptor usuarioActivoInterceptor;
    private final List<String> origenesPermitidos;

    public CorsConfig(UsuarioActivoInterceptor usuarioActivoInterceptor,
                      @Value("${finapp.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5500}")
                      String origenesPermitidos) {
        this.usuarioActivoInterceptor = usuarioActivoInterceptor;
        this.origenesPermitidos = OrigenesWeb.normalizarLista(origenesPermitidos);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(usuarioActivoInterceptor);
                registry.addInterceptor(new ValidacionOrigenInterceptor(origenesPermitidos));
            }
        };
    }

    /**
     * CORS con credenciales: se devuelve exactamente el origen que envió el navegador
     * cuando está permitido (la comparación no distingue mayúsculas ni barra final).
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfigurationSource fuente = request -> {
            CorsConfiguration configuracion = new CorsConfiguration();
            configuracion.setAllowCredentials(true);
            configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            configuracion.setAllowedHeaders(List.of("*"));
            String origen = request.getHeader(HttpHeaders.ORIGIN);
            if (OrigenesWeb.permitido(origen, origenesPermitidos)) {
                configuracion.addAllowedOrigin(origen.trim());
            }
            return configuracion;
        };
        return new CorsFilter(fuente);
    }
}
