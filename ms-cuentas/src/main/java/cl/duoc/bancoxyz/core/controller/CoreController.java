package cl.duoc.bancoxyz.core.controller;

import cl.duoc.bancoxyz.core.model.Cuenta;
import cl.duoc.bancoxyz.core.model.MovimientoAnual;
import cl.duoc.bancoxyz.core.model.Transaccion;
import cl.duoc.bancoxyz.core.service.BancoService;
import cl.duoc.bancoxyz.core.mensajeria.EventoTransaccion;
import cl.duoc.bancoxyz.core.mensajeria.PublicadorTransacciones;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cl.duoc.bancoxyz.core.dto.RespuestaRetiroDto;
import cl.duoc.bancoxyz.core.dto.RespuestaTransferenciaDto;
import cl.duoc.bancoxyz.core.dto.SolicitudRetiroDto;
import cl.duoc.bancoxyz.core.dto.SolicitudTransferenciaDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/core")
@Tag(name = "Core Bancario", description = "Endpoints internos protegidos por Service Token")
public class CoreController {

    private final BancoService bancoService;
    private final PublicadorTransacciones publicadorTransacciones;

    public CoreController(BancoService bancoService, PublicadorTransacciones publicadorTransacciones) {
        this.bancoService = bancoService;
        this.publicadorTransacciones = publicadorTransacciones;
    }

    @Operation(summary = "Obtener cuenta por ID")
    @GetMapping("/cuentas/{cuentaId}")
    public ResponseEntity<Cuenta> obtenerCuenta(@PathVariable Long cuentaId) {
        return ResponseEntity.ok(bancoService.obtenerCuentaPorId(cuentaId));
    }

    @Operation(summary = "Listar todas las cuentas")
    @GetMapping({"/cuentas", "/cuentas/todas"})
    public ResponseEntity<List<Cuenta>> obtenerTodasLasCuentas() {
        return ResponseEntity.ok(bancoService.obtenerTodasLasCuentas());
    }

    @Operation(summary = "Apertura de nueva cuenta bancaria")
    @PostMapping("/cuentas")
    public ResponseEntity<Cuenta> crearCuenta(@RequestBody Cuenta nuevaCuenta) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(bancoService.crearCuenta(nuevaCuenta));
    }

    @Operation(summary = "Mantenimiento / actualizacion de cuenta bancaria")
    @PutMapping("/cuentas/{cuentaId}")
    public ResponseEntity<Cuenta> actualizarCuenta(@PathVariable Long cuentaId, @RequestBody Cuenta datos) {
        return ResponseEntity.ok(bancoService.actualizarCuenta(cuentaId, datos));
    }

    @Operation(summary = "Cierre de cuenta bancaria")
    @DeleteMapping("/cuentas/{cuentaId}")
    public ResponseEntity<Map<String, Object>> cerrarCuenta(@PathVariable Long cuentaId) {
        bancoService.cerrarCuenta(cuentaId);
        return ResponseEntity.ok(Map.of(
                "cuentaId", cuentaId,
                "mensaje", "Cuenta cerrada exitosamente",
                "estado", "CERRADA"
        ));
    }

    @Operation(summary = "Listar transacciones de una cuenta")
    @GetMapping("/cuentas/{cuentaId}/transacciones")
    public ResponseEntity<List<Transaccion>> obtenerTransacciones(@PathVariable Long cuentaId) {
        return ResponseEntity.ok(bancoService.obtenerTransaccionesPorCuenta(cuentaId));
    }

    @Operation(summary = "Listar movimientos anuales de una cuenta")
    @GetMapping({"/cuentas/{cuentaId}/anuales", "/cuentas/{cuentaId}/movimientos-anuales"})
    public ResponseEntity<List<MovimientoAnual>> obtenerMovimientosAnuales(@PathVariable Long cuentaId) {
        return ResponseEntity.ok(bancoService.obtenerMovimientosAnualesPorCuenta(cuentaId));
    }

    @Operation(summary = "Ejecutar retiro de fondos")
    @PostMapping("/operaciones/retiro")
    public ResponseEntity<RespuestaRetiroDto> ejecutarRetiro(@Valid @RequestBody SolicitudRetiroDto solicitud) {
        Cuenta cuenta = bancoService.ejecutarRetiro(
                solicitud.getCuentaId(),
                solicitud.getMonto(),
                solicitud.getCanal() != null ? solicitud.getCanal() : "ATM"
        );
        RespuestaRetiroDto respuesta = RespuestaRetiroDto.builder()
                .cuentaId(cuenta.getCuentaId())
                .montoRetirado(solicitud.getMonto())
                .nuevoSaldoContable(cuenta.getSaldoContable())
                .saldoDisponibleTotal(cuenta.getSaldoContable() + cuenta.getLineaSobregiro())
                .canal(solicitud.getCanal() != null ? solicitud.getCanal() : "ATM")
                .estado("EXITOSA")
                .mensaje("Giro procesado exitosamente")
                .build();
        return ResponseEntity.ok(respuesta);
    }

    @Operation(summary = "Ejecutar transferencia electronica de fondos")
    @PostMapping("/operaciones/transferencia")
    public ResponseEntity<RespuestaTransferenciaDto> ejecutarTransferencia(@Valid @RequestBody SolicitudTransferenciaDto solicitud) {
        bancoService.ejecutarTransferencia(
                solicitud.getCuentaOrigenId(),
                solicitud.getCuentaDestinoId(),
                solicitud.getMonto(),
                solicitud.getComentario()
        );
        RespuestaTransferenciaDto respuesta = RespuestaTransferenciaDto.builder()
                .cuentaOrigenId(solicitud.getCuentaOrigenId())
                .cuentaDestinoId(solicitud.getCuentaDestinoId())
                .montoTransferido(solicitud.getMonto())
                .estado("EXITOSA")
                .mensaje("Transferencia procesada exitosamente")
                .build();
        return ResponseEntity.ok(respuesta);
    }

    @Operation(summary = "Ejecutar deposito / pago en cuenta")
    @PostMapping("/operaciones/deposito")
    public ResponseEntity<Map<String, Object>> ejecutarDeposito(@RequestBody Map<String, Object> solicitud) {
        Long cuentaId = ((Number) solicitud.get("cuentaId")).longValue();
        Long monto = ((Number) solicitud.get("monto")).longValue();
        String canal = (String) solicitud.getOrDefault("canal", "WEB");
        String detalle = (String) solicitud.getOrDefault("detalle", "Abono a cuenta");
        Cuenta cuentaActualizada = bancoService.ejecutarDeposito(cuentaId, monto, canal, detalle);
        return ResponseEntity.ok(Map.of(
                "cuentaId", cuentaActualizada.getCuentaId(),
                "montoAbonado", monto,
                "nuevoSaldo", cuentaActualizada.getSaldoContable(),
                "mensaje", "Deposito procesado exitosamente",
                "estado", "EXITOSA"
        ));
    }

    @Operation(summary = "Listar clientes del banco")
    @GetMapping("/clientes")
    public ResponseEntity<List<Map<String, Object>>> obtenerClientes() {
        return ResponseEntity.ok(bancoService.obtenerTodosLosClientes());
    }

    @Operation(summary = "Obtener perfil de cliente por ID")
    @GetMapping("/clientes/{clienteId}")
    public ResponseEntity<Map<String, Object>> obtenerCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(bancoService.obtenerClientePorId(clienteId));
    }

    @Operation(summary = "Actualizar perfil de cliente")
    @PutMapping("/clientes/{clienteId}")
    public ResponseEntity<Map<String, Object>> actualizarCliente(@PathVariable Long clienteId, @RequestBody Map<String, Object> body) {
        String nombre = (String) body.get("nombre");
        Integer edad = body.get("edad") != null ? ((Number) body.get("edad")).intValue() : null;
        return ResponseEntity.ok(bancoService.actualizarCliente(clienteId, nombre, edad));
    }

    @Operation(summary = "Consultar eventos en contingencia local (Circuit Breaker / ActiveMQ desconectado)")
    @GetMapping("/mensajeria/contingencias")
    public ResponseEntity<Map<String, Object>> obtenerContingencias() {
        List<EventoTransaccion> contingencias = publicadorTransacciones.getTransaccionesContingencia();
        return ResponseEntity.ok(Map.of(
                "totalContingencias", contingencias.size(),
                "descripcion", "Eventos almacenados por Fallback de Resilience4j ante broker ActiveMQ no disponible",
                "eventos", contingencias
        ));
    }
}
