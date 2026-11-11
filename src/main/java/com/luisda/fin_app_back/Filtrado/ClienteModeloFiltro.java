package com.luisda.fin_app_back.Filtrado;

/** Puerto para solicitar al modelo una clasificación cuando las reglas no coinciden. */
@FunctionalInterface
public interface ClienteModeloFiltro {
    String clasificar(String prompt);
}
