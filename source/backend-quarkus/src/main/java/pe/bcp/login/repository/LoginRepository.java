// LoginRepository.java
package pe.bcp.login.service;

import java.util.Optional;

public interface LoginRepository {
    /**
     * Devuelve Optional.empty() solo si el usuario no existe.
     * Los errores de persistencia deben propagarse como
     * PersistenceFailureException, nunca convertirse en Optional.empty().
     */
    Optional<LoginCredential> findByUsername(String username);
}