package com.luisda.fin_app_back.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Locale;

@Component
public class MigracionContenidoCifradoGmail implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigracionContenidoCifradoGmail.class);
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public MigracionContenidoCifradoGmail(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        String motor;
        try (var conexion = dataSource.getConnection()) {
            motor = conexion.getMetaData().getDatabaseProductName();
        }
        if (!motor.toLowerCase(Locale.ROOT).contains("mysql")
                && !motor.toLowerCase(Locale.ROOT).contains("mariadb")) return;

        String tipoColumna;
        try {
            tipoColumna = jdbcTemplate.queryForObject("""
                    SELECT column_type FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 'gmail_mensaje'
                      AND column_name = 'contenido_cifrado'
                    """, String.class);
        } catch (EmptyResultDataAccessException error) {
            throw new IllegalStateException(
                    "No se encontró gmail_mensaje.contenido_cifrado para migrar el almacenamiento Gmail.", error);
        }

        if (tipoColumna != null && !tipoColumna.equalsIgnoreCase("longtext")) {
            jdbcTemplate.execute("ALTER TABLE gmail_mensaje MODIFY COLUMN contenido_cifrado LONGTEXT NULL");
            LOGGER.info("Se amplió gmail_mensaje.contenido_cifrado a LONGTEXT.");
        }
    }
}
