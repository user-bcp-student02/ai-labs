El contexto contiene dos alcances incompatibles: la transferencia no tiene un contrato aprobado y H-AUTH-04 corresponde al login Java. No invento la API de transferencias ni una respuesta HTTP de login cuyo formato no se proporcionó. El código siguiente implementa la separación en el caso de uso; falta conectarlo al adaptador HTTP y al adaptador de base de datos existentes, cuyos contratos y framework no están disponibles aquí.

```java
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
```

```java
// LoginCredential.java
package pe.bcp.login.service;

public final class LoginCredential {
    private final String subject;
    private final String encodedPassword;

    public LoginCredential(String subject, String encodedPassword) {
        this.subject = subject;
        this.encodedPassword = encodedPassword;
    }

    public String subject() {
        return subject;
    }

    public String encodedPassword() {
        return encodedPassword;
    }

    @Override
    public String toString() {
        return "LoginCredential[REDACTED]";
    }
}
```

```java
// PasswordVerifier.java
package pe.bcp.login.service;

@FunctionalInterface
public interface PasswordVerifier {
    boolean matches(char[] suppliedPassword, String encodedPassword);
}
```

```java
// LoginPrincipal.java
package pe.bcp.login.service;

public record LoginPrincipal(String subject) {
}
```

```java
// InvalidCredentialsException.java
package pe.bcp.login.service;

public final class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }
}
```

```java
// PersistenceFailureException.java
package pe.bcp.login.service;

public final class PersistenceFailureException extends RuntimeException {
    public PersistenceFailureException(Throwable cause) {
        super("No se pudo completar la operación de autenticación.", cause);
    }
}
```

```java
// LoginService.java
package pe.bcp.login.service;

import java.util.Objects;
import java.util.Optional;

public final class LoginService {
    private final LoginRepository repository;
    private final PasswordVerifier passwordVerifier;

    public LoginService(
            LoginRepository repository,
            PasswordVerifier passwordVerifier) {
        this.repository = Objects.requireNonNull(repository);
        this.passwordVerifier = Objects.requireNonNull(passwordVerifier);
    }

    public LoginPrincipal authenticate(String username, char[] suppliedPassword) {
        if (username == null || suppliedPassword == null) {
            throw new InvalidCredentialsException();
        }

        // Los fallos del repositorio se propagan; no se convierten en
        // credenciales inválidas.
        Optional<LoginCredential> credential =
                repository.findByUsername(username);

        if (credential.isEmpty()
                || !passwordVerifier.matches(
                        suppliedPassword,
                        credential.get().encodedPassword())) {
            throw new InvalidCredentialsException();
        }

        return new LoginPrincipal(credential.get().subject());
    }
}
```

```java
// LoginServiceTest.java
package pe.bcp.login.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoginServiceTest {
    @Test
    void incorrectPasswordRemainsAnAuthenticationRejection() {
        LoginRepository repository = username -> Optional.of(
                new LoginCredential("subject-test", "encoded-test"));

        LoginService service = new LoginService(
                repository,
                (supplied, encoded) -> false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> service.authenticate("user-test", "wrong".toCharArray()));
    }

    @Test
    void missingUserIsAnAuthenticationRejection() {
        LoginRepository repository = username -> Optional.empty();

        LoginService service = new LoginService(
                repository,
                (supplied, encoded) -> true);

        assertThrows(
                InvalidCredentialsException.class,
                () -> service.authenticate("missing-user", "secret".toCharArray()));
    }

    @Test
    void persistenceFailureIsNotConvertedToInvalidCredentials() {
        PersistenceFailureException failure =
                new PersistenceFailureException(new RuntimeException("internal detail"));

        LoginRepository repository = username -> {
            throw failure;
        };

        LoginService service = new LoginService(
                repository,
                (supplied, encoded) -> false);

        PersistenceFailureException propagated = assertThrows(
                PersistenceFailureException.class,
                () -> service.authenticate("user-test", "secret".toCharArray()));

        assertTrue(propagated == failure);
        assertEquals(
                "No se pudo completar la operación de autenticación.",
                propagated.getMessage());
    }
}
```

La respuesta HTTP genérica de servicio debe implementarse en el adaptador existente, capturando `PersistenceFailureException` y usando el formato y estado definidos por el contrato de login. No es posible especificar ese adaptador fielmente sin conocer el framework, el cuerpo de error aprobado y las firmas actuales de `AuthResource` y `LegacyDatabase`.

Verificación sugerida: ejecutar la prueba con el comando de test ya configurado en el proyecto (por ejemplo, `mvn test` si usa Maven). No ejecuté pruebas ni lint. La conexión con los adaptadores y la verificación quedan pendientes.