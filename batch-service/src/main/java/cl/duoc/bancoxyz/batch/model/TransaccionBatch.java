package cl.duoc.bancoxyz.batch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransaccionBatch {
    private String id;
    private String fecha;
    private String monto;
    private String tipo;
}
