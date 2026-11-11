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
                        "Las operaciones protegidas validan un JWT HttpOnly; inicia sesión en /usuario/login antes de probarlas.",
                contact = @Contact(name = "Equipo FinApp")
        )
)
@SecurityScheme(
        name = "JwtFinApp",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "FINAPP_AUTH",
        description = "Cookie HttpOnly con el JWT emitido por POST /usuario/login. Swagger UI debe abrirse desde el mismo origen del backend."
)
public class OpenApiConfig {
}
