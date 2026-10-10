package cl.duoc.bancoxyz.batch.listener;

import cl.duoc.bancoxyz.batch.repository.BatchAuditoriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;

import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

@Component
public class RegistroSkipListener implements SkipListener<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(RegistroSkipListener.class);
    private final BatchAuditoriaRepository auditoriaRepository;
    private final JdbcTemplate jdbcTemplate;

    public RegistroSkipListener(BatchAuditoriaRepository auditoriaRepository, DataSource dataSource) {
        this.auditoriaRepository = auditoriaRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    private void registrarEnBd(String step, String detalle, String tipoError) {
        try {
            String sql = "INSERT INTO batch_registros_rechazados (job_name, step_name, identificador_registro, datos_origen, tipo_error, motivo_rechazo) VALUES (?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(sql, "SPRING_BATCH_JOB", step, "N/A", detalle, tipoError, detalle);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onSkipInRead(Throwable t) {
        String msg = "[SKIP-READ] Registro corrupto o no parseable omitido en lectura: " + t.getMessage();
        log.warn(msg);
        auditoriaRepository.registrarOmision(msg);
        registrarEnBd("READ_STEP", msg, t.getClass().getSimpleName());
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        String msg = "[SKIP-WRITE] Error en escritura de item " + item + ": " + t.getMessage();
        log.error(msg);
        auditoriaRepository.registrarOmision(msg);
        registrarEnBd("WRITE_STEP", msg, t.getClass().getSimpleName());
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        String msg = "[SKIP-PROCESS] Item omitido en procesamiento " + item + " debido a: " + t.getMessage();
        log.warn(msg);
        auditoriaRepository.registrarOmision(msg);
        registrarEnBd("PROCESS_STEP", msg, t.getClass().getSimpleName());
    }
}
