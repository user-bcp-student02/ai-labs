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