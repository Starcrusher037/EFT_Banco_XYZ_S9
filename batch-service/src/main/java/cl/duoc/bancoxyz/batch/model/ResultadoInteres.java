package cl.duoc.bancoxyz.batch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultadoInteres {
    private Long cuentaId;
    private String nombreTitular;
    private Long saldoBase;
    private Integer edad;
    private String tipoCuenta;
    private Double tasaInteresMensual;
    private Long montoInteresCalculado;
    private Long nuevoSaldoProyectado;
    private String observaciones;
}
