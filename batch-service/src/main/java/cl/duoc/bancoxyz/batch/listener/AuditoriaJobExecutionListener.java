package cl.duoc.bancoxyz.batch.listener;

import cl.duoc.bancoxyz.batch.repository.BatchAuditoriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class AuditoriaJobExecutionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaJobExecutionListener.class);
    private final BatchAuditoriaRepository auditoriaRepository;

    public AuditoriaJobExecutionListener(BatchAuditoriaRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        log.info("[BATCH-INICIO] Iniciando Job '{}' con ID de Ejecucion: {}", jobName, jobExecution.getId());
        auditoriaRepository.guardarMetrica(jobName + "_ultimoInicio", LocalDateTime.now().toString());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();
        LocalDateTime start = jobExecution.getStartTime();
        LocalDateTime end = jobExecution.getEndTime();
        long duracionMs = (start != null && end != null) ? Duration.between(start, end).toMillis() : 0L;

        log.info("[BATCH-FIN] Job '{}' finalizado con estado '{}'. Duracion: {} ms. Total Steps ejecutados: {}",
                jobName, status, duracionMs, jobExecution.getStepExecutions().size());

        auditoriaRepository.guardarMetrica(jobName + "_ultimoEstado", status.toString());
        auditoriaRepository.guardarMetrica(jobName + "_ultimaDuracionMs", duracionMs);
        auditoriaRepository.guardarMetrica(jobName + "_ultimoFin", LocalDateTime.now().toString());

        if (status == BatchStatus.FAILED) {
            log.error("[BATCH-ALERTA] Fallo critico detectado en Job '{}'. Registrando excepciones para reejecucion automatica...", jobName);
            jobExecution.getAllFailureExceptions().forEach(ex ->
                    log.error("[BATCH-ERROR-DETALLE] Causa: {}", ex.getMessage())
            );
        }
    }
}
