package com.luisda.fin_app_back;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FiltroSeguridadConfig {

    @Bean
    public FilterRegistrationBean<CabecerasSeguridadFilter> cabecerasSeguridadFilter() {
        FilterRegistrationBean<CabecerasSeguridadFilter> registro = new FilterRegistrationBean<>(new CabecerasSeguridadFilter());
        registro.addUrlPatterns("/*");
        registro.setName("cabecerasSeguridadFilter");
        registro.setOrder(1);
        return registro;
    }
}
