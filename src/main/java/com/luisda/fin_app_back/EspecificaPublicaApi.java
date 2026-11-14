package com.luisda.fin_app_back;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @uso               Construye la especificación OpenAPI visible sin sesión
 * @capa              Web
 * @responsabilidades Exponer solo las rutas públicas (login, registro y asistente público)
 * @datos             Rutas públicas de FinApp
 * @dependencias      Ninguno
 * @usadoPor          UsuarioActivoInterceptor
 */
@Component
public class EspecificaPublicaApi {

    /** Especificación mínima que se entrega a quien no tiene sesión activa. */
    public Map<String, Object> construir() {
        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/usuario/login", ruta("post", "Inicia sesión con usuario y contraseña.",
                cuerpoCredenciales("usuario"),
                respuestas("200", "Sesión iniciada.", "401", "Credenciales incorrectas.")));
        paths.put("/usuario/admin-login", ruta("post", "Inicia sesión administrativa para operación y pruebas.",
                cuerpoCredenciales("admin"),
                respuestas("200", "Sesión administrativa iniciada.",
                        "401", "Credenciales administrativas incorrectas.")));
        paths.put("/usuario", ruta("post", "Crea una cuenta de usuario.",
                cuerpoVacio(),
                respuestas("201", "Cuenta creada.")));
        paths.put("/asistente/publico", ruta("post", "Consulta informativa del asistente público.",
                cuerpoVacio(),
                respuestas("200", "Respuesta del asistente.")));

        Map<String, Object> esquemas = new LinkedHashMap<>();
        esquemas.put("JwtFinAppBearer", esquemaBearer());
        esquemas.put("JwtFinApp", esquemaCookie());

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "FinApp API");
        info.put("version", "v1");
        info.put("description", "API REST de FinApp. Sin una sesión activa solo se exponen las rutas públicas "
                + "(inicio de sesión, registro y asistente público). Inicia sesión para consultar el resto.");

        Map<String, Object> especificacion = new LinkedHashMap<>();
        especificacion.put("openapi", "3.0.1");
        especificacion.put("info", info);
        especificacion.put("paths", paths);
        Map<String, Object> componentes = new LinkedHashMap<>();
        componentes.put("securitySchemes", esquemas);
        especificacion.put("components", componentes);
        List<Map<String, List<String>>> seguridad = new ArrayList<>();
        Map<String, List<String>> esquema = new LinkedHashMap<>();
        esquema.put("JwtFinAppBearer", List.of());
        seguridad.add(esquema);
        especificacion.put("security", seguridad);
        return especificacion;
    }

    /** Rutas públicas declaradas en la especificación reducida. */
    public List<String> rutasPublicas() {
        List<String> rutas = new ArrayList<>();
        rutas.add("/usuario/login");
        rutas.add("/usuario/admin-login");
        rutas.add("/usuario");
        rutas.add("/asistente/publico");
        return rutas;
    }

    private Map<String, Object> ruta(String metodo, String resumen, Map<String, Object> parametros,
                                     Map<String, Object> respuestas) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put(metodo, operacion(resumen, parametros, respuestas));
        return item;
    }

    private Map<String, Object> operacion(String resumen, Map<String, Object> parametros,
                                          Map<String, Object> respuestas) {
        Map<String, Object> operacion = new LinkedHashMap<>();
        operacion.put("summary", resumen);
        operacion.put("tags", List.of("Sesión"));
        operacion.put("security", List.of());
        if (parametros != null) operacion.putAll(parametros);
        operacion.put("responses", respuestas);
        return operacion;
    }

    private Map<String, Object> cuerpoCredenciales(String ejemplo) {
        Map<String, Object> propiedades = new LinkedHashMap<>();
        propiedades.put("identificador", propiedadTexto(ejemplo));
        propiedades.put("contrasena", propiedadPassword());

        Map<String, Object> esquema = new LinkedHashMap<>();
        esquema.put("type", "object");
        esquema.put("required", List.of("identificador", "contrasena"));
        esquema.put("properties", propiedades);

        Map<String, Object> medio = new LinkedHashMap<>();
        medio.put("schema", esquema);

        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("application/json", medio);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("content", contenido);
        return cuerpo;
    }

    private Map<String, Object> cuerpoVacio() {
        Map<String, Object> esquema = new LinkedHashMap<>();
        esquema.put("type", "object");

        Map<String, Object> medio = new LinkedHashMap<>();
        medio.put("schema", esquema);

        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("application/json", medio);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("content", contenido);
        return cuerpo;
    }

    private Map<String, Object> propiedadTexto(String ejemplo) {
        Map<String, Object> propiedad = new LinkedHashMap<>();
        propiedad.put("type", "string");
        propiedad.put("example", ejemplo);
        return propiedad;
    }

    private Map<String, Object> propiedadPassword() {
        Map<String, Object> propiedad = new LinkedHashMap<>();
        propiedad.put("type", "string");
        propiedad.put("format", "password");
        return propiedad;
    }

    private Map<String, Object> respuestas(String codigoOk, String descripcionOk) {
        Map<String, Object> respuestas = new LinkedHashMap<>();
        respuestas.put(codigoOk, respuesta(descripcionOk));
        return respuestas;
    }

    private Map<String, Object> respuestas(String codigoOk, String descripcionOk,
                                            String codigoError, String descripcionError) {
        Map<String, Object> respuestas = new LinkedHashMap<>();
        respuestas.put(codigoOk, respuesta(descripcionOk));
        if (codigoError != null) respuestas.put(codigoError, respuesta(descripcionError));
        return respuestas;
    }

    private Map<String, Object> respuesta(String descripcion) {
        Map<String, Object> propiedades = new LinkedHashMap<>();
        Map<String, Object> mensaje = new LinkedHashMap<>();
        mensaje.put("type", "string");
        mensaje.put("example", descripcion);
        propiedades.put("mensaje", mensaje);

        Map<String, Object> esquema = new LinkedHashMap<>();
        esquema.put("type", "object");
        esquema.put("properties", propiedades);

        Map<String, Object> medio = new LinkedHashMap<>();
        medio.put("schema", esquema);

        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("application/json", medio);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("description", descripcion);
        respuesta.put("content", contenido);
        return respuesta;
    }

    private Map<String, Object> esquemaBearer() {
        Map<String, Object> esquema = new LinkedHashMap<>();
        esquema.put("type", "http");
        esquema.put("scheme", "bearer");
        esquema.put("bearerFormat", "JWT");
        return esquema;
    }

    private Map<String, Object> esquemaCookie() {
        Map<String, Object> esquema = new LinkedHashMap<>();
        esquema.put("type", "apiKey");
        esquema.put("in", "cookie");
        esquema.put("name", "FINAPP_AUTH");
        return esquema;
    }
}
