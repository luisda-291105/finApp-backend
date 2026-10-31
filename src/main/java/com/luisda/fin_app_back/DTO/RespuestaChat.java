package com.luisda.fin_app_back.DTO;

import java.util.List;

/** Respuesta generada con sus referencias y avisos de disponibilidad. */
public record RespuestaChat(
        String respuesta,
        List<FuenteChat> fuentes,
        boolean busquedaWebUsada,
        String aviso
) {}
