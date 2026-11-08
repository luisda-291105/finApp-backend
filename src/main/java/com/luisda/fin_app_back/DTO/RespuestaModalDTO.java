package com.luisda.fin_app_back.DTO;

public record RespuestaModalDTO(String tipo, String mensaje, String campo, Object datos) {
    public static RespuestaModalDTO exito(String mensaje, Object datos) { return new RespuestaModalDTO("exito", mensaje, null, datos); }
    public static RespuestaModalDTO error(String mensaje, String campo) { return new RespuestaModalDTO("error", mensaje, campo, null); }
}
