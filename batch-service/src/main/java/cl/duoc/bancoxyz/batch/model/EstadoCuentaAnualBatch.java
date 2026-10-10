package cl.duoc.bancoxyz.batch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadoCuentaAnualBatch {
    private String cuentaId;
    private String fecha;
    private String transaccion;
    private String monto;
    private String descripcion;
}
