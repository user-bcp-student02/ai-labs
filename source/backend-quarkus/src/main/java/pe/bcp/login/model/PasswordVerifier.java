// PasswordVerifier.java
package pe.bcp.login.service;

@FunctionalInterface
public interface PasswordVerifier {
    boolean matches(char[] suppliedPassword, String encodedPassword);
}