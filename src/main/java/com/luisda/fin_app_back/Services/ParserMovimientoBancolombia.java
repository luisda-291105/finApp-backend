package com.luisda.fin_app_back.Services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ParserMovimientoBancolombia {
    private static final Pattern MONTO_ETIQUETADO = Pattern.compile(
            "(?i)(?:valor(?:\\s+de\\s+la\\s+compra|\\s+de\\s+la\\s+transacci[oó]n)?|monto(?:\\s+total)?|total(?:\\s+pagado)?|por\\s+valor\\s+de|pagaste|compraste|recibiste|retiraste|compra(?:ste)?\\s+(?:fue\\s+)?por|pago\\s+por|abono\\s+(?:por|de)|abonaron\\s+(?:por|de)|abonamos\\s+(?:por|de)|transferencia\\s+por|transacci[oó]n\\s+por|d[eé]bito\\s+por|consignaci[oó]n\\s+(?:por|de)|dep[oó]sito\\s+(?:por|de)|n[oó]mina\\s+(?:por|de))"
                    + "\\D{0,32}(?:cop\\s*)?\\$?\\s*([0-9][0-9.,]*)");
    private static final Pattern PATRON_GASTO = Pattern.compile(
            "\\b(compra|compraste|pagaste|pago|debito|debitaron|retiro|retiraste|cargo|descont|transferencia enviada|enviaste)\\b");
    private static final Pattern PATRON_INGRESO = Pattern.compile(
            "\\b(recibiste|recibimos|abono|abonaron|abonamos|consignacion|deposito|transferencia recibida|te pagaron|nomina|reembolso|devolucion|devolvieron)\\b");

    public Optional<Movimiento> extraer(String asunto, String contenido) {
        return extraer(asunto, contenido, true);
    }

    /**
     * Extrae un movimiento de un mensaje cuya dirección de remitente ya se verificó exactamente.
     * Mantiene las mismas reglas y conversión de monto que el flujo automático existente, sin exigir
     * que el cuerpo repita la marca del banco.
     */
    public Optional<Movimiento> extraerDesdeRemitenteVerificado(String asunto, String contenido) {
        return extraer(asunto, contenido, false);
    }

    private Optional<Movimiento> extraer(String asunto, String contenido, boolean exigirMarcaBanco) {
        String cuerpo = contenido == null ? "" : contenido;
        String normalizado = normalizar(cuerpo);
        if (exigirMarcaBanco && !normalizado.contains("bancolombia")) return Optional.empty();

        boolean gasto = PATRON_GASTO.matcher(normalizado).find();
        boolean ingreso = PATRON_INGRESO.matcher(normalizado).find();
        if (normalizado.matches("(?s).*\\brecibiste\\s+(?:un\\s+)?pago\\s+de\\s+nomina\\b.*")) {
            gasto = false;
            ingreso = true;
        }
        if (gasto == ingreso) return Optional.empty();

        Optional<BigDecimal> monto = extraerMonto(cuerpo);
        if (monto.isEmpty()) return Optional.empty();

        String concepto = asunto == null || asunto.isBlank() ? "Movimiento Bancolombia" : asunto.trim();
        if (concepto.length() > 160) concepto = concepto.substring(0, 160);
        String tipo = ingreso ? "INGRESO" : "GASTO";
        String categoria = ingreso ? "Ingresos"
                : normalizado.matches("(?s).*(suscrip|recurren|netflix|spotify|youtube|disney|prime video).*")
                    ? "Suscripciones" : "Compras y pagos";
        return Optional.of(new Movimiento(tipo, monto.get(), categoria, concepto));
    }

    private Optional<BigDecimal> extraerMonto(String texto) {
        Matcher etiquetado = MONTO_ETIQUETADO.matcher(texto);
        if (etiquetado.find()) return convertirMonto(etiquetado.group(1));
        return Optional.empty();
    }

    private Optional<BigDecimal> convertirMonto(String valor) {
        String numero = valor.replaceAll("\\s", "").replaceAll("[.,]+$", "");
        int ultimaComa = numero.lastIndexOf(',');
        int ultimoPunto = numero.lastIndexOf('.');
        if (ultimaComa >= 0 && ultimoPunto >= 0) {
            if (ultimaComa > ultimoPunto) numero = numero.replace(".", "").replace(',', '.');
            else numero = numero.replace(",", "");
        } else if (ultimaComa >= 0) {
            int decimales = numero.length() - ultimaComa - 1;
            numero = decimales > 0 && decimales <= 2
                    ? numero.replace(".", "").replace(',', '.') : numero.replace(",", "");
        } else if (ultimoPunto >= 0) {
            int decimales = numero.length() - ultimoPunto - 1;
            if (decimales != 2) numero = numero.replace(".", "");
        }
        try {
            BigDecimal monto = new BigDecimal(numero).setScale(2, RoundingMode.UNNECESSARY);
            return monto.signum() > 0 ? Optional.of(monto) : Optional.empty();
        } catch (NumberFormatException | ArithmeticException error) {
            return Optional.empty();
        }
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    public record Movimiento(String tipo, BigDecimal monto, String categoria, String concepto) { }
}
