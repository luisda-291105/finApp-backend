package com.luisda.fin_app_back.DTO;

public record GmailMovimientoAlertaDTO(
        String id,
        String idMovimiento,
        String asunto,
        String remitente,
        String fecha
) { }
