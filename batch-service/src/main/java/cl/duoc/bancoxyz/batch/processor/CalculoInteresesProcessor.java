package cl.duoc.bancoxyz.batch.processor;

import cl.duoc.bancoxyz.batch.model.InteresCuentaBatch;
import cl.duoc.bancoxyz.batch.model.ResultadoInteres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class CalculoInteresesProcessor implements ItemProcessor<InteresCuentaBatch, ResultadoInteres> {

    private static final Logger log = LoggerFactory.getLogger(CalculoInteresesProcessor.class);

    @Override
    public ResultadoInteres process(InteresCuentaBatch item) throws Exception {
        if (item == null || item.getCuentaId() == null || item.getCuentaId().trim().isEmpty()) {
            return null;
        }

        Long cuentaId;
        try {
            cuentaId = Long.parseLong(item.getCuentaId().trim());
        } catch (NumberFormatException e) {
            log.warn("[INTERES-SKIP] Cuenta ID no numerico: {}", item.getCuentaId());
            return null;
        }

        StringBuilder obs = new StringBuilder();

        // 1. Titular
        String nombre = item.getNombre() != null ? item.getNombre().trim() : "";
        if (nombre.isEmpty() || "Unknown".equalsIgnoreCase(nombre)) {
            nombre = "Cliente Titular " + cuentaId;
            obs.append("Nombre corregido de valor desconocido. ");
        }

        // 2. Saldo base
        long saldo = 0L;
        if (item.getSaldo() != null && !item.getSaldo().trim().isEmpty()) {
            try {
                double val = Double.parseDouble(item.getSaldo().trim());
                if (val < 0) {
                    obs.append("Saldo negativo regularizado a $0. ");
                    saldo = 0L;
                } else {
                    saldo = (long) val;
                }
            } catch (NumberFormatException e) {
                obs.append("Saldo corrupto reasignado a $0. ");
                saldo = 0L;
            }
        } else {
            obs.append("Saldo nulo regularizado a $0. ");
            saldo = 0L;
        }

        // 3. Edad
        int edad = 35;
        if (item.getEdad() != null && !item.getEdad().trim().isEmpty()) {
            try {
                int ed = Integer.parseInt(item.getEdad().trim());
                if (ed >= 18 && ed <= 95) {
                    edad = ed;
                } else {
                    obs.append("Edad fuera de rango normal (").append(ed).append("), ajustada a 35. ");
                }
            } catch (NumberFormatException e) {
                obs.append("Edad no valida, asignada edad estandar 35. ");
            }
        }

        // 4. Tipo de cuenta y calculo de tasa mensual
        String tipoRaw = item.getTipo() != null ? item.getTipo().trim().toLowerCase() : "";
        String tipoFinal;
        double tasaMensual;
        long interesCalculado;
        long nuevoSaldo;

        if (tipoRaw.contains("ahorro")) {
            tipoFinal = "CUENTA_AHORRO";
            tasaMensual = 0.038 / 12.0; // 3.8% anual
            interesCalculado = (long) Math.round(saldo * tasaMensual);
            nuevoSaldo = saldo + interesCalculado;
            obs.append("Interes de ahorro acreditado a favor del cliente. ");
        } else if (tipoRaw.contains("prestamo") || tipoRaw.contains("hipoteca")) {
            tipoFinal = tipoRaw.contains("hipoteca") ? "HIPOTECA" : "PRESTAMO";
            tasaMensual = 0.012; // 1.2% mensual de cargo por financiamiento
            interesCalculado = (long) Math.round(saldo * tasaMensual);
            nuevoSaldo = saldo + interesCalculado; // Saldo deudor incrementado
            obs.append("Interes de financiamiento devengado sobre saldo adeudado. ");
        } else {
            tipoFinal = "CUENTA_CORRIENTE";
            tasaMensual = 0.005 / 12.0; // 0.5% anual
            interesCalculado = (long) Math.round(saldo * tasaMensual);
            nuevoSaldo = saldo + interesCalculado;
            obs.append("Interes mensual estandar aplicado. ");
        }

        return ResultadoInteres.builder()
                .cuentaId(cuentaId)
                .nombreTitular(nombre)
                .saldoBase(saldo)
                .edad(edad)
                .tipoCuenta(tipoFinal)
                .tasaInteresMensual(Math.round(tasaMensual * 10000.0) / 100.0) // En porcentaje
                .montoInteresCalculado(interesCalculado)
                .nuevoSaldoProyectado(nuevoSaldo)
                .observaciones(obs.length() > 0 ? obs.toString().trim() : "Calculo regular exitoso")
                .build();
    }
}
