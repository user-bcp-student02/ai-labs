# Design

## Context

El login es `POST /auth/login` → `AuthResource.login` → `LegacyDatabase.findByCredentials(username, password)` → SQLite (`legacy_users`). Ver [proposal.md](./proposal.md) para el motivo y [specs/auth-login/spec.md](./specs/auth-login/spec.md) para los requisitos.

Estado actual relevante:

- `findByCredentials` (`auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`) concatena `username` y `password` en el SQL y usa `Statement`. Captura cualquier excepción, la imprime y devuelve `null`.
- `AuthResource.login` traduce `null` en 401 y cualquier excepción no controlada en 500. Por eso, un fallo de base de datos hoy llega como 401, no como 500.
- `DB_URL` es un `static final` leído de `AUTH_DB_URL` (por defecto `jdbc:sqlite:./data/workshop.db`). `@PostConstruct` crea la tabla y siembra `student` y `trainer`.
- `AuthResourceTest` es un `@QuarkusTest` con un único caso (login válido de `student`). Usa la base real del directorio `auth/`.
- `pom.xml` ya incluye `sqlite-jdbc`, `quarkus-junit5` y JaCoCo; `tools/lab.py test auth` ejecuta `mvn verify`.
- Análisis de la consulta actual: con `username=' OR '1'='1` y `password=cualquiera` la expresión `username='' OR '1'='1' AND password='cualquiera'` evalúa a falso, por lo que devuelve 401. La explotación real aparece en `password=' OR '1'='1` (`... AND password='' OR '1'='1'` es verdadero) y en `username=' OR 1=1 --`, donde el comentario anula la condición de contraseña.

## Goals / Non-Goals

**Goals:**

- Ejecutar la verificación de credenciales con `PreparedStatement` y parámetros `?` para `username` y `password`.
- Mantener sin cambios el contrato de `POST /auth/login` descrito en la propuesta (rutas, códigos, campos, mensajes).
- Cerrar la regresión con pruebas que fallen antes del cambio y pasen después.
- Cambio mínimo y revisable, limitado a `auth/`.

**Non-Goals:**

- No cambiar el formato del token (`LegacyTokenUtil`), ni los valores `3600`, ni la respuesta `debug`.
- No refactorizar `AuthResource` en capas (servicio/repositorio). Es deseable según `AGENTS.md`, pero iría en un cambio aparte.
- No cambiar el manejo de excepciones de `findByCredentials` (ver Decisión 2).
- No migrar a hash de contraseñas, no cambiar el esquema ni la siembra de datos.
- No tocar la configuración de pruebas (`AUTH_DB_URL`, surefire, JaCoCo) ni CI.

## Decisions

### 1. Consulta parametrizada con `PreparedStatement`

Se usa `connection.prepareStatement("SELECT id, username, display_name, role FROM legacy_users WHERE username = ? AND password = ?")` y `setString(1, username)`, `setString(2, password)`. Es la técnica que pide el Issue #24 y no requiere dependencias nuevas.

Alternativas consideradas:

- **Escapar comillas manualmente** (`replace("'", "''")`): descartado; es frágil frente a otras formas de inyección y no cumple el criterio "no concatena valores".
- **JPA/Hibernate o un ORM**: descartado; añade dependencias y reescribe la capa de datos, fuera del alcance.
- **Mantener `Statement` con validación de caracteres**: descartado; las listas de bloqueo no son una garantía.

### 2. Preservar el manejo de excepciones de `findByCredentials`

Se mantiene el `catch` que imprime y devuelve `null`. Así un fallo de SQLite sigue llegando como 401, igual que hoy, y no se altera el contrato. Alternativa descartada: propagar la excepción para que el recurso responda 500. Cambiaría un código observable sin que lo pida el Issue.

Consecuencia: el escenario "Fallo inesperado durante la verificación → 500" de la spec no se alcanza mediante un error de base de datos. Requiere una decisión (ver Open Questions).

### 3. Alcance de cambio en código: solo `LegacyDatabase.findByCredentials`

`AuthResource` no cambia: el `null` como señal de "no autenticado" se mantiene, y la respuesta 401 que ya existe cubre la inyección. Así el contrato de respuesta queda intacto por construcción.

### 4. Orden de pruebas: caracterizar, luego reproducir, luego corregir

Las pruebas de `AuthResourceTest` se agregan antes de tocar `findByCredentials`:

1. **Caracterización (deben pasar con el código actual):** 200 con `token`, `expiresIn=3600`, `user.username`, `user.displayName`, `user.role=STUDENT`, `debug`; 400 con `Request is null`, `Username is required`, `Password is required` (incluyendo espacios en blanco); 401 con contraseña incorrecta y con usuario inexistente, sin `token`.
2. **Reproducción de la vulnerabilidad (deben fallar hoy):** `password=' OR '1'='1` con usuario `student` debe responder 401 sin `token` (hoy 200). Se agrega `username=' OR 1=1 --` con una contraseña cualquiera (hoy 200). No se usa como prueba roja el `username=' OR '1'='1` con `password=cualquiera`, porque ya devuelve 401.
3. **Cobertura de regresión:** `username=' OR '1'='1` con `password=' OR '1'='1` debe responder 401; el caso con comillas dentro de un valor válido debe tratarse como literal.

Después del cambio, las pruebas de la fase 2 y 3 deben pasar y las de la fase 1 no deben cambiar.

### 5. Base de datos de prueba

Se mantiene la base real del directorio `auth/` porque las pruebas de caracterización dependen del usuario sembrado y ninguna prueba escribe. No se introduce `AUTH_DB_URL` de prueba en esta fase; si se quiere aislar, se hace en un cambio de configuración aparte.

## Contratos protegidos

| Contrato | Verificación |
|---|---|
| Ruta `POST /auth/login`, `Content-Type` JSON | Pruebas de caracterización existentes y nuevas |
| 200: `token`, `expiresIn=3600`, `user{username, displayName, role}`, `debug` | Caracterización |
| 400: mensajes `Request is null`, `Username is required`, `Password is required` | Caracterización |
| 401 sin `token` ante credenciales no válidas o inyección | Reproducción y caracterización |
| `displayName` escapado HTML | Caracterización (valor `Workshop Student` no cambia; el escape se conserva en código) |
| Esquema `legacy_users` y siembra de `student`/`trainer` | Sin cambios en código; las pruebas dependen de ella |

## Componentes afectados

- `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`: método `findByCredentials` (cambio funcional).
- `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`: pruebas de caracterización y de inyección.
- No afectados: `AuthResource.java`, `LegacyTokenUtil.java`, `UserRecord.java`, `LoginRequest.java`, `application.properties`, `pom.xml`, `api/`, `web/`.

## Estrategia incremental

1. Agregar las pruebas de caracterización y ejecutarlas: deben pasar sin cambios de código.
2. Agregar las pruebas de inyección y confirmar que fallan por la razón esperada (200 en lugar de 401).
3. Cambiar `findByCredentials` a `PreparedStatement` manteniendo firma, tipo de retorno y `catch`.
4. Ejecutar `python tools/lab.py test auth` desde la raíz (o `mvn verify` en `auth/`) y revisar JaCoCo: el código modificado debe quedar en 85% o más.
5. Revisar que CodeQL no pierda el hallazgo por supresiones; no se agregan `@SuppressWarnings` ni exclusiones.

Cada paso es un commit revisable.

## Risks / Trade-offs

- **[Riesgo] La spec exige 500 ante fallos no controlados, pero un error de BD produce 401.** → Mitigación: se preserva el comportamiento actual y la spec se ajustó en consecuencia (ver Resolved Questions).
- **[Riesgo] Las pruebas usan la base real del directorio `auth/`.** → Mitigación: las pruebas solo leen; si otra prueba escribe en el futuro, se aísla con `AUTH_DB_URL`.
- **[Riesgo] La prueba de inyección depende de SQLite.** → Trade-off aceptado: el código y el driver son SQLite; el caso de inyección se valida contra ese motor.
- **[Trade-off] `AuthResource` mantiene su mezcla de validación, acceso a datos y respuesta.** → Se deja para un cambio posterior; este cambio no lo amplía.
- **[Riesgo] El token legacy sigue aceptado por `api/` según `docs/ENDPOINTS.md`.** → Fuera de alcance; ya registrado en la propuesta.

## Migration Plan

No hay migración de datos ni de esquema. Los usuarios sembrados y las contraseñas en texto plano no cambian; el cambio no altera `legacy_users`.

**Rollback:** `git revert` del commit de `LegacyDatabase.findByCredentials` (y de las pruebas de inyección si se desea). Esto restaura la concatenación y reabre la vulnerabilidad H-AUTH-01, por lo que el rollback solo se justifica como medida temporal mientras se corrige un problema operativo. No requiere limpieza de datos porque no se escribe nada nuevo.

## Resolved Questions

- **Fallo de base de datos durante el login.** Decidido por el equipo: mantener 401 sin `token` (Decisión 2). La spec se ajustó: el escenario de fallo de consulta responde 401, y el 500 queda para errores no controlados fuera de la consulta de credenciales.
