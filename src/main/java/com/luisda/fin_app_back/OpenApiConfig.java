package com.luisda.fin_app_back;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Metadatos compartidos de la API OpenAPI de FinApp. */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "FinApp API",
                version = "v1",
                description = "API REST de FinApp para autenticación, perfiles, fuentes y administración financiera. " +
                        "La web usa una cookie JWT HttpOnly. En Swagger UI hay dos formas de autenticarse: " +
                        "(1) pegando un JWT en el botón Authorize (esquema JwtFinAppBearer), o " +
                        "(2) ejecutando POST /usuario/login y reusando la cookie FINAPP_AUTH devuelta en Set-Cookie. " +
                        "El token se puede generar con docs/generar_token_swagger.py.",
                contact = @Contact(name = "Equipo FinApp")
        ),
        security = {
                @SecurityRequirement(name = "JwtFinAppBearer"),
                @SecurityRequirement(name = "JwtFinApp")
        }
)
@SecurityScheme(
        name = "JwtFinApp",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "FINAPP_AUTH",
        description = "Cookie HttpOnly con el JWT emitido por POST /usuario/login. Swagger UI debe abrirse desde el mismo origen del backend."
)
@SecurityScheme(
        name = "JwtFinAppBearer",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT de la sesión. Pega aquí el valor del token (sin la palabra Bearer). " +
                "Genera uno con: python docs/generar_token_swagger.py <idUsuario>"
)
public class OpenApiConfig {
}
