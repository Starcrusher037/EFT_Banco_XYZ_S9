package cl.duoc.bancoxyz.batch.controller;

import cl.duoc.bancoxyz.batch.dto.JobExecutionResponseDto;
import cl.duoc.bancoxyz.batch.dto.ResumenBatchDto;
import cl.duoc.bancoxyz.batch.model.ReporteTransaccionDiaria;
import cl.duoc.bancoxyz.batch.model.ResultadoInteres;
import cl.duoc.bancoxyz.batch.model.ResumenEstadoCuenta;
import cl.duoc.bancoxyz.batch.repository.BatchAuditoriaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batch")
@Tag(name = "Spring Batch Legacy Migration", description = "Endpoints para orquestar y auditar los jobs batch del Banco XYZ")
public class BatchJobController {

    private final JobLauncher jobLauncher;
    private final Job reporteTransaccionesDiariasJob;
    private final Job calculoInteresesMensualesJob;
    private final Job estadosCuentaAnualesJob;
    private final BatchAuditoriaRepository auditoriaRepository;

    public BatchJobController(
            JobLauncher jobLauncher,
            @Qualifier("reporteTransaccionesDiariasJob") Job reporteTransaccionesDiariasJob,
            @Qualifier("calculoInteresesMensualesJob") Job calculoInteresesMensualesJob,
            @Qualifier("estadosCuentaAnualesJob") Job estadosCuentaAnualesJob,
            BatchAuditoriaRepository auditoriaRepository) {
        this.jobLauncher = jobLauncher;
        this.reporteTransaccionesDiariasJob = reporteTransaccionesDiariasJob;
        this.calculoInteresesMensualesJob = calculoInteresesMensualesJob;
        this.estadosCuentaAnualesJob = estadosCuentaAnualesJob;
        this.auditoriaRepository = auditoriaRepository;
    }

    @Operation(summary = "Ejecutar Job: Reporte de Transacciones Diarias")
    @PostMapping("/jobs/transacciones-diarias")
    public ResponseEntity<JobExecutionResponseDto> ejecutarTransaccionesDiarias() throws Exception {
        auditoriaRepository.limpiarDatosDiarios();
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("jobName", "reporteTransaccionesDiarias")
                .toJobParameters();
        JobExecution execution = jobLauncher.run(reporteTransaccionesDiariasJob, params);
        return ResponseEntity.ok(construirRespuesta(execution));
    }

    @Operation(summary = "Ejecutar Job: Calculo de Intereses Mensuales")
    @PostMapping("/jobs/calculo-intereses")
    public ResponseEntity<JobExecutionResponseDto> ejecutarCalculoIntereses() throws Exception {
        auditoriaRepository.limpiarDatosIntereses();
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("jobName", "calculoInteresesMensuales")
                .toJobParameters();
        JobExecution execution = jobLauncher.run(calculoInteresesMensualesJob, params);
        return ResponseEntity.ok(construirRespuesta(execution));
    }

    @Operation(summary = "Ejecutar Job: Estados de Cuenta Anuales para Auditoria")
    @PostMapping("/jobs/estados-cuenta-anuales")
    public ResponseEntity<JobExecutionResponseDto> ejecutarEstadosCuenta() throws Exception {
        auditoriaRepository.limpiarDatosEstadosCuenta();
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("jobName", "estadosCuentaAnuales")
                .toJobParameters();
        JobExecution execution = jobLauncher.run(estadosCuentaAnualesJob, params);
        return ResponseEntity.ok(construirRespuesta(execution));
    }

    @Operation(summary = "Ejecutar todos los procesos batch en pipeline secuencial")
    @PostMapping("/jobs/ejecutar-todos")
    public ResponseEntity<Map<String, Object>> ejecutarTodosLosJobs() throws Exception {
        Map<String, Object> resultados = new HashMap<>();

        JobExecution resTx = jobLauncher.run(reporteTransaccionesDiariasJob, new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis()).toJobParameters());
        resultados.put("transaccionesDiarias", construirRespuesta(resTx));

        JobExecution resInt = jobLauncher.run(calculoInteresesMensualesJob, new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis()).toJobParameters());
        resultados.put("calculoIntereses", construirRespuesta(resInt));

        JobExecution resAnual = jobLauncher.run(estadosCuentaAnualesJob, new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis()).toJobParameters());
        resultados.put("estadosCuentaAnuales", construirRespuesta(resAnual));

        resultados.put("mensaje", "Pipeline completo de 3 jobs batch legacy ejecutado con exito");
        return ResponseEntity.ok(resultados);
    }

    @Operation(summary = "Obtener consolidado y metricas de ejecucion de procesos batch")
    @GetMapping("/jobs/resumen")
    public ResponseEntity<ResumenBatchDto> obtenerResumen() {
        List<ReporteTransaccionDiaria> txs = auditoriaRepository.obtenerReportesDiarios();
        List<ResultadoInteres> intereses = auditoriaRepository.obtenerResultadosIntereses();
        List<ResumenEstadoCuenta> anuales = auditoriaRepository.obtenerResumenesEstadosCuenta();

        long anomalias = txs.stream().filter(ReporteTransaccionDiaria::esAnomalia).count();
        long totalMontoTx = txs.stream().mapToLong(ReporteTransaccionDiaria::getMontoNormalizado).sum();
        long totalIntereses = intereses.stream().mapToLong(ResultadoInteres::getMontoInteresCalculado).sum();

        ResumenBatchDto resumen = ResumenBatchDto.builder()
                .totalTransaccionesDiariasProcesadas(txs.size())
                .totalAnomaliasDetectadas(anomalias)
                .volumenTotalTransaccionesDiarias(totalMontoTx)
                .totalCuentasInteresesProcesadas(intereses.size())
                .totalInteresesLiquidados(totalIntereses)
                .totalMovimientosAnualesAuditados(anuales.size())
                .totalRegistrosOmitidosPorCorrupcion(auditoriaRepository.obtenerRegistrosOmitidos().size())
                .metricasUltimaEjecucion(auditoriaRepository.obtenerMetricas())
                .estadoGeneral("SISTEMA_BATCH_OPERATIVO")
                .build();

        return ResponseEntity.ok(resumen);
    }

    @Operation(summary = "Listar transacciones diarias procesadas y anomalias detectadas")
    @GetMapping("/jobs/transacciones")
    public ResponseEntity<List<ReporteTransaccionDiaria>> listarTransacciones() {
        return ResponseEntity.ok(auditoriaRepository.obtenerReportesDiarios());
    }

    @Operation(summary = "Listar calculo de intereses mensuales")
    @GetMapping("/jobs/intereses")
    public ResponseEntity<List<ResultadoInteres>> listarIntereses() {
        return ResponseEntity.ok(auditoriaRepository.obtenerResultadosIntereses());
    }

    @Operation(summary = "Listar estados de cuenta anuales auditados")
    @GetMapping("/jobs/estados-cuenta")
    public ResponseEntity<List<ResumenEstadoCuenta>> listarEstadosCuenta() {
        return ResponseEntity.ok(auditoriaRepository.obtenerResumenesEstadosCuenta());
    }

    @Operation(summary = "Consultar bitacora de lineas corruptas omitidas (Skip Policy)")
    @GetMapping("/jobs/omisiones")
    public ResponseEntity<List<String>> listarOmisiones() {
        return ResponseEntity.ok(auditoriaRepository.obtenerRegistrosOmitidos());
    }

    private JobExecutionResponseDto construirRespuesta(JobExecution execution) {
        LocalDateTime start = execution.getStartTime();
        LocalDateTime end = execution.getEndTime();
        long duracion = (start != null && end != null) ? Duration.between(start, end).toMillis() : 0L;

        return JobExecutionResponseDto.builder()
                .executionId(execution.getId())
                .jobName(execution.getJobInstance().getJobName())
                .status(execution.getStatus().toString())
                .exitCode(execution.getExitStatus().getExitCode())
                .startTime(start)
                .endTime(end)
                .duracionMs(duracion)
                .stepsEjecutados(execution.getStepExecutions().size())
                .mensaje("Job " + execution.getJobInstance().getJobName() + " finalizo con estado: " + execution.getStatus())
                .build();
    }
}
