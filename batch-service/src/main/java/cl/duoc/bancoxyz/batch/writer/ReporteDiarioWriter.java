package cl.duoc.bancoxyz.batch.writer;

import cl.duoc.bancoxyz.batch.model.ReporteTransaccionDiaria;
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
public class ReporteDiarioWriter implements ItemWriter<ReporteTransaccionDiaria> {

    private static final Logger log = LoggerFactory.getLogger(ReporteDiarioWriter.class);
    private final BatchAuditoriaRepository auditoriaRepository;
    private final JdbcTemplate jdbcTemplate;

    public ReporteDiarioWriter(BatchAuditoriaRepository auditoriaRepository, DataSource dataSource) {
        this.auditoriaRepository = auditoriaRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void write(Chunk<? extends ReporteTransaccionDiaria> chunk) throws Exception {
        List<? extends ReporteTransaccionDiaria> items = chunk.getItems();
        auditoriaRepository.agregarReportesDiarios(items);

        String sql = "INSERT INTO transacciones_procesadas (transaccion_id, fecha_transaccion, monto, tipo_transaccion, es_anomala, motivo_observacion) VALUES (?, ?, ?, ?, ?, ?)";
        for (ReporteTransaccionDiaria r : items) {
            try {
                jdbcTemplate.update(sql, String.valueOf(r.getId()), r.getFecha(), r.getMontoNormalizado(), r.getTipoOperacion(), r.isEsAnomalia(), r.getDetalleAnomalia());
            } catch (Exception e) {
                log.warn("[JDBC-WRITER] Advertencia al persistir reporte diario {}: {}", r.getId(), e.getMessage());
            }
        }

        long anomalias = items.stream().filter(ReporteTransaccionDiaria::esAnomalia).count();
        long totalMonto = items.stream().mapToLong(ReporteTransaccionDiaria::getMontoNormalizado).sum();

        log.info("[BATCH-WRITER-DIARIO] Chunk procesado exitosamente. Items: {}, Anomalias rectificadas: {}, Monto total chunk: ${}",
                items.size(), anomalias, totalMonto);
    }
}
