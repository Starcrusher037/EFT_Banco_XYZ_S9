package cl.duoc.bancoxyz.batch.repository;

import cl.duoc.bancoxyz.batch.model.ReporteTransaccionDiaria;
import cl.duoc.bancoxyz.batch.model.ResultadoInteres;
import cl.duoc.bancoxyz.batch.model.ResumenEstadoCuenta;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class BatchAuditoriaRepository {

    private final List<ReporteTransaccionDiaria> reportesDiarios = new CopyOnWriteArrayList<>();
    private final List<ResultadoInteres> resultadosIntereses = new CopyOnWriteArrayList<>();
    private final List<ResumenEstadoCuenta> resumenesEstadosCuenta = new CopyOnWriteArrayList<>();
    private final List<String> registrosOmitidos = new CopyOnWriteArrayList<>();
    private final Map<String, Object> metricasJobs = new ConcurrentHashMap<>();

    public void agregarReporteDiario(ReporteTransaccionDiaria item) {
        reportesDiarios.add(item);
    }

    public void agregarReportesDiarios(List<? extends ReporteTransaccionDiaria> items) {
        reportesDiarios.addAll(items);
    }

    public List<ReporteTransaccionDiaria> obtenerReportesDiarios() {
        return Collections.unmodifiableList(reportesDiarios);
    }

    public void agregarResultadoInteres(ResultadoInteres item) {
        resultadosIntereses.add(item);
    }

    public void agregarResultadosIntereses(List<? extends ResultadoInteres> items) {
        resultadosIntereses.addAll(items);
    }

    public List<ResultadoInteres> obtenerResultadosIntereses() {
        return Collections.unmodifiableList(resultadosIntereses);
    }

    public void agregarResumenEstadoCuenta(ResumenEstadoCuenta item) {
        resumenesEstadosCuenta.add(item);
    }

    public void agregarResumenesEstadosCuenta(List<? extends ResumenEstadoCuenta> items) {
        resumenesEstadosCuenta.addAll(items);
    }

    public List<ResumenEstadoCuenta> obtenerResumenesEstadosCuenta() {
        return Collections.unmodifiableList(resumenesEstadosCuenta);
    }

    public void registrarOmision(String detalle) {
        registrosOmitidos.add(detalle);
    }

    public List<String> obtenerRegistrosOmitidos() {
        return Collections.unmodifiableList(registrosOmitidos);
    }

    public void guardarMetrica(String clave, Object valor) {
        metricasJobs.put(clave, valor);
    }

    public Map<String, Object> obtenerMetricas() {
        return Collections.unmodifiableMap(metricasJobs);
    }

    public void limpiarDatosDiarios() {
        reportesDiarios.clear();
    }

    public void limpiarDatosIntereses() {
        resultadosIntereses.clear();
    }

    public void limpiarDatosEstadosCuenta() {
        resumenesEstadosCuenta.clear();
    }
}
