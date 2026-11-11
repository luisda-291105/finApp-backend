package com.luisda.fin_app_back.DTO;

import java.time.LocalDate;
import java.util.List;

/** DTOs de inicio, progreso y resultado por mensaje de la importación Gmail asíncrona. */
public final class ImportacionLoteGmailDTO {
    private ImportacionLoteGmailDTO() { }

    public record Inicio(String loteId, int total) { }

    public record Estado(String loteId, int total, int completados, boolean terminado,
                         int importados, int porRevisar, int duplicados, int errores,
                         List<ResultadoMensaje> mensajes) { }

    public record ResultadoMensaje(String mensajeId, String asunto, String estado, String motivo) { }

    /** Entrada interna transitoria; el contenido descifrado solo se retiene mientras corre el lote. */
    public record Mensaje(String mensajeId, String remitente, String asunto, LocalDate fecha,
                          String contenido, String errorLectura) { }
}
