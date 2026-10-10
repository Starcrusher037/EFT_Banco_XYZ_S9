package cl.duoc.bancoxyz.core.saga;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de solicitud para iniciar una transacción distribuida bajo el patrón Saga.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudSagaTransferencia {

    @NotNull(message = "El identificador de la cuenta origen es obligatorio")
    @JsonAlias({"cuentaId", "cuentaOrigenId"})
    private Long cuentaOrigenId;

    @NotNull(message = "El identificador de la cuenta destino es obligatorio")
    private Long cuentaDestinoId;

    @NotNull(message = "El monto es obligatorio")
    @Min(value = 100, message = "El monto mínimo a transferir es $100")
    private Long monto;

    private String glosa;

    /**
     * Flag opcional para demostración pedagógica y pruebas de capturas:
     * Si es true, simula un fallo en la cuenta destino para observar
     * la ejecución automática de la transacción de compensación.
     */
    private Boolean simularFalloDestino;
}
