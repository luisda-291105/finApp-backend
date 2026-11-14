package com.luisda.fin_app_back.Services;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @uso               Resuelve configuración sensible desde el entorno o desde el archivo .env
 * @capa              Servicio
 * @responsabilidades Leer claves de entorno con respaldo en .env sin exponer valores en logs
 * @datos             Variables de entorno y archivo .env local (ignorado por git)
 * @dependencias      Java NIO
 * @usadoPor          SAccesoAdmin, SAsistenteChat
 */
@Component
public class SConfiguracionEntorno {
    private final Map<String, String> valores;

    public SConfiguracionEntorno() {
        this(cargarArchivoDotEnv());
    }

    public SConfiguracionEntorno(Map<String, String> valores) {
        this.valores = valores == null ? Map.of() : Map.copyOf(valores);
    }

    /** Devuelve el valor de una clave o null si no está configurada. */
    public String valor(String clave) {
        if (clave == null || clave.isBlank()) return null;
        String delEntorno = System.getenv(clave);
        if (delEntorno != null && !delEntorno.isBlank()) return delEntorno.trim();
        String valor = valores.get(clave);
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** Indica si la clave está configurada. */
    public boolean configurada(String clave) {
        return valor(clave) != null;
    }

    private static Map<String, String> cargarArchivoDotEnv() {
        Map<String, String> mapa = new HashMap<>();
        List<Path> posiblesRutas = List.of(
                Path.of(".env"),
                Path.of("../.env"),
                Path.of("finApp-backend/.env"),
                Path.of(System.getProperty("user.dir", "."), ".env")
        );
        for (Path ruta : posiblesRutas) {
            try {
                if (!Files.exists(ruta) || !Files.isRegularFile(ruta)) continue;
                List<String> lineas = Files.readAllLines(ruta, StandardCharsets.UTF_8);
                for (String linea : lineas) {
                    String limpia = linea.trim();
                    if (limpia.isEmpty() || limpia.startsWith("#")) continue;
                    int idx = limpia.indexOf('=');
                    if (idx <= 0) continue;
                    String clave = limpia.substring(0, idx).trim();
                    String valor = limpia.substring(idx + 1).trim();
                    if ((valor.startsWith("\"") && valor.endsWith("\""))
                            || (valor.startsWith("'") && valor.endsWith("'"))) {
                        valor = valor.substring(1, valor.length() - 1);
                    }
                    mapa.put(clave, valor);
                }
                if (!mapa.isEmpty()) break;
            } catch (Exception ignored) {
                // Sin archivo .env legible: se usan solo variables de entorno.
            }
        }
        return mapa;
    }
}
