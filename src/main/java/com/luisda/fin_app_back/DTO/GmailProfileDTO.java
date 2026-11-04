package com.luisda.fin_app_back.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Perfil de Gmail usado para identificar la cuenta conectada. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GmailProfileDTO(@JsonProperty("emailAddress") String emailAddress) { }
