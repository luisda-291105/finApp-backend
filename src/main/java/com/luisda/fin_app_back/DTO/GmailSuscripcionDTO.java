package com.luisda.fin_app_back.DTO;

import java.util.List;

/** Vista segura y filtrada de un remitente suscrito detectado en Gmail. */
public record GmailSuscripcionDTO(
        String id,
        String nombre,
        String correoRemitente,
        List<String> categorias,
        String urlBaja,
        String mailtoBaja,
        boolean bajaConUnClic,
        Long correosRecientes,
        String cuentaGmailId,
        String cuentaGmail
) { }
