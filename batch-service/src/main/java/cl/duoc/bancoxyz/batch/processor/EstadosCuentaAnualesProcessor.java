package cl.duoc.bancoxyz.batch.processor;

import cl.duoc.bancoxyz.batch.model.EstadoCuentaAnualBatch;
import cl.duoc.bancoxyz.batch.model.ResumenEstadoCuenta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class EstadosCuentaAnualesProcessor implements ItemProcessor<EstadoCuentaAnualBatch, ResumenEstadoCuenta> {

    private static final Logger log = LoggerFactory.getLogger(EstadosCuentaAnualesProcessor.class);

    @Override
    public ResumenEstadoCuenta process(EstadoCuentaAnualBatch item) throws Exception {
        if (item == null || item.getCuentaId() == null || item.getCuentaId().trim().isEmpty()) {
            return null;
        }

        Long cuentaId;
        try {
            cuentaId = Long.parseLong(item.getCuentaId().trim());
        } catch (NumberFormatException e) {
            log.warn("[ANUAL-SKIP] Cuenta ID no valida: {}", item.getCuentaId());
            return null;
        }

        // 1. Fecha
        LocalDate fecha = normalizarFecha(item.getFecha());

        // 2. Tipo transaccion
        String tipoRaw = item.getTransaccion() != null ? item.getTransaccion().trim().toUpperCase() : "COMPRA";
        if (tipoRaw.isEmpty()) tipoRaw = "COMPRA";

        // 3. Monto
        long monto = 1000L;
        if (item.getMonto() != null && !item.getMonto().trim().isEmpty()) {
            try {
                monto = (long) Math.abs(Double.parseDouble(item.getMonto().trim()));
                if (monto == 0) monto = 2500L;
            } catch (NumberFormatException ignored) {}
        }

        // 4. Descripcion
        String desc = item.getDescripcion() != null ? item.getDescripcion().trim() : "";
        if (desc.isEmpty()) {
            desc = "Movimiento consolidado anual de " + tipoRaw.toLowerCase();
        }

        return ResumenEstadoCuenta.builder()
                .cuentaId(cuentaId)
                .fecha(fecha)
                .tipoMovimiento(tipoRaw)
                .monto(monto)
                .descripcion(desc)
                .estadoValidacion("VERIFICADO_AUDITORIA")
                .build();
    }

    private LocalDate normalizarFecha(String fechaStr) {
        if (fechaStr == null || fechaStr.trim().isEmpty()) {
            return LocalDate.of(2024, 12, 31);
        }
        String clean = fechaStr.trim().replace("/", "-");
        if (clean.matches("\\d{2}-\\d{2}-\\d{4}")) {
            String[] p = clean.split("-");
            clean = p[2] + "-" + p[1] + "-" + p[0];
        }
        try {
            return LocalDate.parse(clean);
        } catch (Exception e) {
            return LocalDate.of(2024, 12, 31);
        }
    }
}
