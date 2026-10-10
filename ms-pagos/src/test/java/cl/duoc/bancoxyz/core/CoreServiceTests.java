package cl.duoc.bancoxyz.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.duoc.bancoxyz.core.model.Cuenta;
import cl.duoc.bancoxyz.core.repository.BancoRepository;
import cl.duoc.bancoxyz.core.service.BancoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class CoreServiceTests {

    @Autowired
    private BancoRepository bancoRepository;

    @Autowired
    private BancoService bancoService;

    @Test
    @DisplayName("Carga de datos legacy inicializa al menos 50 cuentas bancarias")
    void cargaDatosLegacyExitosa() {
        List<Cuenta> cuentas = bancoRepository.obtenerTodasLasCuentas();
        assertThat(cuentas).hasSizeGreaterThanOrEqualTo(50);
    }

    @Test
    @DisplayName("Ejecución de retiro exitosa sobre cuenta bancaria")
    void ejecutarRetiroCoreExitoso() {
        Cuenta primeraCuenta = bancoRepository.obtenerTodasLasCuentas().get(0);
        Long cuentaId = primeraCuenta.getCuentaId();
        
        // Asignar saldo seguro para la prueba
        primeraCuenta.setSaldoContable(100000L);
        bancoRepository.guardarCuenta(primeraCuenta);

        Cuenta actualizada = bancoService.ejecutarRetiro(cuentaId, 10000L, "ATM");
        assertThat(actualizada.getSaldoContable()).isEqualTo(90000L);
    }

    @Autowired
    private cl.duoc.bancoxyz.core.saga.OrquestadorSagaTransferencia orquestadorSaga;

    @Test
    @DisplayName("Retiro mayor al saldo disponible lanza IllegalArgumentException")
    void retiroExcedenteLanzaExcepcion() {
        Cuenta primeraCuenta = bancoRepository.obtenerTodasLasCuentas().get(0);
        Long cuentaId = primeraCuenta.getCuentaId();
        
        assertThatThrownBy(() -> bancoService.ejecutarRetiro(cuentaId, 9999999999L, "ATM"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Saga Transferencia Exitosa: Débito y abono completados sin compensación")
    void sagaTransferenciaExitosa() {
        List<Cuenta> cuentas = bancoRepository.obtenerTodasLasCuentas();
        Cuenta cta1 = cuentas.get(0);
        Cuenta cta2 = cuentas.get(1);

        cta1.setSaldoContable(100000L);
        cta2.setSaldoContable(50000L);
        bancoRepository.guardarCuenta(cta1);
        bancoRepository.guardarCuenta(cta2);

        cl.duoc.bancoxyz.core.saga.SolicitudSagaTransferencia solicitud = cl.duoc.bancoxyz.core.saga.SolicitudSagaTransferencia.builder()
                .cuentaOrigenId(cta1.getCuentaId())
                .cuentaDestinoId(cta2.getCuentaId())
                .monto(20000L)
                .glosa("Transferencia Saga exitosa")
                .simularFalloDestino(false)
                .build();

        cl.duoc.bancoxyz.core.saga.RegistroSaga resultado = orquestadorSaga.ejecutarSaga(solicitud);

        assertThat(resultado.getEstado()).isEqualTo(cl.duoc.bancoxyz.core.saga.EstadoSaga.COMPLETADA);
        assertThat(cta1.getSaldoContable()).isEqualTo(80000L);
        assertThat(cta2.getSaldoContable()).isEqualTo(70000L);
    }

    @Test
    @DisplayName("Saga Transferencia con Fallo en Destino: Se activa Compensación Automática y restituye fondos")
    void sagaTransferenciaCompensacionPorFallo() {
        List<Cuenta> cuentas = bancoRepository.obtenerTodasLasCuentas();
        Cuenta cta1 = cuentas.get(0);
        Cuenta cta2 = cuentas.get(1);

        cta1.setSaldoContable(100000L);
        bancoRepository.guardarCuenta(cta1);

        cl.duoc.bancoxyz.core.saga.SolicitudSagaTransferencia solicitud = cl.duoc.bancoxyz.core.saga.SolicitudSagaTransferencia.builder()
                .cuentaOrigenId(cta1.getCuentaId())
                .cuentaDestinoId(cta2.getCuentaId())
                .monto(30000L)
                .glosa("Transferencia con fallo forzado")
                .simularFalloDestino(true)
                .build();

        cl.duoc.bancoxyz.core.saga.RegistroSaga resultado = orquestadorSaga.ejecutarSaga(solicitud);

        // Estado debe ser COMPENSADA tras el fallo en el paso 2
        assertThat(resultado.getEstado()).isEqualTo(cl.duoc.bancoxyz.core.saga.EstadoSaga.COMPENSADA);
        // El saldo de la cuenta origen fue debitado y luego restituido íntegramente
        assertThat(cta1.getSaldoContable()).isEqualTo(100000L);
        assertThat(resultado.getBitacoraPasos()).anyMatch(p -> p.contains("COMPENSACIÓN COMPLETADA"));
    }
}
