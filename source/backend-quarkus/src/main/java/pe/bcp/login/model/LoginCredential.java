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