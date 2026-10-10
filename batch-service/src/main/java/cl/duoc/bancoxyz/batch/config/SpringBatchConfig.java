package cl.duoc.bancoxyz.batch.config;

import cl.duoc.bancoxyz.batch.listener.AuditoriaJobExecutionListener;
import cl.duoc.bancoxyz.batch.listener.RegistroSkipListener;
import cl.duoc.bancoxyz.batch.model.*;
import cl.duoc.bancoxyz.batch.processor.CalculoInteresesProcessor;
import cl.duoc.bancoxyz.batch.processor.EstadosCuentaAnualesProcessor;
import cl.duoc.bancoxyz.batch.processor.TransaccionesDiariasProcessor;
import cl.duoc.bancoxyz.batch.writer.CalculoInteresesWriter;
import cl.duoc.bancoxyz.batch.writer.EstadosCuentaWriter;
import cl.duoc.bancoxyz.batch.writer.ReporteDiarioWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.ConnectException;

@Configuration
public class SpringBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AuditoriaJobExecutionListener auditoriaListener;
    private final RegistroSkipListener skipListener;
    private final TaskExecutor taskExecutor;

    public SpringBatchConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            AuditoriaJobExecutionListener auditoriaListener,
            RegistroSkipListener skipListener,
            @Qualifier("batchTaskExecutor") TaskExecutor taskExecutor) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.auditoriaListener = auditoriaListener;
        this.skipListener = skipListener;
        this.taskExecutor = taskExecutor;
    }

    @Value("${app.batch.input-file:data/movimientos_financieros_diarios.csv}")
    private String rutaTransacciones;

    @Value("${app.batch.intereses-file:data/intereses_trimestrales.csv}")
    private String rutaIntereses;

    @Value("${app.batch.cuentas-anuales-file:data/estados_financieros_anuales.csv}")
    private String rutaCuentasAnuales;

    private org.springframework.core.io.Resource obtenerRecurso(String path) {
        if (path.startsWith("/") || path.contains(":/") || path.contains(":\\")) {
            return new org.springframework.core.io.FileSystemResource(path);
        }
        return new ClassPathResource(path);
    }

    // =========================================================================
    // 1. PROCESO BATCH: REPORTE DE TRANSACCIONES DIARIAS
    // =========================================================================
    @Bean
    public FlatFileItemReader<TransaccionBatch> readerTransaccionesDiarias() {
        return new FlatFileItemReaderBuilder<TransaccionBatch>()
                .name("readerTransaccionesDiarias")
                .resource(obtenerRecurso(rutaTransacciones))
                .delimited()
                .names("id", "fecha", "monto", "tipo")
                .linesToSkip(1)
                .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                    setTargetType(TransaccionBatch.class);
                }})
                .build();
    }

    @Bean
    public Step stepTransaccionesDiarias(
            TransaccionesDiariasProcessor processor,
            ReporteDiarioWriter writer) {
        return new StepBuilder("stepTransaccionesDiarias", jobRepository)
                .<TransaccionBatch, ReporteTransaccionDiaria>chunk(50, transactionManager)
                .reader(readerTransaccionesDiarias())
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retryLimit(3)
                .retry(ConnectException.class)
                .retry(Exception.class)
                .skipLimit(100)
                .skip(FlatFileParseException.class)
                .skip(IllegalArgumentException.class)
                .listener(skipListener)
                .build();
    }

    @Bean
    public Job reporteTransaccionesDiariasJob(Step stepTransaccionesDiarias) {
        return new JobBuilder("reporteTransaccionesDiariasJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(auditoriaListener)
                .start(stepTransaccionesDiarias)
                .build();
    }

    // =========================================================================
    // 2. PROCESO BATCH: CALCULO DE INTERESES MENSUALES
    // =========================================================================
    @Bean
    public FlatFileItemReader<InteresCuentaBatch> readerCalculoIntereses() {
        return new FlatFileItemReaderBuilder<InteresCuentaBatch>()
                .name("readerCalculoIntereses")
                .resource(obtenerRecurso(rutaIntereses))
                .delimited()
                .names("cuentaId", "nombre", "saldo", "edad", "tipo")
                .linesToSkip(1)
                .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                    setTargetType(InteresCuentaBatch.class);
                }})
                .build();
    }

    @Bean
    public Step stepCalculoIntereses(
            CalculoInteresesProcessor processor,
            CalculoInteresesWriter writer) {
        return new StepBuilder("stepCalculoIntereses", jobRepository)
                .<InteresCuentaBatch, ResultadoInteres>chunk(50, transactionManager)
                .reader(readerCalculoIntereses())
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retryLimit(3)
                .retry(ConnectException.class)
                .retry(Exception.class)
                .skipLimit(100)
                .skip(FlatFileParseException.class)
                .skip(IllegalArgumentException.class)
                .listener(skipListener)
                .build();
    }

    @Bean
    public Job calculoInteresesMensualesJob(Step stepCalculoIntereses) {
        return new JobBuilder("calculoInteresesMensualesJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(auditoriaListener)
                .start(stepCalculoIntereses)
                .build();
    }

    // =========================================================================
    // 3. PROCESO BATCH: ESTADOS DE CUENTA ANUALES PARA AUDITORIA
    // =========================================================================
    @Bean
    public FlatFileItemReader<EstadoCuentaAnualBatch> readerEstadosCuenta() {
        return new FlatFileItemReaderBuilder<EstadoCuentaAnualBatch>()
                .name("readerEstadosCuenta")
                .resource(obtenerRecurso(rutaCuentasAnuales))
                .delimited()
                .names("cuentaId", "fecha", "transaccion", "monto", "descripcion")
                .linesToSkip(1)
                .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                    setTargetType(EstadoCuentaAnualBatch.class);
                }})
                .build();
    }

    @Bean
    public Step stepEstadosCuentaAnuales(
            EstadosCuentaAnualesProcessor processor,
            EstadosCuentaWriter writer) {
        return new StepBuilder("stepEstadosCuentaAnuales", jobRepository)
                .<EstadoCuentaAnualBatch, ResumenEstadoCuenta>chunk(50, transactionManager)
                .reader(readerEstadosCuenta())
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retryLimit(3)
                .retry(ConnectException.class)
                .retry(Exception.class)
                .skipLimit(100)
                .skip(FlatFileParseException.class)
                .skip(IllegalArgumentException.class)
                .listener(skipListener)
                .build();
    }

    @Bean
    public Job estadosCuentaAnualesJob(Step stepEstadosCuentaAnuales) {
        return new JobBuilder("estadosCuentaAnualesJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(auditoriaListener)
                .start(stepEstadosCuentaAnuales)
                .build();
    }
}
