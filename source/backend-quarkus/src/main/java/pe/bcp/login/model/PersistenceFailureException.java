// PersistenceFailureException.java
package pe.bcp.login.service;

public final class PersistenceFailureException extends RuntimeException {
    public PersistenceFailureException(Throwable cause) {
        super("No se pudo completar la operación de autenticación.", cause);
    }
}