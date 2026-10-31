package com.luisda.fin_app_back.Services;

import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Recupera secciones relevantes de la guía pública empaquetada con el backend. */
@Service
public class SDocumentacionFinApp {
    private static final Set<String> PALABRAS_COMUNES = Set.of(
            "para", "como", "donde", "cuando", "quien", "cual", "puedo", "tiene", "desde", "sobre", "esta"
    );
    private static final Pattern SECCIONES = Pattern.compile("(?m)(?=^#{1,3}\\s)");
    private final List<Fragmento> fragmentos;

    public SDocumentacionFinApp(@Value("classpath:agent/knowledge/finapp.md") Resource guia) {
        this.fragmentos = cargarFragmentos(guia);
    }

    public List<Fragmento> buscar(String consulta) {
        List<String> terminos = tokenizar(consulta).stream()
                .filter(termino -> termino.length() > 2 && !PALABRAS_COMUNES.contains(termino))
                .distinct()
                .toList();
        if (terminos.isEmpty()) return List.of();

        return fragmentos.stream()
                .map(fragmento -> new Fragmento(
                        fragmento.titulo(), fragmento.contenido(), puntuar(fragmento, terminos)))
                .filter(fragmento -> fragmento.puntuacion() > 0)
                .sorted(Comparator.comparingDouble(Fragmento::puntuacion).reversed())
                .limit(3)
                .toList();
    }

    private List<Fragmento> cargarFragmentos(Resource guia) {
        try (var entrada = guia.getInputStream()) {
            String contenido = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            return Arrays.stream(SECCIONES.split(contenido))
                    .map(String::trim)
                    .filter(seccion -> !seccion.isBlank())
                    .map(seccion -> {
                        String titulo = seccion.lines().findFirst().orElse("Guía de FinApp")
                                .replaceFirst("^#{1,3}\\s*", "").trim();
                        return new Fragmento(titulo, seccion, 0);
                    })
                    .toList();
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo cargar la guía local del asistente.", error);
        }
    }

    private double puntuar(Fragmento fragmento, List<String> terminos) {
        String texto = normalizar(fragmento.titulo() + " " + fragmento.contenido());
        long coincidencias = terminos.stream().filter(texto::contains).count();
        return (double) coincidencias / terminos.size();
    }

    private List<String> tokenizar(String texto) {
        return Arrays.stream(normalizar(texto).split("[^a-z0-9]+"))
                .filter(token -> !token.isBlank())
                .toList();
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    public record Fragmento(String titulo, String contenido, double puntuacion) {}
}
