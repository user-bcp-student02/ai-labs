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