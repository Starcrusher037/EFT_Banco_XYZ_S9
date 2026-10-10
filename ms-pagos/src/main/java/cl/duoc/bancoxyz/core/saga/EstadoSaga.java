package cl.duoc.bancoxyz.core.saga;

/**
 * Estados del ciclo de vida de una Saga de Transferencia Bancaria Distribuida.
 */
public enum EstadoSaga {
    INICIADA,
    DEBITO_ORIGEN_EXITOSO,
    ABONO_DESTINO_EXITOSO,
    COMPLETADA,
    FALLIDA,
    COMPENSACION_INICIADA,
    COMPENSADA
}
