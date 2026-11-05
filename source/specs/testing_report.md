# `testing_report.md`

```markdown
# Informe de pruebas — H-AUTH-04

**Feature:** Distinguir fallos de base de datos de credenciales inválidas en login  
**Componente:** `auth`  
**Fecha del informe:** 2026-11-05  
**Estado:** PLANIFICADO — NO EJECUTADO

## 1. Alcance

Se evalúa exclusivamente `POST /auth/login` y la separación entre:

- credenciales inválidas, que deben conservar el rechazo de autenticación;
- errores de persistencia, que deben propagarse y convertirse en un error de servicio;
- contenido de error, que no debe revelar credenciales ni detalles internos.

La propuesta de transferencia entre cuentas del historial no forma parte de H-AUTH-04 y queda fuera de este informe.

No se recibió el contrato completo de `POST /auth/login`, el código actual de `AuthResource` y `LegacyDatabase`, la configuración de compilación ni una salida de pruebas. Por ello, **no se atribuyen ejecuciones ni resultados reales**. Los resultados de la matriz son esperados; todos los casos permanecen **NO EJECUTADO**.

## 2. Contratos y correcciones por verificar

### Contrato HTTP conocido y pendiente

| Aspecto | Contrato disponible | Verificación |
|---|---|---|
| Ruta y método | `POST /auth/login` | Se conserva en las pruebas de integración. |
| Rechazo por credenciales incorrectas | La evidencia indica que el endpoint lo presenta como `401` | Verificar `401`. El cuerpo exacto no fue proporcionado y no se inventa. |
| Fallo de persistencia | Debe ser un error de servicio, no `401` | Verificar un estado de servicio del contrato aprobado, típicamente `5xx`. El estado exacto y el cuerpo de error están pendientes de confirmar. |
| Éxito | No se proporcionó el estado ni el esquema de respuesta | Comparar contra la especificación vigente cuando esté disponible; no fijar una respuesta ficticia. |
| Datos de solicitud | El código frontend propuesto usa `username` y `password` | Confirmar que esos nombres y validaciones son los del contrato real. |

**Bloqueo de contrato:** antes de aprobar la prueba de respuesta de servicio, Backend debe indicar el estado HTTP y el esquema de error aprobados para login. No se debe adoptar automáticamente `503` a partir de la propuesta de transferencias del historial: no es un contrato de login.

### Contrato de persistencia que debe cumplirse

1. Usuario inexistente: resultado equivalente a “sin registro” (por ejemplo, `Optional.empty()`), que el servicio traduce al rechazo de autenticación.
2. Usuario existente: entrega de la credencial persistida para verificar la contraseña.
3. Error de inicialización o consulta: excepción de persistencia propagada o envuelta en una excepción de persistencia; nunca `null`, usuario inexistente ni credenciales inválidas.
4. Un fallo de login no debe modificar datos persistidos.

La evidencia original menciona capturas de excepciones en `LegacyDatabase.java:16-28,34-43` y una traducción a rechazo en `AuthResource.java:35-38`. Se debe comprobar en el código real que la corrección elimina esa conversión de error a `null` y conecta la excepción hasta el adaptador HTTP.

### Evaluación de la corrección propuesta en el historial

La propuesta de `LoginRepository`, `LoginService` y `PersistenceFailureException` separa correctamente, a nivel de servicio, usuario inexistente de error de repositorio. Sin embargo, **no consta que esté conectada al `LegacyDatabase` ni a `AuthResource` reales**. Por tanto, no prueba por sí sola el contrato de API.

La propuesta también conserva la causa interna de la excepción. Eso puede ser adecuado para diagnóstico interno, pero el manejador HTTP debe construir una respuesta genérica aprobada; no debe serializar la excepción, su causa o su stack trace. La prueba HTTP debe inspeccionar el cuerpo real de error.

La propuesta frontend clasifica `401` como error de credenciales y `5xx`/fallo de red como error de servicio. Es una distinción de presentación, no demuestra que el backend propague correctamente el error de persistencia.

## 3. Preparación y datos de prueba

### Pruebas unitarias

- Usar repositorio falso en memoria; no conectar a datos reales.
- Usar `qa-auth-user` como usuario sintético.
- Usar `encoded-test-value` como valor sintético de credencial codificada.
- Usar `synthetic-correct-password` y `synthetic-wrong-password` únicamente en memoria.
- Simular el fallo con una excepción que incluya el marcador `DB_INTERNAL_SENTINEL`; comprobar que no llega al mensaje público.
- No imprimir contraseñas, credenciales codificadas ni objetos de credenciales en logs.
- Las pruebas unitarias no escriben ni limpian filas en una base de datos.

### Pruebas HTTP/de integración

- Usar únicamente una instancia local o entorno de integración aislado con datos sintéticos.
- Confirmar que la base de datos está disponible para la prueba de credenciales inválidas.
- Para simular fallo de consulta, detener solo el servicio de base de datos del entorno de prueba mediante el mecanismo local aprobado. No ejecutar este procedimiento contra producción o un entorno compartido.
- Preparar `AUTH_SERVICE_STATUS` con el estado exacto aprobado para el error de servicio.
- La prueba de fallo de inicialización requiere iniciar una instancia aislada de la aplicación con la base de datos no disponible. El comportamiento de readiness/inicio esperado debe acordarse con Backend.
- Guardar las respuestas en un directorio temporal y eliminarlo al terminar.
- Restaurar el servicio de base de datos y la aplicación a su estado inicial. No borrar datos compartidos.

## 4. Casos de prueba y resultado esperado

| ID | Tipo | Criterio | Preparación y datos | Resultado esperado | Estado |
|---|---|---|---|---|---|
| UT-01 | Positivo, unitario | Contrato funcional de login | Repositorio falso devuelve una credencial sintética; verificador acepta la contraseña de prueba | Login devuelve un principal para el usuario sintético; no se produce rechazo ni error de persistencia | **NO EJECUTADO** |
| UT-02 | Negativo, unitario/API | AC-1 | Usuario existe; verificador rechaza `synthetic-wrong-password` | Servicio clasifica como credenciales inválidas; en HTTP, `POST /auth/login` devuelve `401` según la evidencia disponible, con el cuerpo del contrato vigente | **NO EJECUTADO** |
| UT-03 | Negativo, unitario/API | AC-1 | Repositorio devuelve “usuario no existe” | Mismo rechazo de autenticación que para contraseña incorrecta; no se confunde con error de servicio | **NO EJECUTADO** |
| UT-04 | Negativo, unitario | AC-2 y AC-4 | Repositorio lanza `PersistenceFailureException` con causa `DB_INTERNAL_SENTINEL` | La excepción de persistencia no se convierte en `InvalidCredentialsException` ni en ausencia de usuario; el verificador de contraseña no se invoca | **NO EJECUTADO** |
| UT-05 | Borde, unitario | Contrato funcional a confirmar | Probar username nulo y contraseña nula por separado | Según la propuesta de servicio, se rechazan como credenciales inválidas. Confirmar que coincide con la validación del contrato real | **NO EJECUTADO** |
| INT-01 | Integración HTTP positiva | Conservación del contrato de login | Cuenta sintética de integración con credencial válida | Estado y cuerpo coinciden exactamente con el contrato de éxito vigente; no se fijan aquí valores no proporcionados | **NO EJECUTADO** |
| INT-02 | Integración HTTP negativa | AC-1 y AC-4 | Base de datos disponible; enviar credenciales sintéticas incorrectas | HTTP `401`; no se presenta como error de servicio; cuerpo conforme al contrato vigente | **NO EJECUTADO** |
| INT-03 | Integración con persistencia | AC-2 y AC-4 | Base de datos del entorno aislado no disponible durante la consulta | Se devuelve el estado de servicio aprobado, distinto de `401`; la falla no se trata como usuario inexistente ni credenciales inválidas | **NO EJECUTADO** |
| INT-04 | Seguridad de respuesta | AC-3 | Provocar error con marcador interno conocido en el entorno de prueba | La respuesta no contiene el marcador, SQL, nombres de tablas, stack trace, clase de excepción, username ni password enviados | **NO EJECUTADO** |
| INT-05 | Integración de inicialización | AC-2 | Iniciar la aplicación aislada con la base de datos indisponible | La falla no se oculta como ausencia de usuario. Debe cumplirse el comportamiento acordado de inicio/readiness o error de servicio | **NO EJECUTADO** |
| INT-06 | Persistencia | AC-2 | Capturar estado antes y después de un login que falla por persistencia | No hay escrituras ni cambios persistidos atribuibles al intento de login | **NO EJECUTADO** |

### Relación directa con criterios de aceptación

- **AC-1 — credenciales incorrectas:** UT-02, UT-03 e INT-02.
- **AC-2 — fallo de base de datos como error de servicio:** UT-04, INT-03, INT-05 e INT-06.
- **AC-3 — no exponer detalles internos ni credenciales:** INT-04.
- **AC-4 — cobertura de credenciales incorrectas y persistencia simulada, conservando el contrato:** UT-02, UT-04, INT-02 e INT-03; INT-01 verifica adicionalmente el flujo positivo.

## 5. Comandos

Los comandos siguientes son instrucciones de ejecución, no evidencia de ejecución. No se conoce si el proyecto usa Maven, Gradle u otro sistema; utilizar únicamente el comando que corresponda a los archivos reales del repositorio.

### Unitarias — Maven, si el módulo tiene `auth/pom.xml` y JUnit configurado

Desde la raíz del repositorio:

```bash
./mvnw -f auth/pom.xml -Dtest=LoginServiceContractTest test
```

Si no existe wrapper Maven y Maven está instalado:

```bash
mvn -f auth/pom.xml -Dtest=LoginServiceContractTest test
```

Si el proyecto no es Maven, adaptar el comando al build existente antes de ejecutar. La clase de prueba incluida abajo usa el paquete y las interfaces de la propuesta del historial; no compilará contra producción hasta integrar o adaptar esos tipos.

### Pruebas HTTP locales

Se entrega `scripts/test-h-auth-04-api.sh`. Requiere `bash`, `curl`, `docker compose` y un entorno local desechable. El valor de `DB_SERVICE` debe ser el nombre real del servicio Compose de base de datos. `AUTH_SERVICE_STATUS` debe ser el estado acordado por el contrato de login; `503` es solo un ejemplo si Backend lo aprueba.

Ejemplo **solo para un entorno local aislado**; reemplazar `auth-db-test` y confirmar el estado antes de usar:

```bash
chmod +x scripts/test-h-auth-04-api.sh

AUTH_BASE_URL=http://127.0.0.1:8080 \
DB_SERVICE=auth-db-test \
AUTH_SERVICE_STATUS=503 \
ALLOW_DB_STOP=YES \
./scripts/test-h-auth-04-api.sh
```

El script comprueba primero el rechazo por credenciales inválidas con la base de datos disponible; luego detiene el servicio indicado, comprueba el error de login y lo vuelve a iniciar. No ejecutar en producción ni en un entorno compartido. Si no se conoce el nombre del servicio, no ejecutar.

La prueba de fallo de inicialización no está automatizada por este script: requiere el comando de inicio real de la aplicación y el procedimiento aislado aprobado para hacerla arrancar sin base de datos.

## 6. Criterios de aprobación

1. UT-02, UT-03 e INT-02 confirman el rechazo de autenticación aprobado para credenciales incorrectas.
2. UT-04 confirma que el fallo de repositorio no se convierte en credenciales inválidas.
3. INT-03 confirma el estado HTTP de servicio aprobado y que no es `401`.
4. INT-04 confirma que el cuerpo HTTP no filtra credenciales ni información interna.
5. Se aprueba el contrato de estado y cuerpo del error de servicio antes de cerrar INT-03/INT-04.
6. Se confirma que el adaptador real de persistencia no captura errores y retorna `null`/“no encontrado”.
7. Las pruebas de éxito y rechazo siguen pasando contra el contrato real de `POST /auth/login`.
8. Se adjuntan las salidas reales del build y de las pruebas. Sin esas salidas no se puede declarar aprobado el cambio.

## 7. Resultados y evidencia

| Verificación | Resultado |
|---|---|
| Ejecución de pruebas unitarias | **NO EJECUTADO** — no se ejecutaron herramientas ni se recibió salida del build. |
| Ejecución de pruebas HTTP | **NO EJECUTADO** — no se recibió URL, entorno, configuración Compose ni salida HTTP real. |
| Contrato exacto del cuerpo de `401` | Pendiente: no proporcionado. |
| Estado y cuerpo exactos del error de servicio | Pendiente de aprobación por Backend; no se inventan. |
| Conexión de la corrección propuesta a `LegacyDatabase` y `AuthResource` reales | Pendiente de inspección e integración. |
| Fallo de inicialización de base de datos | Pendiente de definir el comportamiento de arranque/readiness y ejecutar en entorno aislado. |

**Conclusión:** no hay evidencia suficiente para certificar la corrección. La separación propuesta en la capa de servicio es comprobable con las pruebas unitarias incluidas, pero falta conectarla al código real y verificar el contrato HTTP y de persistencia de extremo a extremo.
```

# `auth/src/test/java/pe/bcp/login/service/LoginServiceContractTest.java`

> Prueba para las interfaces y clases propuestas en el historial (`pe.bcp.login.service`). No se afirma que corresponda al paquete o build del repositorio real ni que haya sido ejecutada.

```java
package pe.bcp.login.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class LoginServiceContractTest {
    private static final String USERNAME = "qa-auth-user";
    private static final String ENCODED_TEST_VALUE = "encoded-test-value";
    private static final String CORRECT_PASSWORD = "synthetic-correct-password";
    private static final String WRONG_PASSWORD = "synthetic-wrong-password";

    @Test
    void validCredentialsReturnPrincipal() {
        LoginRepository repository = username -> Optional.of(
                new LoginCredential(USERNAME, ENCODED_TEST_VALUE));

        LoginService service = new LoginService(
                repository,
                (supplied, encoded) ->
                        CORRECT_PASSWORD.equals(new String(supplied))
                                && ENCODED_TEST_VALUE.equals(encoded));

        LoginPrincipal principal =
                service.authenticate(USERNAME, CORRECT_PASSWORD.toCharArray());

        assertEquals(USERNAME, principal.subject());
    }

    @Test
    void wrongPasswordRemainsAnAuthenticationRejection() {
        LoginRepository repository = username -> Optional.of(
                new LoginCredential(USERNAME, ENCODED_TEST_VALUE));

        LoginService service = new LoginService(
                repository,
                (supplied, encoded) -> false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> service.authenticate(USERNAME, WRONG_PASSWORD.toCharArray()));
    }

    @Test
    void missingUserRemainsAnAuthenticationRejection() {
        AtomicBoolean verifierCalled = new AtomicBoolean(false);
        LoginRepository repository = username -> Optional.empty();

        LoginService service = new LoginService(
                repository,
                (supplied, encoded) -> {
                    verifierCalled.set(true);
                    return true;
                });

        assertThrows(
                InvalidCredentialsException.class,
                () -> service.authenticate(
                        "qa-auth-missing-user",
                        WRONG_PASSWORD.toCharArray()));

        assertFalse(verifierCalled.get());
    }

    @Test
    void persistenceFailureIsNotConvertedToInvalidCredentials() {
        RuntimeException internalCause =
                new RuntimeException("DB_INTERNAL_SENTINEL");
        PersistenceFailureException persistenceFailure =
                new PersistenceFailureException(internalCause);

        AtomicBoolean verifierCalled = new AtomicBoolean(false);
        LoginRepository repository = username -> {
            throw persistenceFailure;
        };

        LoginService service = new LoginService(
                repository,
                (supplied, encoded) -> {
                    verifierCalled.set(true);
                    return false;
                });

        PersistenceFailureException thrown = assertThrows(
                PersistenceFailureException.class,
                () -> service.authenticate(USERNAME, WRONG_PASSWORD.toCharArray()));

        assertSame(persistenceFailure, thrown);
        assertFalse(verifierCalled.get());

        // La excepción pública propuesta usa un mensaje genérico. La causa
        // interna puede existir para diagnóstico interno, pero no debe ser
        // serializada por el adaptador HTTP.
        assertEquals(
                "No se pudo completar la operación de autenticación.",
                thrown.getMessage());
        assertFalse(thrown.getMessage().contains("DB_INTERNAL_SENTINEL"));
    }

    @Test
    void nullUsernameIsRejectedAsInvalidCredentials() {
        LoginRepository repository = username -> Optional.empty();
        LoginService service = new LoginService(
                repository,
                (supplied, encoded) -> true);

        assertThrows(
                InvalidCredentialsException.class,
                () -> service.authenticate(null, CORRECT_PASSWORD.toCharArray()));
    }

    @Test
    void nullPasswordIsRejectedAsInvalidCredentials() {
        LoginRepository repository = username -> Optional.empty();
        LoginService service = new LoginService(
                repository,
                (supplied, encoded) -> true);

        assertThrows(
                InvalidCredentialsException.class,
                () -> service.authenticate(USERNAME, null));
    }
}
```

# `scripts/test-h-auth-04-api.sh`

> Prueba de integración propuesta. Requiere un entorno local desechable; no se ejecutó. No determina por sí sola el estado HTTP correcto: `AUTH_SERVICE_STATUS` debe coincidir con el contrato aprobado.

```bash
#!/usr/bin/env bash
set -euo pipefail

: "${AUTH_BASE_URL:?Definir AUTH_BASE_URL, por ejemplo http://127.0.0.1:8080}"
: "${DB_SERVICE:?Definir el nombre real del servicio DB de Docker Compose}"
: "${AUTH_SERVICE_STATUS:?Definir el estado 5xx aprobado por el contrato de login}"
: "${ALLOW_DB_STOP:?Definir ALLOW_DB_STOP=YES solo para un entorno local desechable}"

if [[ "$ALLOW_DB_STOP" != "YES" ]]; then
  echo "ERROR: se requiere ALLOW_DB_STOP=YES para autorizar la interrupción de la DB local." >&2
  exit 2
fi

if [[ ! "$AUTH_BASE_URL" =~ ^https?://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$ ]]; then
  echo "ERROR: AUTH_BASE_URL debe apuntar a localhost, 127.0.0.1 o ::1." >&2
  exit 2
fi

if [[ ! "$AUTH_SERVICE_STATUS" =~ ^5[0-9]{2}$ ]]; then
  echo "ERROR: AUTH_SERVICE_STATUS debe ser el estado 5xx aprobado para el error de servicio." >&2
  exit 2
fi

AUTH_BASE_URL="${AUTH_BASE_URL%/}"
TEMP_DIR="$(mktemp -d)"
DB_STOPPED_BY_TEST=0

cleanup() {
  if [[ "$DB_STOPPED_BY_TEST" -eq 1 ]]; then
    echo "Restaurando el servicio de base de datos: $DB_SERVICE"
    docker compose start "$DB_SERVICE" || {
      echo "ERROR: no se pudo restaurar $DB_SERVICE. Revisar el entorno local." >&2
    }
  fi
  rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

request_login() {
  local username="$1"
  local password="$2"
  local response_file="$3"

  curl --silent --show-error \
    --connect-timeout 3 \
    --output "$response_file" \
    --write-out '%{http_code}' \
    --header 'Content-Type: application/json' \
    --request POST \
    --data "{\"username\":\"${username}\",\"password\":\"${password}\"}" \
    "${AUTH_BASE_URL}/auth/login"
}

assert_no_service_details() {
  local response_file="$1"

  if grep -Fq 'qa-h-auth04-missing-user' "$response_file"; then
    echo "ERROR: la respuesta de servicio contiene el username de prueba." >&2
    return 1
  fi

  if grep -Fq 'Synthetic-NoRealSecret-H-AUTH04' "$response_file"; then
    echo "ERROR: la respuesta de servicio contiene la contraseña de prueba." >&2
    return 1
  fi

  if grep -Eiq \
    'DB_INTERNAL_SENTINEL|SQLException|SQLSTATE|jdbc:|stack trace|stacktrace|org\.hibernate|password=' \
    "$response_file"; then
    echo "ERROR: la respuesta de servicio parece exponer detalles internos." >&2
    return 1
  fi
}

echo "INT-02: credenciales incorrectas con la DB disponible"
INVALID_BODY="${TEMP_DIR}/invalid-credentials.json"
INVALID_STATUS="$(
  request_login \
    'qa-h-auth04-missing-user' \
    'Synthetic-NoRealSecret-H-AUTH04' \
    "$INVALID_BODY"
)"

if [[ "$INVALID_STATUS" != "401" ]]; then
  echo "ERROR: se esperaba HTTP 401 para credenciales incorrectas; se recibió $INVALID_STATUS." >&2
  cat "$INVALID_BODY" >&2
  exit 1
fi

echo "INT-02: HTTP 401 recibido."

echo "INT-03/INT-04: fallo simulado de persistencia"
docker compose stop "$DB_SERVICE"
DB_STOPPED_BY_TEST=1

SERVICE_BODY="${TEMP_DIR}/service-error.json"
SERVICE_STATUS="$(
  request_login \
    'qa-h-auth04-missing-user' \
    'Synthetic-NoRealSecret-H-AUTH04' \
    "$SERVICE_BODY"
)"

if [[ "$SERVICE_STATUS" != "$AUTH_SERVICE_STATUS" ]]; then
  echo "ERROR: se esperaba HTTP $AUTH_SERVICE_STATUS para fallo de persistencia; se recibió $SERVICE_STATUS." >&2
  cat "$SERVICE_BODY" >&2
  exit 1
fi

if [[ "$SERVICE_STATUS" == "401" ]]; then
  echo "ERROR: el fallo de persistencia fue presentado como credenciales inválidas." >&2
  exit 1
fi

assert_no_service_details "$SERVICE_BODY"

echo "INT-03/INT-04: estado de servicio y comprobaciones básicas de no filtrado recibidos."
echo "La salida anterior no sustituye la comparación del cuerpo con el contrato de error aprobado."
```

**Ejecución:** **NO EJECUTADO**. No se recibieron salidas reales de pruebas, del servidor ni de la base de datos.