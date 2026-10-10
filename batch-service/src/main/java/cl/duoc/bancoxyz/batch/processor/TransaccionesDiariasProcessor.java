package cl.duoc.bancoxyz.batch.processor;

import cl.duoc.bancoxyz.batch.model.ReporteTransaccionDiaria;
import cl.duoc.bancoxyz.batch.model.TransaccionBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TransaccionesDiariasProcessor implements ItemProcessor<TransaccionBatch, ReporteTransaccionDiaria> {

    private static final Logger log = LoggerFactory.getLogger(TransaccionesDiariasProcessor.class);

    @Override
    public ReporteTransaccionDiaria process(TransaccionBatch item) throws Exception {
        if (item == null || item.getId() == null || item.getId().trim().isEmpty()) {
            return null; // Omitir linea vacia
        }

        Long id;
        try {
            id = Long.parseLong(item.getId().trim());
        } catch (NumberFormatException e) {
            log.warn("[ANOMALIA-ID] Registro con ID corrupto omitido: {}", item.getId());
            return null;
        }

        boolean esAnomalia = false;
        StringBuilder detalles = new StringBuilder();

        // 1. Deteccion de anomalias en monto
        long montoOriginal = 0L;
        long montoNormalizado = 0L;
        try {
            if (item.getMonto() != null && !item.getMonto().trim().isEmpty()) {
                double parsed = Double.parseDouble(item.getMonto().trim());
                montoOriginal = (long) parsed;
                if (parsed < 0) {
                    esAnomalia = true;
                    detalles.append("Monto negativo detectado ($").append(montoOriginal).append("). ");
                    montoNormalizado = Math.abs(montoOriginal);
                } else if (parsed == 0) {
                    esAnomalia = true;
                    detalles.append("Monto en cero detectado. ");
                    montoNormalizado = (id % 5 + 1) * 5000L; // Rectificacion automatica
                } else {
                    montoNormalizado = (long) parsed;
                }
            } else {
                esAnomalia = true;
                detalles.append("Monto nulo o vacio. ");
                montoNormalizado = (id % 5 + 1) * 5000L;
            }
        } catch (NumberFormatException e) {
            esAnomalia = true;
            detalles.append("Monto no numerico: '").append(item.getMonto()).append("'. ");
            montoNormalizado = 10000L;
        }

        // 2. Normalizacion de fechas legacy (formatos variados)
        LocalDate fecha = normalizarFecha(item.getFecha());
        if (item.getFecha() != null && !item.getFecha().matches("\\d{4}-\\d{2}-\\d{2}")) {
            esAnomalia = true;
            detalles.append("Formato de fecha legacy irregular ('").append(item.getFecha()).append("'). ");
        }

        // 3. Normalizacion de tipo de operacion
        String tipoRaw = item.getTipo() != null ? item.getTipo().trim().toLowerCase() : "";
        String tipoFinal;
        if ("credito".equals(tipoRaw) || "abono".equals(tipoRaw) || "deposito".equals(tipoRaw)) {
            tipoFinal = "CREDITO";
        } else if ("debito".equals(tipoRaw) || "cargo".equals(tipoRaw) || "retiro".equals(tipoRaw)) {
            tipoFinal = "DEBITO";
        } else {
            esAnomalia = true;
            detalles.append("Tipo de transaccion no estandar o invalido ('").append(tipoRaw).append("'). ");
            tipoFinal = (id % 2 == 0) ? "CREDITO" : "DEBITO"; // Heuristica de rectificacion
        }

        String estadoAuditoria = esAnomalia ? "RECTIFICADA_OBSERVADA" : "VALIDADA";

        return ReporteTransaccionDiaria.builder()
                .id(id)
                .fecha(fecha)
                .montoOriginal(montoOriginal)
                .montoNormalizado(montoNormalizado)
                .tipoOperacion(tipoFinal)
                .esAnomalia(esAnomalia)
                .detalleAnomalia(detalles.length() > 0 ? detalles.toString().trim() : "Ninguna")
                .estadoAuditoria(estadoAuditoria)
                .build();
    }

    private LocalDate normalizarFecha(String fechaStr) {
        if (fechaStr == null || fechaStr.trim().isEmpty()) {
            return LocalDate.of(2024, 6, 15);
        }
        String clean = fechaStr.trim().replace("/", "-");
        if (clean.matches("\\d{2}-\\d{2}-\\d{4}")) {
            String[] p = clean.split("-");
            clean = p[2] + "-" + p[1] + "-" + p[0];
        }
        if (clean.matches("\\d{4}-13-\\d{2}")) {
            clean = clean.replace("-13-", "-12-");
        }
        try {
            return LocalDate.parse(clean);
        } catch (Exception e) {
            return LocalDate.of(2024, 6, 15);
        }
    }
}
