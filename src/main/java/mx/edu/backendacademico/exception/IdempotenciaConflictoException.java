package mx.edu.backendacademico.exception;

// Reto U11: la clave ya está ligada a otra solicitud con contenido distinto.
public class IdempotenciaConflictoException extends RuntimeException {
    public IdempotenciaConflictoException(String clave) {
        super("La clave de idempotencia " + clave + " ya se usó con otro contenido");
    }
}
