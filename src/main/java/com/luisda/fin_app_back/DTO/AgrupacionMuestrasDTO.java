package com.luisda.fin_app_back.DTO;

import java.util.List;

public record AgrupacionMuestrasDTO(String idAgrupacion, String nombre, List<Long> ids) {}
