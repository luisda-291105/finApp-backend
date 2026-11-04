package com.luisda.fin_app_back.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Perfil verificado devuelto por Google OpenID Connect. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleUserInfoDTO(
        String sub,
        String email,
        String name,
        @JsonProperty("email_verified") Boolean emailVerified
) { }
