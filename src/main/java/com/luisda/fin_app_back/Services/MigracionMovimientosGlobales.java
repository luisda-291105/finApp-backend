package com.luisda.fin_app_back.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Locale;

/** Actualiza la nulabilidad requerida por los movimientos globales en bases MySQL existentes. */
@Component
public class MigracionMovimientosGlobales implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigracionMovimientosGlobales.class);
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public MigracionMovimientosGlobales(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        String motor;
        try (var conexion = dataSource.getConnection()) {
            motor = conexion.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        }
        if (!motor.contains("mysql") && !motor.contains("mariadb")) return;

        Boolean nullable = jdbc.queryForObject("""
                SELECT is_nullable = 'YES' FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'movimiento_contable'
                  AND column_name = 'id_bolsillo'
                """, Boolean.class);
        if (Boolean.FALSE.equals(nullable)) {
            jdbc.execute("ALTER TABLE movimiento_contable MODIFY COLUMN id_bolsillo VARCHAR(36) NULL");
            LOGGER.info("Se habilitó id_bolsillo nulo para movimientos globales.");
        }
        jdbc.update("""
                UPDATE movimiento_contable m
                JOIN bolsillo b ON b.id_bolsillo = m.id_bolsillo
                SET m.id_usuario = b.id_usuario
                WHERE m.id_usuario IS NULL
                """);
    }
}
