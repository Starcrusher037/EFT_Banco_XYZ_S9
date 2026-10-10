package cl.duoc.bancoxyz.core.saga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro de estado y trazabilidad de una Saga de Transferencia Bancaria.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroSaga {

    private String sagaId;
    private Long cuentaOrigenId;
    private Long cuentaDestinoId;
    private Long monto;
    private EstadoSaga estado;
    private String mensajeResultado;
    private Long saldoFinalOrigen;
    private Long saldoFinalDestino;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;

    @Builder.Default
    private List<String> bitacoraPasos = new ArrayList<>();

    public void agregarPaso(String paso) {
        if (bitacoraPasos == null) {
            bitacoraPasos = new ArrayList<>();
        }
        bitacoraPasos.add(LocalDateTime.now().toLocalTime() + " - " + paso);
    }
}
