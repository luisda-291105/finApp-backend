package com.luisda.fin_app_back.Services;

import org.springframework.stereotype.Service;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SVerificadorContrasena {
    public static final String PREFIJO = "pbkdf2$";
    private final SecureRandom random = new SecureRandom();
    public String codificar(String password) {
        byte[] salt = new byte[16]; random.nextBytes(salt);
        return PREFIJO + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash(password, salt));
    }
    public boolean verificar(String password, String encoded) {
        if (encoded == null || !encoded.startsWith(PREFIJO)) return false;
        try {
            String[] parts = encoded.split("\\$", -1);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            return MessageDigest.isEqual(Base64.getDecoder().decode(parts[2]), hash(password, salt));
        } catch (RuntimeException e) { return false; }
    }
    private byte[] hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 210_000, 256);
            try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
            finally { spec.clearPassword(); }
        } catch (Exception e) { throw new IllegalStateException("No se pudo validar la contraseña", e); }
    }
}
