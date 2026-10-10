package cl.duoc.bancoxyz.batch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteTransaccionDiaria {
    private Long id;
    private LocalDate fecha;
    private Long montoOriginal;
    private Long montoNormalizado;
    private String tipoOperacion; // CREDITO, DEBITO
    private boolean esAnomalia;
    private String detalleAnomalia;
    private String estadoAuditoria; // VALIDADA, RECTIFICADA, OBSERVADA

    public boolean esAnomalia() {
        return esAnomalia;
    }
}
