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
public class ResumenEstadoCuenta {
    private Long cuentaId;
    private LocalDate fecha;
    private String tipoMovimiento; // DEPOSITO, RETIRO, COMPRA
    private Long monto;
    private String descripcion;
    private String estadoValidacion;
}
