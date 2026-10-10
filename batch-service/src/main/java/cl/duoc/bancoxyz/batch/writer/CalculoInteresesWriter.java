package cl.duoc.bancoxyz.batch.writer;

import cl.duoc.bancoxyz.batch.model.ResultadoInteres;
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
public class CalculoInteresesWriter implements ItemWriter<ResultadoInteres> {

    private static final Logger log = LoggerFactory.getLogger(CalculoInteresesWriter.class);
    private final BatchAuditoriaRepository auditoriaRepository;
    private final JdbcTemplate jdbcTemplate;

    public CalculoInteresesWriter(BatchAuditoriaRepository auditoriaRepository, DataSource dataSource) {
        this.auditoriaRepository = auditoriaRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void write(Chunk<? extends ResultadoInteres> chunk) throws Exception {
        List<? extends ResultadoInteres> items = chunk.getItems();
        auditoriaRepository.agregarResultadosIntereses(items);

        String sql = "INSERT INTO intereses_procesados (cuenta_id, nombre, saldo, tipo, interes_calculado, es_anomala, motivo_observacion) VALUES (?, ?, ?, ?, ?, ?, ?)";
        for (ResultadoInteres res : items) {
            try {
                jdbcTemplate.update(sql, String.valueOf(res.getCuentaId()), res.getNombreTitular(), res.getSaldoBase(), res.getTipoCuenta(), res.getMontoInteresCalculado(), false, "Calculo normal");
            } catch (Exception e) {
                log.warn("[JDBC-WRITER] Advertencia al persistir interes de cuenta {}: {}", res.getCuentaId(), e.getMessage());
            }
        }

        long totalIntereses = items.stream().mapToLong(ResultadoInteres::getMontoInteresCalculado).sum();
        log.info("[BATCH-WRITER-INTERESES] Chunk liquidado. Cuentas procesadas: {}, Intereses devengados/acreditados: ${}",
                items.size(), totalIntereses);
    }
}
