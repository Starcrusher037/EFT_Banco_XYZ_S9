package cl.duoc.bancoxyz.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cuenta {
    private Long cuentaId;
    private String nombreTitular;
    private Long saldoContable;
    private Integer edadTitular;
    private String tipoCuenta;
    private Long lineaSobregiro;
    private Double tasaInteresAnual;
    private String estado;

    public Long getSaldo() {
        return saldoContable;
    }

    public void setSaldo(Long saldo) {
        this.saldoContable = saldo;
    }

    public Integer getEdad() {
        return edadTitular;
    }

    public void setEdad(Integer edad) {
        this.edadTitular = edad;
    }

    public Long getId() {
        return cuentaId;
    }

    public void setId(Long id) {
        this.cuentaId = id;
    }
}
