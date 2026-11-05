// InvalidCredentialsException.java
package pe.bcp.login.service;

public final class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }
}