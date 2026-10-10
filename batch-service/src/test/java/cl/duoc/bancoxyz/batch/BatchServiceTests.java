package cl.duoc.bancoxyz.batch;

import static org.assertj.core.api.Assertions.assertThat;

import cl.duoc.bancoxyz.batch.repository.BatchAuditoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BatchServiceTests {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("reporteTransaccionesDiariasJob")
    private Job reporteTransaccionesDiariasJob;

    @Autowired
    @Qualifier("calculoInteresesMensualesJob")
    private Job calculoInteresesMensualesJob;

    @Autowired
    @Qualifier("estadosCuentaAnualesJob")
    private Job estadosCuentaAnualesJob;

    @Autowired
    private BatchAuditoriaRepository auditoriaRepository;

    @Test
    @DisplayName("Job Reporte de Transacciones Diarias procesa registros y detecta anomalias exitosamente")
    void testReporteTransaccionesDiariasJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(reporteTransaccionesDiariasJob, params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(auditoriaRepository.obtenerReportesDiarios()).isNotEmpty();
        // Verificar que detecto anomalias intencionales de los datos legacy
        long anomalias = auditoriaRepository.obtenerReportesDiarios().stream()
                .filter(cl.duoc.bancoxyz.batch.model.ReporteTransaccionDiaria::esAnomalia)
                .count();
        assertThat(anomalias).isGreaterThan(0);
    }

    @Test
    @DisplayName("Job Calculo de Intereses Mensuales liquida intereses de cuentas de ahorro y prestamos")
    void testCalculoInteresesMensualesJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(calculoInteresesMensualesJob, params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(auditoriaRepository.obtenerResultadosIntereses()).isNotEmpty();
    }

    @Test
    @DisplayName("Job Estados de Cuenta Anuales procesa movimientos para auditoria")
    void testEstadosCuentaAnualesJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(estadosCuentaAnualesJob, params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(auditoriaRepository.obtenerResumenesEstadosCuenta()).isNotEmpty();
    }
}
