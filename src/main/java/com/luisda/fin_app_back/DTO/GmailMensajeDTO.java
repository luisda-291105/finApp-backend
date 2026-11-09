package com.luisda.fin_app_back.DTO;

import java.util.List;

/** Datos de un correo guardado, listos para mostrar en la bandeja. */
public record GmailMensajeDTO(
        String id,
        String remitente,
        String destinatario,
        String asunto,
        String fecha,
        String fragmento,
        List<String> etiquetas,
        List<String> nombresEtiquetas
) { }
