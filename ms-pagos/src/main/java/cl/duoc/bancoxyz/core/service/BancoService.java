package cl.duoc.bancoxyz.core.service;

import cl.duoc.bancoxyz.core.model.Cuenta;
import cl.duoc.bancoxyz.core.model.MovimientoAnual;
import cl.duoc.bancoxyz.core.model.Transaccion;
import cl.duoc.bancoxyz.core.repository.BancoRepository;
import cl.duoc.bancoxyz.core.mensajeria.EventoTransaccion;
import cl.duoc.bancoxyz.core.mensajeria.PublicadorTransacciones;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class BancoService {

    private final BancoRepository bancoRepository;
    private final PublicadorTransacciones publicadorTransacciones;

    public BancoService(BancoRepository bancoRepository, PublicadorTransacciones publicadorTransacciones) {
        this.bancoRepository = bancoRepository;
        this.publicadorTransacciones = publicadorTransacciones;
    }

    public Cuenta obtenerCuentaPorId(Long cuentaId) {
        return bancoRepository.buscarCuentaPorId(cuentaId)
                .orElseThrow(() -> new NoSuchElementException("No se encontró la cuenta con ID: " + cuentaId));
    }

    public List<Cuenta> obtenerTodasLasCuentas() {
        return bancoRepository.obtenerTodasLasCuentas();
    }

    public List<Transaccion> obtenerTransaccionesPorCuenta(Long cuentaId) {
        obtenerCuentaPorId(cuentaId);
        return bancoRepository.buscarTransaccionesPorCuenta(cuentaId);
    }

    public List<MovimientoAnual> obtenerMovimientosAnualesPorCuenta(Long cuentaId) {
        obtenerCuentaPorId(cuentaId);
        return bancoRepository.buscarMovimientosAnualesPorCuenta(cuentaId);
    }

    public synchronized Cuenta ejecutarRetiro(Long cuentaId, Long monto, String canal) {
        Cuenta cuenta = obtenerCuentaPorId(cuentaId);
        long saldoTotalDisponible = cuenta.getSaldoContable() + cuenta.getLineaSobregiro();

        if (monto > saldoTotalDisponible) {
            throw new IllegalArgumentException("Saldo insuficiente para efectuar el retiro");
        }

        cuenta.setSaldoContable(cuenta.getSaldoContable() - monto);
        bancoRepository.guardarCuenta(cuenta);

        Transaccion tx = new Transaccion(
                null,
                cuentaId,
                LocalDate.now(),
                monto,
                "debito",
                "Giro transaccional en " + canal,
                canal
        );
        bancoRepository.guardarTransaccion(tx);

        // Publicacion Asincrona del Evento Transaccional a ActiveMQ con tolerancia a fallos
        EventoTransaccion evento = EventoTransaccion.builder()
                .transaccionId("TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .cuentaOrigenId(cuentaId)
                .cuentaDestinoId(null)
                .monto(monto)
                .tipoOperacion("RETIRO")
                .canal(canal != null ? canal : "ATM")
                .estado("EXITOSA")
                .fechaHora(LocalDateTime.now().toString())
                .detalle("Giro por canal " + canal + " completado exitosamente")
                .build();
        publicadorTransacciones.publicarTransaccion(evento);

        return cuenta;
    }

    public synchronized void ejecutarTransferencia(Long cuentaOrigenId, Long cuentaDestinoId, Long monto, String comentario) {
        Cuenta origen = obtenerCuentaPorId(cuentaOrigenId);
        Cuenta destino = obtenerCuentaPorId(cuentaDestinoId);

        long disponible = origen.getSaldoContable() + origen.getLineaSobregiro();
        if (monto > disponible) {
            throw new IllegalArgumentException("Saldo insuficiente en cuenta origen");
        }

        origen.setSaldoContable(origen.getSaldoContable() - monto);
        destino.setSaldoContable(destino.getSaldoContable() + monto);

        bancoRepository.guardarCuenta(origen);
        bancoRepository.guardarCuenta(destino);

        Transaccion cargo = new Transaccion(null, cuentaOrigenId, LocalDate.now(), monto, "debito",
                "Transferencia a cta " + cuentaDestinoId + ": " + (comentario != null ? comentario : "Traspaso"), "MOVIL");
        Transaccion abono = new Transaccion(null, cuentaDestinoId, LocalDate.now(), monto, "credito",
                "Transferencia de cta " + cuentaOrigenId + ": " + (comentario != null ? comentario : "Abono"), "MOVIL");

        bancoRepository.guardarTransaccion(cargo);
        bancoRepository.guardarTransaccion(abono);

        // Publicacion Asincrona del Evento Transaccional a ActiveMQ con tolerancia a fallos
        EventoTransaccion evento = EventoTransaccion.builder()
                .transaccionId("TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .cuentaOrigenId(cuentaOrigenId)
                .cuentaDestinoId(cuentaDestinoId)
                .monto(monto)
                .tipoOperacion("TRANSFERENCIA")
                .canal("MOVIL")
                .estado("EXITOSA")
                .fechaHora(LocalDateTime.now().toString())
                .detalle(comentario != null ? comentario : "Transferencia de fondos exitosa")
                .build();
        publicadorTransacciones.publicarTransaccion(evento);
    }

    // =========================================================================
    // 1. GESTION DE CUENTAS (Apertura, Mantenimiento, Cierre)
    // =========================================================================
    public synchronized Cuenta crearCuenta(Cuenta nuevaCuenta) {
        if (nuevaCuenta.getCuentaId() == null) {
            nuevaCuenta.setCuentaId(bancoRepository.generarSiguienteIdCuenta());
        }
        if (nuevaCuenta.getSaldoContable() == null) {
            nuevaCuenta.setSaldoContable(0L);
        }
        if (nuevaCuenta.getEstado() == null) {
            nuevaCuenta.setEstado("ACTIVA");
        }
        if (nuevaCuenta.getLineaSobregiro() == null) {
            nuevaCuenta.setLineaSobregiro("cuenta_corriente".equalsIgnoreCase(nuevaCuenta.getTipoCuenta()) ? 300000L : 0L);
        }
        if (nuevaCuenta.getTasaInteresAnual() == null) {
            nuevaCuenta.setTasaInteresAnual("cuenta_ahorro".equalsIgnoreCase(nuevaCuenta.getTipoCuenta()) ? 3.8 : 0.5);
        }
        bancoRepository.guardarCuenta(nuevaCuenta);

        // Registro de transaccion inicial si tiene saldo
        if (nuevaCuenta.getSaldoContable() > 0) {
            Transaccion tx = new Transaccion(
                    null,
                    nuevaCuenta.getCuentaId(),
                    LocalDate.now(),
                    nuevaCuenta.getSaldoContable(),
                    "credito",
                    "Apertura de cuenta y deposito inicial",
                    "SUCURSAL"
            );
            bancoRepository.guardarTransaccion(tx);
        }

        return nuevaCuenta;
    }

    public synchronized Cuenta actualizarCuenta(Long cuentaId, Cuenta datosActualizados) {
        Cuenta existente = obtenerCuentaPorId(cuentaId);
        if (datosActualizados.getNombreTitular() != null && !datosActualizados.getNombreTitular().isBlank()) {
            existente.setNombreTitular(datosActualizados.getNombreTitular());
        }
        if (datosActualizados.getEdadTitular() != null) {
            existente.setEdadTitular(datosActualizados.getEdadTitular());
        }
        if (datosActualizados.getTipoCuenta() != null) {
            existente.setTipoCuenta(datosActualizados.getTipoCuenta());
        }
        if (datosActualizados.getLineaSobregiro() != null) {
            existente.setLineaSobregiro(datosActualizados.getLineaSobregiro());
        }
        if (datosActualizados.getTasaInteresAnual() != null) {
            existente.setTasaInteresAnual(datosActualizados.getTasaInteresAnual());
        }
        if (datosActualizados.getEstado() != null) {
            existente.setEstado(datosActualizados.getEstado());
        }
        bancoRepository.guardarCuenta(existente);
        return existente;
    }

    public synchronized boolean cerrarCuenta(Long cuentaId) {
        Cuenta cuenta = obtenerCuentaPorId(cuentaId);
        cuenta.setEstado("CERRADA");
        bancoRepository.guardarCuenta(cuenta);
        return true;
    }

    // =========================================================================
    // 2. PROCESAMIENTO DE PAGOS Y DEPOSITOS
    // =========================================================================
    public synchronized Cuenta ejecutarDeposito(Long cuentaId, Long monto, String canal, String detalle) {
        if (monto == null || monto <= 0) {
            throw new IllegalArgumentException("El monto a depositar debe ser mayor a $0");
        }
        Cuenta cuenta = obtenerCuentaPorId(cuentaId);
        if ("CERRADA".equalsIgnoreCase(cuenta.getEstado())) {
            throw new IllegalStateException("No se pueden realizar depositos en una cuenta cerrada");
        }

        cuenta.setSaldoContable(cuenta.getSaldoContable() + monto);
        bancoRepository.guardarCuenta(cuenta);

        String canalFinal = canal != null ? canal : "WEB";
        Transaccion tx = new Transaccion(
                null,
                cuentaId,
                LocalDate.now(),
                monto,
                "credito",
                detalle != null ? detalle : "Deposito de fondos en cuenta",
                canalFinal
        );
        bancoRepository.guardarTransaccion(tx);

        EventoTransaccion evento = EventoTransaccion.builder()
                .transaccionId("TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .cuentaOrigenId(null)
                .cuentaDestinoId(cuentaId)
                .monto(monto)
                .tipoOperacion("DEPOSITO")
                .canal(canalFinal)
                .estado("EXITOSA")
                .fechaHora(LocalDateTime.now().toString())
                .detalle(detalle != null ? detalle : "Deposito bancario procesado con exito")
                .build();
        publicadorTransacciones.publicarTransaccion(evento);

        return cuenta;
    }

    // =========================================================================
    // 3. GESTION DE CLIENTES (Consulta y Actualizacion de Perfil)
    // =========================================================================
    public List<java.util.Map<String, Object>> obtenerTodosLosClientes() {
        return bancoRepository.obtenerTodasLasCuentas().stream()
                .map(c -> java.util.Map.of(
                        "clienteId", (Object) c.getCuentaId(),
                        "nombre", c.getNombreTitular(),
                        "edad", c.getEdadTitular(),
                        "cuentaId", c.getCuentaId(),
                        "tipoCuenta", c.getTipoCuenta(),
                        "estado", c.getEstado()
                ))
                .collect(java.util.stream.Collectors.toList());
    }

    public java.util.Map<String, Object> obtenerClientePorId(Long clienteId) {
        Cuenta c = obtenerCuentaPorId(clienteId);
        return java.util.Map.of(
                "clienteId", (Object) c.getCuentaId(),
                "nombre", c.getNombreTitular(),
                "edad", c.getEdadTitular(),
                "cuentaId", c.getCuentaId(),
                "tipoCuenta", c.getTipoCuenta(),
                "estado", c.getEstado()
        );
    }

    public java.util.Map<String, Object> actualizarCliente(Long clienteId, String nuevoNombre, Integer nuevaEdad) {
        Cuenta c = obtenerCuentaPorId(clienteId);
        if (nuevoNombre != null && !nuevoNombre.isBlank()) {
            c.setNombreTitular(nuevoNombre);
        }
        if (nuevaEdad != null && nuevaEdad >= 18) {
            c.setEdadTitular(nuevaEdad);
        }
        bancoRepository.guardarCuenta(c);
        return obtenerClientePorId(clienteId);
    }
}
