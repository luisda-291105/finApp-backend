package com.luisda.fin_app_back.Services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * @uso               Cifra y descifra secretos de conexiones de almacenamiento (tokens y credenciales)
 * @capa              Servicio
 * @responsabilidades AES/GCM con clave derivada de un secreto solo disponible en el entorno backend
 * @datos             Texto plano en memoria; solo material cifrado llega a la base de datos
 * @dependencias      JCE
 * @usadoPor          SAlmacenamientoConexiones
 */
@Service
public class SAlmacenamientoCifrado {

    private static final String PREFIJO_LLAVE = "finapp:almacenamiento:v1:";
    private static final int TAMANO_NONCE = 12;
    private static final int BITS_AUTENTICACION = 128;
    private final SecureRandom random = new SecureRandom();
    private final String secreto;

    public SAlmacenamientoCifrado(
            @Value("${finapp.almacenamiento.cipher-secret:${FINAPP_ALMACENAMIENTO_CIPHER_SECRET:}}") String secretoConfigurado,
            @Value("${finapp.jwt.secret:${FINAPP_JWT_SECRET:}}") String secretoSesion) {
        this.secreto = secretoConfigurado != null && !secretoConfigurado.isBlank() ? secretoConfigurado : secretoSesion;
    }

    /** Cifra el valor con AES/GCM y devuelve nonce+ciphertext en Base64. */
    public String cifrar(String valor) throws GeneralSecurityException {
        if (valor == null) return null;
        byte[] nonce = new byte[TAMANO_NONCE];
        random.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, llave(), new GCMParameterSpec(BITS_AUTENTICACION, nonce));
        byte[] cifrado = cipher.doFinal(valor.getBytes(StandardCharsets.UTF_8));
        byte[] combinado = new byte[nonce.length + cifrado.length];
        System.arraycopy(nonce, 0, combinado, 0, nonce.length);
        System.arraycopy(cifrado, 0, combinado, nonce.length, cifrado.length);
        return Base64.getEncoder().encodeToString(combinado);
    }

    /** Descifra el material producido por cifrar. */
    public String descifrar(String material) throws GeneralSecurityException {
        if (material == null) return null;
        byte[] combinado = Base64.getDecoder().decode(material);
        byte[] nonce = Arrays.copyOfRange(combinado, 0, TAMANO_NONCE);
        byte[] cifrado = Arrays.copyOfRange(combinado, TAMANO_NONCE, combinado.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, llave(), new GCMParameterSpec(BITS_AUTENTICACION, nonce));
        return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
    }

    private SecretKeySpec llave() throws GeneralSecurityException {
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest((PREFIJO_LLAVE + secreto).getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(bytes, "AES");
    }
}
