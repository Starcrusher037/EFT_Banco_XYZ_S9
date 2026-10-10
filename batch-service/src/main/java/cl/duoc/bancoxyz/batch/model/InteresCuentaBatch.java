package cl.duoc.bancoxyz.batch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InteresCuentaBatch {
    private String cuentaId;
    private String nombre;
    private String saldo;
    private String edad;
    private String tipo;
}
