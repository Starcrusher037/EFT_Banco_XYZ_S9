package cl.duoc.bancoxyz.batch.writer;

import cl.duoc.bancoxyz.batch.model.ResumenEstadoCuenta;
import cl.duoc.bancoxyz.batch.repository.BatchAuditoriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.List;

@Component
public class EstadosCuentaWriter implements ItemWriter<ResumenEstadoCuenta> {

    private static final Logger log = LoggerFactory.getLogger(EstadosCuentaWriter.class);
    private final BatchAuditoriaRepository auditoriaRepository;
    private final JdbcTemplate jdbcTemplate;

    public EstadosCuentaWriter(BatchAuditoriaRepository auditoriaRepository, DataSource dataSource) {
        this.auditoriaRepository = auditoriaRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void write(Chunk<? extends ResumenEstadoCuenta> chunk) throws Exception {
        List<? extends ResumenEstadoCuenta> items = chunk.getItems();
        auditoriaRepository.agregarResumenesEstadosCuenta(items);

        String sql = "INSERT INTO cuentas_anuales_procesadas (cuenta_id, fecha, transaccion, monto, descripcion, es_anomala, motivo_observacion) VALUES (?, ?, ?, ?, ?, ?, ?)";
        for (ResumenEstadoCuenta ec : items) {
            try {
                jdbcTemplate.update(sql, String.valueOf(ec.getCuentaId()), ec.getFecha(), ec.getTipoMovimiento(), ec.getMonto(), ec.getDescripcion(), false, "Auditado OK");
            } catch (Exception e) {
                log.warn("[JDBC-WRITER] Advertencia al persistir resumen anual cuenta {}: {}", ec.getCuentaId(), e.getMessage());
            }
        }

        long montoTotal = items.stream().mapToLong(ResumenEstadoCuenta::getMonto).sum();
        log.info("[BATCH-WRITER-ANUAL] Chunk consolidado para auditoria. Registros: {}, Total auditado: ${}",
                items.size(), montoTotal);
    }
}
