package cl.duoc.bancoxyz.core.saga;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para el Patrón Saga de Transferencias Bancarias Distribuidas.
 */
@RestController
@RequestMapping("/api/core/saga")
@Tag(name = "Patrón Saga", description = "Endpoints de Orquestación de Transacciones Distribuidas y Compensación Automática")
public class SagaController {

    private final OrquestadorSagaTransferencia orquestadorSaga;

    public SagaController(OrquestadorSagaTransferencia orquestadorSaga) {
        this.orquestadorSaga = orquestadorSaga;
    }

    @Operation(summary = "Ejecutar Transferencia bajo Patrón Saga (con compensación automática si falla el destino)")
    @PostMapping("/transferencia")
    public ResponseEntity<RegistroSaga> ejecutarSagaTransferencia(@Valid @RequestBody SolicitudSagaTransferencia solicitud) {
        RegistroSaga resultado = orquestadorSaga.ejecutarSaga(solicitud);
        return ResponseEntity.ok(resultado);
    }

    @Operation(summary = "Listar historial y bitácora de Sagas ejecutadas")
    @GetMapping("/historial")
    public ResponseEntity<List<RegistroSaga>> listarHistorialSagas() {
        return ResponseEntity.ok(orquestadorSaga.obtenerHistorialSagas());
    }

    @Operation(summary = "Consultar estado y pasos de una Saga específica")
    @GetMapping("/{sagaId}")
    public ResponseEntity<?> consultarSaga(@PathVariable String sagaId) {
        RegistroSaga saga = orquestadorSaga.obtenerSagaPorId(sagaId);
        if (saga == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Saga no encontrada: " + sagaId));
        }
        return ResponseEntity.ok(saga);
    }
}
