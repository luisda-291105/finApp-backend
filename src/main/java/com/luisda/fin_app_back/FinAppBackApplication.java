package com.luisda.fin_app_back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
public class FinAppBackApplication {

	public static void main(String[] args) {
		cargarDotEnv();
		SpringApplication.run(FinAppBackApplication.class, args);
	}

	private static void cargarDotEnv() {
		List<Path> rutas = List.of(
				Path.of(".env"),
				Path.of("../.env"),
				Path.of("finApp-backend/.env"),
				Path.of(System.getProperty("user.dir", "."), ".env")
		);
		for (Path ruta : rutas) {
			try {
				if (Files.exists(ruta) && Files.isRegularFile(ruta)) {
					for (String linea : Files.readAllLines(ruta)) {
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
							if (System.getProperty(clave) == null && System.getenv(clave) == null) {
								System.setProperty(clave, valor);
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
