package cl.duoc.bancoxyz.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumenBatchDto {
    private int totalTransaccionesDiariasProcesadas;
    private long totalAnomaliasDetectadas;
    private long volumenTotalTransaccionesDiarias;
    private int totalCuentasInteresesProcesadas;
    private long totalInteresesLiquidados;
    private int totalMovimientosAnualesAuditados;
    private int totalRegistrosOmitidosPorCorrupcion;
    private Map<String, Object> metricasUltimaEjecucion;
    private String estadoGeneral;
}
