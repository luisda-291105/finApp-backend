package com.luisda.fin_app_back.Seguridad;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @uso               Resolver la IP real del cliente detrás de Cloudflare Tunnel / proxies
 * @capa              Seguridad
 * @responsabilidades Leer CF-Connecting-IP y X-Forwarded-For solo cuando el peer es de confianza
 * @datos             finapp.seguridad.proxies-confiables (CIDR), cabeceras de la petición
 * @dependencias      Servlet API
 * @usadoPor          CUsuario, SRefreshFamilias
 */
@Component
public class IpCliente {

    private final List<String> proxiesConfiables;

    public IpCliente(@Value("${finapp.seguridad.proxies-confiables:127.0.0.1/32,::1/128}")
                     String proxiesConfiables) {
        this.proxiesConfiables = new ArrayList<>();
        if (proxiesConfiables != null) {
            for (String entrada : proxiesConfiables.split(",")) {
                String normalizada = entrada.trim().toLowerCase(Locale.ROOT);
                if (!normalizada.isEmpty()) this.proxiesConfiables.add(normalizada);
            }
        }
    }

    /**
     * Preferencia: CF-Connecting-IP → X-Forwarded-For → X-Real-IP → remoteAddr.
     * Si el peer no está en la lista de proxies de confianza, se ignora cualquier cabecera
     * reenviada: así un cliente que alcance el puerto directamente no puede falsificar la IP.
     */
    public String resolver(HttpServletRequest solicitud) {
        String remoto = ipLiteral(solicitud.getRemoteAddr());
        if (remoto == null) return "ip-desconocida";
        if (!esProxyConfiable(remoto)) return remoto;

        String cf = ipLiteral(solicitud.getHeader("CF-Connecting-IP"));
        if (cf != null) return cf;

        String reenviado = solicitud.getHeader("X-Forwarded-For");
        if (reenviado != null) {
            for (String parte : reenviado.split(",")) {
                String ip = ipLiteral(parte);
                if (ip != null) return ip;
            }
        }

        String real = ipLiteral(solicitud.getHeader("X-Real-IP"));
        return real != null ? real : remoto;
    }

    /** Acepta solo literales IP válidos; nunca hostnames (evita resolución DNS del atacante). */
    private String ipLiteral(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim();
        if (limpio.isEmpty() || limpio.length() > 45) return null;
        for (int i = 0; i < limpio.length(); i++) {
            char c = limpio.charAt(i);
            boolean permitido = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F') || c == ':' || c == '.' || c == '%' || c == '/';
            if (!permitido) return null;
        }
        String sinZona = limpio.contains("%") ? limpio.substring(0, limpio.indexOf('%')) : limpio;
        try {
            InetAddress direccion = InetAddress.getByName(sinZona);
            return direccion.getHostAddress();
        } catch (UnknownHostException error) {
            return null;
        }
    }

    private boolean esProxyConfiable(String ip) {
        for (String red : proxiesConfiables) {
            if (coincide(ip, red)) return true;
        }
        return false;
    }

    private boolean coincide(String ip, String red) {
        String[] partes = red.split("/");
        byte[] ipBytes = bytes(ip);
        byte[] redBytes = bytes(partes[0]);
        if (ipBytes == null || redBytes == null || ipBytes.length != redBytes.length) return false;

        int bits = partes.length == 1 ? ipBytes.length * 8 : parseInt(partes[1], ipBytes.length * 8);
        int bytesCompletos = bits / 8;
        int bitsParciales = bits % 8;
        for (int i = 0; i < bytesCompletos; i++) {
            if (ipBytes[i] != redBytes[i]) return false;
        }
        if (bitsParciales == 0) return true;
        int mascara = 0xFF << (8 - bitsParciales);
        return (ipBytes[bytesCompletos] & mascara) == (redBytes[bytesCompletos] & mascara);
    }

    private byte[] bytes(String ip) {
        String limpio = ip.contains("%") ? ip.substring(0, ip.indexOf('%')) : ip;
        try {
            return InetAddress.getByName(limpio).getAddress();
        } catch (UnknownHostException error) {
            return null;
        }
    }

    private int parseInt(String valor, int porDefecto) {
        try {
            int numero = Integer.parseInt(valor);
            return numero < 0 || numero > porDefecto ? porDefecto : numero;
        } catch (NumberFormatException error) {
            return porDefecto;
        }
    }
}
