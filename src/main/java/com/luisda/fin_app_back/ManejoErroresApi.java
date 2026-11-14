package com.luisda.fin_app_back;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * @uso               Manejo centralizado de errores de la API
 * @capa              Web
 * @responsabilidades Responder sin exponer stack traces ni detalles internos al cliente
 * @datos             Mensajes seguros al cliente, logs internos
 * @dependencias      Spring Web
 * @usadoPor          Controladores
 */
@RestControllerAdvice
public class ManejoErroresApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManejoErroresApi.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<java.util.Map<String, Object>> estado(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(java.util.Map.of(
                "mensaje", error.getReason() == null ? "La operación no se pudo completar." : error.getReason()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<java.util.Map<String, Object>> genérico(Exception error) {
        LOGGER.error("Error no controlado en la API de FinApp.", error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                "mensaje", "No se pudo completar la operación. Inténtalo de nuevo más tarde."
        ));
    }
}
