package cl.duoc.bancoxyz.core.saga;

import cl.duoc.bancoxyz.core.mensajeria.EventoTransaccion;
import cl.duoc.bancoxyz.core.mensajeria.PublicadorTransacciones;
import cl.duoc.bancoxyz.core.model.Cuenta;
import cl.duoc.bancoxyz.core.model.Transaccion;
import cl.duoc.bancoxyz.core.repository.BancoRepository;
import cl.duoc.bancoxyz.core.service.BancoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquestador del Patrón Saga para Transferencias Bancarias Distribuidas.
 * Coordina transacciones locales paso a paso y ejecuta transacciones de compensación
 * automáticas en caso de fallos en pasos intermedios.
 */
@Service
public class OrquestadorSagaTransferencia {

    private static final Logger log = LoggerFactory.getLogger(OrquestadorSagaTransferencia.class);

    private final BancoService bancoService;
    private final BancoRepository bancoRepository;
    private final PublicadorTransacciones publicadorTransacciones;

    // Bitácora en memoria de ejecuciones de Saga
    private final Map<String, RegistroSaga> historialSagas = new ConcurrentHashMap<>();

    public OrquestadorSagaTransferencia(
            BancoService bancoService,
            BancoRepository bancoRepository,
            PublicadorTransacciones publicadorTransacciones) {
        this.bancoService = bancoService;
        this.bancoRepository = bancoRepository;
        this.publicadorTransacciones = publicadorTransacciones;
    }

    /**
     * Ejecuta el flujo orquestado de la Saga de Transferencia.
     */
    public synchronized RegistroSaga ejecutarSaga(SolicitudSagaTransferencia solicitud) {
        String sagaId = "SAGA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        RegistroSaga registro = RegistroSaga.builder()
                .sagaId(sagaId)
                .cuentaOrigenId(solicitud.getCuentaOrigenId())
                .cuentaDestinoId(solicitud.getCuentaDestinoId())
                .monto(solicitud.getMonto())
                .estado(EstadoSaga.INICIADA)
                .fechaInicio(LocalDateTime.now())
                .build();

        registro.agregarPaso("Saga iniciada para transferencia de $" + solicitud.getMonto() +
                " desde cuenta " + solicitud.getCuentaOrigenId() + " hacia cuenta " + solicitud.getCuentaDestinoId());

        historialSagas.put(sagaId, registro);

        Cuenta origen;
        try {
            origen = bancoService.obtenerCuentaPorId(solicitud.getCuentaOrigenId());
        } catch (Exception ex) {
            registro.setEstado(EstadoSaga.FALLIDA);
            registro.agregarPaso("ERROR PREVIO: Cuenta origen " + solicitud.getCuentaOrigenId() + " no encontrada.");
            registro.setMensajeResultado("Fallo al iniciar Saga: Cuenta origen no existe.");
            registro.setFechaFin(LocalDateTime.now());
            return registro;
        }

        // Validación inicial de saldo y sobregiro disponible
        long disponible = origen.getSaldoContable() + (origen.getLineaSobregiro() != null ? origen.getLineaSobregiro() : 0L);
        if (solicitud.getMonto() > disponible) {
            registro.setEstado(EstadoSaga.FALLIDA);
            registro.agregarPaso("ERROR PREVIO: Saldo disponible insuficiente ($" + disponible + " disponible vs $" + solicitud.getMonto() + " solicitado)");
            registro.setMensajeResultado("Transferencia rechazada: Saldo insuficiente en cuenta origen.");
            registro.setSaldoFinalOrigen(origen.getSaldoContable());
            registro.setFechaFin(LocalDateTime.now());
            return registro;
        }

        // =====================================================================
        // PASO 1: Transacción Local 1 -> Débito en Cuenta Origen
        // =====================================================================
        log.info("[SAGA {}] Ejecutando PASO 1: Débito en cuenta origen {}", sagaId, origen.getCuentaId());
        origen.setSaldoContable(origen.getSaldoContable() - solicitud.getMonto());
        bancoRepository.guardarCuenta(origen);

        Transaccion txDebito = new Transaccion(
                null,
                origen.getCuentaId(),
                LocalDate.now(),
                solicitud.getMonto(),
                "debito",
                "Saga [" + sagaId + "] Débito transferencia hacia cuenta " + solicitud.getCuentaDestinoId(),
                "SAGA-ORQUESTADOR"
        );
        bancoRepository.guardarTransaccion(txDebito);

        registro.setEstado(EstadoSaga.DEBITO_ORIGEN_EXITOSO);
        registro.agregarPaso("PASO 1 EXITOSO: Débito de $" + solicitud.getMonto() +
                " completado en cuenta origen. Nuevo saldo contable: $" + origen.getSaldoContable());

        // =====================================================================
        // PASO 2: Transacción Local 2 -> Abono en Cuenta Destino
        // =====================================================================
        log.info("[SAGA {}] Ejecutando PASO 2: Acreditación en cuenta destino {}", sagaId, solicitud.getCuentaDestinoId());
        boolean paso2Exitoso = false;
        String causaFallo = null;

        try {
            // Verificación de simulación forzada de fallo (para fines de pruebas de compensación)
            if (Boolean.TRUE.equals(solicitud.getSimularFalloDestino())) {
                throw new IllegalStateException("Simulación pedagógica: Rechazo de acreditación por cuenta destino no disponible / timeout interbancario.");
            }

            Cuenta destino = bancoService.obtenerCuentaPorId(solicitud.getCuentaDestinoId());
            if ("CERRADA".equalsIgnoreCase(destino.getEstado()) || "BLOQUEADA".equalsIgnoreCase(destino.getEstado())) {
                throw new IllegalStateException("La cuenta destino se encuentra inactiva (Estado: " + destino.getEstado() + ")");
            }

            // Si es válida, abonar fondos
            destino.setSaldoContable(destino.getSaldoContable() + solicitud.getMonto());
            bancoRepository.guardarCuenta(destino);

            Transaccion txCredito = new Transaccion(
                    null,
                    destino.getCuentaId(),
                    LocalDate.now(),
                    solicitud.getMonto(),
                    "credito",
                    "Saga [" + sagaId + "] Abono recibido desde cuenta " + origen.getCuentaId(),
                    "SAGA-ORQUESTADOR"
            );
            bancoRepository.guardarTransaccion(txCredito);

            paso2Exitoso = true;
            registro.setEstado(EstadoSaga.COMPLETADA);
            registro.setSaldoFinalOrigen(origen.getSaldoContable());
            registro.setSaldoFinalDestino(destino.getSaldoContable());
            registro.agregarPaso("PASO 2 EXITOSO: Abono de $" + solicitud.getMonto() +
                    " acreditado en cuenta destino. Nuevo saldo destino: $" + destino.getSaldoContable());
            registro.setMensajeResultado("Transferencia Saga completada exitosamente sin requerir compensación.");

            // Notificación JMS
            publicarEvento(sagaId, origen.getCuentaId(), destino.getCuentaId(), solicitud.getMonto(), "COMPLETADA", "Transferencia Saga exitosa");

        } catch (Exception ex) {
            causaFallo = ex.getMessage();
            log.warn("[SAGA {}] Falla detectada en PASO 2: {}", sagaId, causaFallo);
            registro.agregarPaso("FALLO EN PASO 2: " + causaFallo);
        }

        // =====================================================================
        // PASO 3 (TRANSACCIÓN DE COMPENSACIÓN): Se activa ante fallo en Paso 2
        // =====================================================================
        if (!paso2Exitoso) {
            log.warn("[SAGA {}] ACTIVANDO TRANSACCIÓN DE COMPENSACIÓN AUTOMÁTICA", sagaId);
            registro.setEstado(EstadoSaga.COMPENSACION_INICIADA);
            registro.agregarPaso("PASO 3 (COMPENSACIÓN): Revirtiendo débito en cuenta origen...");

            // Restitución íntegra de fondos debitados a la cuenta de origen
            origen.setSaldoContable(origen.getSaldoContable() + solicitud.getMonto());
            bancoRepository.guardarCuenta(origen);

            Transaccion txCompensacion = new Transaccion(
                    null,
                    origen.getCuentaId(),
                    LocalDate.now(),
                    solicitud.getMonto(),
                    "credito",
                    "Saga [" + sagaId + "] COMPENSACIÓN: Devolución de fondos por fallo en abono (" + causaFallo + ")",
                    "SAGA-COMPENSADOR"
            );
            bancoRepository.guardarTransaccion(txCompensacion);

            registro.setEstado(EstadoSaga.COMPENSADA);
            registro.setSaldoFinalOrigen(origen.getSaldoContable());
            registro.agregarPaso("COMPENSACIÓN COMPLETADA: Fondos de $" + solicitud.getMonto() +
                    " restituidos íntegramente a cuenta origen. Saldo final restaurado: $" + origen.getSaldoContable());
            registro.setMensajeResultado("La transferencia falló en la cuenta destino (" + causaFallo +
                    "). La transacción de compensación fue ejecutada y los fondos fueron restituidos a la cuenta origen.");

            // Notificación JMS de compensación
            publicarEvento(sagaId, origen.getCuentaId(), solicitud.getCuentaDestinoId(), solicitud.getMonto(), "COMPENSADA",
                    "Transferencia compensada tras fallo: " + causaFallo);
        }

        registro.setFechaFin(LocalDateTime.now());
        return registro;
    }

    public List<RegistroSaga> obtenerHistorialSagas() {
        return new ArrayList<>(historialSagas.values());
    }

    public RegistroSaga obtenerSagaPorId(String sagaId) {
        return historialSagas.get(sagaId);
    }

    private void publicarEvento(String sagaId, Long origenId, Long destinoId, Long monto, String estado, String detalle) {
        EventoTransaccion evento = EventoTransaccion.builder()
                .transaccionId(sagaId)
                .cuentaOrigenId(origenId)
                .cuentaDestinoId(destinoId)
                .monto(monto)
                .tipoOperacion("SAGA_TRANSFERENCIA")
                .canal("SAGA")
                .estado(estado)
                .fechaHora(LocalDateTime.now().toString())
                .detalle(detalle)
                .build();
        publicadorTransacciones.publicarTransaccion(evento);
    }
}
