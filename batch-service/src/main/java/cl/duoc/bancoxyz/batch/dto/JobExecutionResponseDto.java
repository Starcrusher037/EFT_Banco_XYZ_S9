package cl.duoc.bancoxyz.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecutionResponseDto {
    private Long executionId;
    private String jobName;
    private String status;
    private String exitCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duracionMs;
    private Integer stepsEjecutados;
    private String mensaje;
}
