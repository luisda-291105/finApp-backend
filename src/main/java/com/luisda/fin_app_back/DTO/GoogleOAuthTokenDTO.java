package com.luisda.fin_app_back.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Respuesta tipada del endpoint OAuth token de Google. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleOAuthTokenDTO(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") Long expiresIn
) { }
