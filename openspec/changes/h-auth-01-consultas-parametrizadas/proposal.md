# Proposal

Issue: [#24 H-AUTH-01 Usar consultas parametrizadas en el login de auth](https://github.com/user-bcp-student02/ai-labs/issues/24)
Hallazgo: H-AUTH-01 (vulnerabilidad, severidad alta) · Componente: `auth`

## Why

`LegacyDatabase.findByCredentials` construye la consulta SQL concatenando `username` y `password` directamente (`auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32`). Un valor como `' OR '1'='1` altera la consulta y permite iniciar sesión sin conocer una contraseña válida, lo que compromete la autenticación del servicio. Es un riesgo de severidad alta que debe corregirse antes de cualquier otro cambio sobre el flujo de login.

## What Changes

- El acceso a credenciales de `auth` usa consultas parametrizadas: `username` y `password` se pasan como parámetros y nunca se concatenan en el SQL.
- El contrato observable de `POST /auth/login` se conserva para credenciales válidas e inválidas (ver *Comportamiento que debe preservarse*).
- Un intento de inyección en `username` o `password` deja de autenticar: responde 401 sin `token`.
- Se agrega cobertura de prueba para el intento de inyección en `AuthResourceTest`.
- No hay cambios de contrato hacia clientes: ni rutas, ni campos de respuesta, ni códigos de estado.

**BREAKING**: ninguno.

## Capabilities

### New Capabilities
- `auth-login`: verificación de credenciales en `POST /auth/login` (validación de entrada, consulta de credenciales de forma segura, respuestas 200/400/401/500 y el contrato de la respuesta exitosa).

### Modified Capabilities
- Ninguna. `openspec/specs/` no contiene capacidades previas, por lo que el comportamiento de login se declara por primera vez.

## Alcance

Dentro del alcance:
- Consulta de credenciales en `auth/` (`LegacyDatabase.findByCredentials`) y la llamada desde `AuthResource.login`.
- Pruebas de `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` que cubran el login válido y el intento de inyección.
- Requisitos y escenarios del login en la spec `auth-login`.

Fuera de alcance:
- Cambios en `api/` o `web/`, incluido el consumo de tokens en `api/`.
- Cambios al formato del token (`LegacyTokenUtil`): el token legacy sigue como está en este cambio.
- Migración de contraseñas a hash, política de contraseñas, bloqueo por intentos fallidos o rotación de credenciales de prueba.
- Reemplazo del almacenamiento SQLite o de la inicialización de `legacy_users`.
- Corrección de otros hallazgos de `auth` (por ejemplo, el eco del `username` en el mensaje 401, que se conserva tal cual).
- Cambios de CI, quality gates o dependencias (CodeQL, Dependency Review y JaCoCo no se silencian ni se modifican).

## Comportamiento que debe preservarse

Fuente: el Issue #24 y `AGENTS.md` (sección auth: "Conserva el contrato de `POST /auth/login`"). `docs/ENDPOINTS.md` documenta las rutas de `api/` y no incluye `POST /auth/login`; por eso el contrato de login se toma del código actual y del Issue, no de ese documento.

Comportamiento observado en `AuthResource.login` que debe mantenerse:

| Caso | Estado | Cuerpo |
|---|---|---|
| Credenciales válidas (`student` / `Workshop123!`) | 200 | `token`, `expiresIn` (3600), `user` con `username`, `displayName`, `role`, y `debug` |
| Request nulo | 400 | `message`: `Request is null` |
| `username` vacío o en blanco | 400 | `message`: `Username is required` |
| `password` vacío o en blanco | 400 | `message`: `Password is required` |
| Credenciales no coincidentes o inyección | 401 | `message`; sin `token` |
| Error interno no controlado | 500 | `message` y `exception` |

El campo `displayName` se mantiene escapado HTML como hoy.

## Riesgos y supuestos

- El Issue fija el criterio de 401 sin `token` para la inyección; el mensaje exacto del 401 no cambia.
- Se asume que el esquema SQLite actual (`legacy_users`) no cambia.
- El token emitido sigue siendo el legacy de `auth`; la integración con `api/` (que según `docs/ENDPOINTS.md` rechaza tokens legacy) queda como riesgo pendiente fuera de este cambio.

## Impact

- Código: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java` (consulta de credenciales) y, si es necesario, `AuthResource.java` sin cambiar su contrato.
- Pruebas: `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`; meta de cobertura del código modificado de 85% o superior (`AGENTS.md`, auth, punto 7).
- Validación esperada: `python tools/lab.py test auth` desde la raíz (o `mvn test` desde `auth/`).
- APIs: sin cambios en rutas ni en el contrato de `POST /auth/login`.
- Dependencias: ninguna nueva; se usa JDBC ya presente.
