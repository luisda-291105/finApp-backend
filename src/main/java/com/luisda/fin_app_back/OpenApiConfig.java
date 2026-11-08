package com.luisda.fin_app_back;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Metadatos compartidos de la API OpenAPI de FinApp. */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "FinApp API",
                version = "v1",
                description = "API REST de FinApp para autenticación, perfiles, fuentes y administración financiera. " +
                        "Las operaciones de usuario usan sesión HTTP; inicia sesión en /usuario/login antes de probar rutas protegidas.",
                contact = @Contact(name = "Equipo FinApp")
        )
)
@SecurityScheme(
        name = "SesionFinApp",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "JSESSIONID",
        description = "Cookie de sesión emitida por POST /usuario/login. Swagger UI debe abrirse desde el mismo origen del backend."
)
public class OpenApiConfig {
}
