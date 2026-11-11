package com.luisda.fin_app_back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

@SpringBootApplication
@EnableScheduling
public class FinAppBackApplication {

	public static void main(String[] args) {
		cargarDotEnv();
		SpringApplication.run(FinAppBackApplication.class, args);
	}

	private static void cargarDotEnv() {
		Path directorioTrabajo = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
		Set<Path> rutas = new LinkedHashSet<>();
		rutas.add(directorioTrabajo.resolve("finApp-backend").resolve(".env"));
		try {
			Path clases = Path.of(FinAppBackApplication.class.getProtectionDomain()
					.getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
			if (!Files.isDirectory(clases)) clases = clases.getParent();
			for (int nivel = 0; nivel < 3 && clases != null; nivel++, clases = clases.getParent()) {
				rutas.add(clases.resolve(".env"));
			}
		} catch (Exception e) {
			System.err.println("[FinApp] No se pudo resolver la ubicación del backend para cargar .env: " + e.getMessage());
		}
		rutas.add(directorioTrabajo.resolve(".env"));
		if (directorioTrabajo.getParent() != null) {
			rutas.add(directorioTrabajo.getParent().resolve(".env"));
		}
		for (Path ruta : rutas) {
			try {
				if (Files.exists(ruta) && Files.isRegularFile(ruta)) {
					for (String linea : Files.readAllLines(ruta, StandardCharsets.UTF_8)) {
						String limpia = linea.trim();
						if (limpia.isEmpty() || limpia.startsWith("#")) continue;
						int idx = limpia.indexOf('=');
						if (idx > 0) {
							String clave = limpia.substring(0, idx).trim();
							String valor = limpia.substring(idx + 1).trim();
							if ((valor.startsWith("\"") && valor.endsWith("\"")) ||
								(valor.startsWith("'") && valor.endsWith("'"))) {
								valor = valor.substring(1, valor.length() - 1);
							}
							String valorSistema = System.getProperty(clave);
							String valorEntorno = System.getenv(clave);
							boolean usarArchivo = (valorSistema == null || valorSistema.isBlank())
									&& (valorEntorno == null || valorEntorno.isBlank());
							if (usarArchivo) {
								System.setProperty(clave, valor);
								if ("FINAPP_JWT_SECRET".equals(clave)) {
									String clavePropiedad = System.getProperty("finapp.jwt.secret");
									if (clavePropiedad == null || clavePropiedad.isBlank()) {
										System.setProperty("finapp.jwt.secret", valor);
									}
								}
							}
						}
					}
					System.out.println("[FinApp] Archivo .env cargado exitosamente desde: " + ruta.toAbsolutePath());
					break;
				}
			} catch (Exception e) {
				System.err.println("[FinApp] No se pudo leer .env: " + e.getMessage());
			}
		}
	}

}
