# Plan

## auth

### Aprobación humana

| ID | Decisión | Revisor/a | Rol | Fecha | Evidencia |
|---|---|---|---|---|---|
| H-AUTH-01 | Aprobado para implementar según el plan | Walter Marcelo | LT | 2026-10-26 | ok |
| H-AUTH-02 | Aprobado para implementar según el plan | Walter Marcelo | LT | 2026-10-26 | Confirmación explícita del revisor en esta conversación |
| H-AUTH-03 | Aprobado para implementar según el plan | Walter Marcelo | LT | 2026-10-26 | Confirmación explícita del revisor en esta conversación |
| H-AUTH-04 | Aprobado para implementar según el plan | Walter Marcelo | LT | 2026-10-26 | Confirmación explícita del revisor en esta conversación |
| H-AUTH-05 | Aprobado para implementar según el plan | Walter Marcelo | LT | 2026-10-26 | Confirmación explícita del revisor en esta conversación |
| H-AUTH-06 | Aprobado para implementar según el plan | Walter Marcelo | LT | 2026-10-26 | Confirmación explícita del revisor en esta conversación |
| H-AUTH-07 | Aprobado para implementar según el plan | Walter Marcelo | LT | 2026-10-26 | Confirmación explícita del revisor en esta conversación |

### Selección

La selección inicial incluyó H-AUTH-01 a H-AUTH-07, todos aprobados en la tabla
anterior; no se proporcionó motivo de priorización. H-AUTH-01, 02, 04 y 06 se
implementaron; H-AUTH-03 se implementó tras confirmar aceptación externa de
JWT HS256 y provisión del secreto por configuración; H-AUTH-05 queda parcial,
pendiente de la allowlist CORS por entorno; H-AUTH-07 queda pendiente del
análisis SCA de Quarkus.

### Contexto y límites del contrato

`docs/ENDPOINTS.md` no existe. El contrato actualmente observable es:
`POST /auth/login`; request JSON con `username` y `password`; campos ausentes o
vacíos producen 400, credenciales sin coincidencia producen 401, y el éxito
produce 200 con `token`, `expiresIn`, `user` y `debug`
(`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:22-49`). Las
propuestas conservan ruta, códigos de resultado y forma de éxito. Cuando un
hallazgo requiere cambiar una exposición insegura (H-AUTH-04) o el formato
opaco del token (H-AUTH-03), se identifica expresamente como cambio de
implementación; antes de desplegar un formato de token nuevo se debe coordinar
compatibilidad con sus consumidores externos, que no se inspeccionan en este
alcance. Para H-AUTH-03 se confirmó que aceptan JWT HS256; el entorno deberá
proveer `AUTH_JWT_SECRET` antes del despliegue. No se verificó el entorno de
despliegue ni se recibió el valor del secreto.

Antes de cambiar persistencia o tokens, agregar pruebas de caracterización
para la respuesta actual y ejecutarlas con datos aislados/locales. Las pruebas
previas y posteriores de H-AUTH-01 a H-AUTH-07 se registran abajo. Para
H-AUTH-03 se confirmó que consumidores externos aceptan JWT HS256 y que el
entorno proveerá la clave; no se recibió su valor. H-AUTH-05 no se cierra hasta
definir allowlist CORS por entorno. Mantener los umbrales y scanners
existentes; no agregar dependencias hasta justificar una necesidad concreta.
Los cambios deben seguir `docs/GOOD_PRACTICES.md` y las reglas auth de
`AGENTS.md`: capas Resource → Service → Repository, métodos con una
responsabilidad, guard clauses, nombres explícitos, sin duplicación ni valores
mágicos.

### H-AUTH-01 — Concatenación de credenciales en SQL

- **Hallazgo / problema:** Las credenciales del request se concatenan al SQL y
  se ejecutan con `Statement`, lo que permite que comillas alteren la consulta
  (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:35`;
  `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:31-35`).
- **Cambio propuesto:** Sustituir la consulta de login por SQL constante con
  placeholders en `PreparedStatement`, enlazando usuario y contraseña con
  `setString`. Mantener la semántica de búsqueda y los resultados HTTP
  observados; no añadir escapes manuales ni concatenación.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`;
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y, si se
  separa el acceso para probarlo, una prueba de repositorio bajo
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Prueba de caracterización antes del cambio:** En SQLite local aislado,
  caracterizar login válido (200 y campos de éxito existentes), credenciales
  incorrectas (401) y campos requeridos (400). Luego añadir primero la prueba
  de regresión de un usuario de prueba cuyo nombre contiene una comilla,
  insertado como valor literal en la base de prueba: debe poder autenticarse
  con el valor exacto sin que el texto se interprete como SQL. No usar
  payloads contra servicios remotos.

### H-AUTH-02 — Contraseñas persistidas en texto plano

- **Hallazgo / problema:** La columna `password` es texto y los usuarios
  iniciales se insertan con credenciales literales
  (`auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:22-24`).
  Una copia o lectura de la base expone las contraseñas.
- **Cambio propuesto:** Almacenar/verificar hashes lentos con salt, siguiendo
  `docs/GOOD_PRACTICES.md` (por ejemplo BCrypt); no guardar ni registrar
  contraseñas. Se eligió migración diferida al primer login válido para filas
  preexistentes, y PBKDF2-HMAC-SHA256 de la JDK con salt aleatorio para evitar
  nueva dependencia. Las cuentas demo se siembran solo en los perfiles dev y
  test; los demás perfiles no crean esas cuentas y eliminan las cuentas
  predeterminadas existentes solo si la contraseña aún coincide con el valor
  conocido (hash o texto legado). Se preservan las credenciales modificadas.
  El operador debe provisionar usuarios de producción por un mecanismo
  separado antes de habilitar el login; ese mecanismo no forma parte del
  alcance.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`
  o el futuro repositorio; nueva clase de seguridad/hash bajo
  `auth/src/main/java/com/bcp/workshop/auth/` si se separa esa responsabilidad;
  pruebas en `auth/src/test/java/com/bcp/workshop/auth/`; posiblemente
  `auth/pom.xml` solo si la solución elegida requiere una dependencia
  justificada.
- **Prueba de caracterización antes del cambio:** En una base de prueba
  aislada, verificar que las credenciales semilla actuales producen 200, que
  una contraseña incorrecta produce 401 y que la respuesta conserva sus
  campos observados. Antes de implementar la transición, agregar pruebas para
  autenticar una fila heredada y una fila nueva, comprobar que el valor
  persistido no es la contraseña literal y que inicializar/migrar dos veces no
  rompe el login. No considerar la prueba de almacenamiento seguro como
  aprobada hasta ejecutarla.

### H-AUTH-03 — Token Base64 con secreto embebido y sin firma

- **Hallazgo / problema:** El token concatena username, rol, expiración y un
  secreto estático, y luego solo lo codifica con Base64
  (`auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8,12-15`).
  La codificación no ofrece firma verificable.
- **Cambio propuesto:** Emitir un token firmado con expiración y claims
  explícitos; conservar el campo `token`, la respuesta HTTP 200 y el valor
  contractual de expiración `expiresIn` (3600 segundos), usando una constante
  con nombre. Obtener las claves/secreto de configuración externa, nunca de
  un literal en código. Seleccionar mecanismo y dependencia solo tras
  justificarlo; no tratar Base64 como firma. Se confirmó que los consumidores
  externos aceptan JWT HS256 y que el entorno desplegará el secreto mediante
  configuración externa. Se implementó HS256 con JCA y Jackson ya disponible,
  sin dependencia adicional; el valor real no se pidió ni almacenó.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`
  o el nuevo servicio de token bajo `auth/src/main/java/com/bcp/workshop/auth/`;
  `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java` si se inyecta el
  servicio; `auth/src/main/resources/application.properties`; pruebas bajo
  `auth/src/test/java/com/bcp/workshop/auth/`; `auth/pom.xml` solo si la
  biblioteca elegida lo requiere.
- **Prueba de caracterización antes del cambio:** Caracterizar login válido y
  verificar que la respuesta conserva `token`, `expiresIn`, `user` y `debug`,
  con `expiresIn` de 3600; caracterizar también 401 para credenciales
  inválidas. Antes de sustituir el formato, añadir pruebas para verificar
  firma, claims username/role, expiración y rechazo de token alterado o
  vencido. Esas pruebas del formato firmado son regresión de la solución, no
  resultados actuales.

### H-AUTH-04 — La respuesta de error expone detalles internos

- **Hallazgo / problema:** El 500 incluye el mensaje de excepción y su nombre
  de clase en la respuesta
  (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:54-58`).
- **Cambio propuesto:** Devolver un mensaje genérico al cliente y enviar el
  detalle al log del servidor de forma controlada, conforme a
  `docs/GOOD_PRACTICES.md`. Conservar el código HTTP 500. La eliminación de
  detalles del cuerpo es el cambio intencional que corrige el hallazgo; no se
  conserva la exposición observada.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`
  o el manejador de excepciones que se introduzca; pruebas bajo
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Prueba de caracterización antes del cambio:** Caracterizar primero 400
  para request/campos requeridos, 401 para credenciales inválidas y 200 para
  login válido. Añadir prueba controlada que provoque un error interno y
  registre el comportamiento actual como línea base; tras la corrección,
  verificar 500 y mensaje genérico sin mensaje/clase interna en el cuerpo. La
  aserción posterior debe reflejar el cambio de seguridad, no congelar el
  detalle filtrado.

### H-AUTH-05 — Secreto estático y CORS permisivo

- **Hallazgo / problema:** El secreto se define como literal en la utilidad del
  token y CORS está habilitado con origen `/.*/`
  (`auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8`;
  `auth/src/main/resources/application.properties:4-7`).
- **Cambio propuesto:** Leer el secreto y la lista de orígenes permitidos desde
  configuración externa, sin valores secretos por defecto en el código.
  H-AUTH-03 ya externalizó el secreto como `AUTH_JWT_SECRET`, sin fallback
  sensible, y valida longitud mínima de 32 bytes. Sigue pendiente cambiar
  CORS `/.*/` por una allowlist explícita por entorno y mantener los
  métodos/headers observados. La persona responsable confirmó que la allowlist
  aún no está definida; no se inventan orígenes productivos. La prueba usará
  orígenes ficticios una vez definido el conjunto esperado.
- **Archivos a tocar:** `auth/src/main/resources/application.properties`;
  `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java` o el nuevo
  servicio de token; pruebas bajo
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Prueba de caracterización antes del cambio:** Caracterizar 200 para login
  válido y la respuesta con token, además del comportamiento CORS actual en
  un entorno local. Antes de cambiar configuración, añadir pruebas locales con
  origen permitido y no permitido según una allowlist de prueba explícita;
  verificar que el secreto no está embebido en el token/código y que falta de
  configuración se trata según una política explícita, no con un secreto
  silencioso de respaldo. La allowlist real sigue pendiente de coordinación.

### H-AUTH-06 — Responsabilidades mezcladas en AuthResource

- **Hallazgo / problema:** `login` valida, consulta persistencia, toma
  decisiones de negocio, genera el token y arma la respuesta dentro de un
  método con condicionales anidados
  (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:24-60`).
- **Cambio propuesto:** Separar Resource (HTTP), servicio (reglas de login),
  repositorio (SQLite) y seguridad/token; dejar en el Resource la traducción
  entre request, resultado de servicio y respuesta. Usar métodos de una
  responsabilidad, guard clauses, nombres claros y constantes con nombre.
  Reutilizar los servicios/repositorio introducidos al resolver H-AUTH-01 a
  H-AUTH-05; evitar duplicar SQL, hashing o construcción/verificación de
  tokens.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`,
  `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java` y nuevas
  clases `AuthService`/repositorio/DTO solo donde cada responsabilidad lo
  requiera; pruebas bajo `auth/src/test/java/com/bcp/workshop/auth/`.
- **Prueba de caracterización antes del cambio:** Ampliar primero las pruebas
  de recurso para 400 en entradas ausentes/vacías, 401 en credenciales
  inválidas y 200 con la forma de respuesta actual para login válido.
  Ejecutarlas antes de mover lógica; después repetirlas contra el flujo por
  capas y añadir pruebas unitarias por servicio/repositorio sin duplicar las
  pruebas HTTP.

### H-AUTH-07 — Versiones de dependencias requieren análisis SCA

- **Hallazgo / problema:** `auth/pom.xml` declara Quarkus 3.27.4 y Commons
  Text 1.9 (`auth/pom.xml:13,49-50`). La matriz no confirma CVE; el reto pide
  investigar advisories.
- **Cambio propuesto:** Revisar advisories y árbol de dependencias resuelto;
  actualizar o retirar únicamente dependencias con una justificación basada
  en evidencia, sin silenciar scanners. Commons Text se usa en el recurso
  para escapar HTML (`auth/src/main/java/com/bcp/workshop/auth/AuthService.java:4,24`),
  por lo que retirarla exige decidir una alternativa y caracterizar el valor
  `displayName` de la respuesta para no alterar su representación sin
  revisión. Commons Text se actualizó de 1.9 a 1.15.0: la página oficial de
  Apache documenta CVE-2022-42889 en versiones anteriores a 1.10.0 cuando se
  utiliza `StringSubstitutor` con entrada no confiable. El código de este
  componente usa `StringEscapeUtils`, así que no se afirma que esa ruta
  vulnerable se ejecute. El árbol local confirma Quarkus 3.27.4, pero no se
  encontró evidencia que demuestre aplicabilidad de advisories de Red Hat
  build a este artefacto upstream. Los scanners locales no están disponibles;
  CodeQL y Dependency Review remotos no se ejecutan en este recorrido. Por
  ello H-AUTH-07 queda pendiente de análisis SCA verificable.
- **Archivos a tocar:** `auth/pom.xml` si la evidencia requiere actualizar o
  retirar versiones; `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`
  solo si se justifica reemplazar/eliminar el uso de Commons Text; pruebas
  bajo `auth/src/test/java/com/bcp/workshop/auth/` si se afecta la respuesta.
- **Prueba de caracterización antes del cambio:** Registrar el árbol de
  dependencias y resultados de escáneres disponibles, ejecutar la suite
  existente y caracterizar `displayName` en una respuesta exitosa. Después de
  cualquier cambio justificado, repetir la suite, verificar el árbol
  actualizado y volver a ejecutar los scanners aplicables. Si no hay advisory
  confirmado o herramienta disponible, registrar el pendiente; no afirmar que
  se corrigió.

### Secuencia propuesta y pendientes

1. Se caracterizó el login existente en SQLite local aislado antes de cambiar
   SQL, persistencia, errores, capas y dependencia de escape HTML. Ver los
   resultados ejecutados abajo.
2. H-AUTH-01, 02, 03, 04 y 06 están implementados. H-AUTH-03 conserva forma
   HTTP/códigos y `expiresIn=3600`; usa `AUTH_JWT_SECRET` en runtime y secreto
   ficticio únicamente en la configuración de pruebas. H-AUTH-07 se mitigó
   parcialmente actualizando Commons Text; no se marca completo sin
   scanner/evidencia específica para Quarkus upstream.
3. H-AUTH-05 queda parcial: el propietario debe definir la allowlist CORS por
   entorno antes de restringir `quarkus.http.cors.origins`; no se cambió el
   patrón permisivo sin esos datos.
4. Continuar H-AUTH-07 con un scanner SCA disponible y registrar hallazgos
   atribuibles a versiones exactas. CodeQL y Dependency Review remotos quedan
   no ejecutados. Registrar futuras pruebas en `docs/TESTING.md` cuando
   corresponda; no se creó ese artefacto en este paso.

Pendientes de coordinación: allowlist CORS por entorno para H-AUTH-05. Se
confirmó que los consumidores externos aceptan JWT HS256 y que el entorno
proveerá `AUTH_JWT_SECRET` para H-AUTH-03; el valor real no fue compartido. Para
H-AUTH-07 sigue pendiente un scanner SCA que verifique el árbol transitivo
Quarkus de este artefacto upstream. La configuración de producción ya no
siembra usuarios demo; el operador debe provisionar cuentas legítimas mediante
un procedimiento separado antes de habilitar el login en ese entorno. Ese
proceso no está implementado en este alcance. Las aprobaciones humanas para
H-AUTH-01 a H-AUTH-07 están registradas arriba.

### Registro de ejecución

| Fecha | Componente | IDs | Resultado |
|---|---|---|---|
| 2026-10-26 | auth | H-AUTH-01 a H-AUTH-07 | Bloqueado en el intento inicial: no había evidencia de aprobación en ese momento. No se ejecutaron pruebas en ese intento. |
| 2026-10-26 | auth | H-AUTH-01 | Aprobación registrada arriba para este ID. Antes del cambio, `mvn test` pasó con la prueba original (1/1). Tras agregar caracterización, `mvn test` tuvo 1 fallo esperado de 5: el usuario ficticio cuyo nombre contiene comilla recibió 401 en vez de 200; los otros cuatro casos pasaron. La primera `mvn verify` posterior al cambio falló al compilar por falta del import `PreparedStatement`, corregido enseguida. Verificación final: `mvn verify` pasó (5/5). JaCoCo: `LegacyDatabase.java` 20/23 líneas cubiertas (87%); la ruta de consulta y binding quedó cubierta. La SQLite temporal se ubicó en `auth/target`. CodeQL y Dependency Review remotos: no ejecutados. |
| 2026-10-26 | auth | H-AUTH-02 | Antes de la mitigación inicial, caracterización HTTP `mvn -Dtest=AuthResourceTest#logsInWithDefaultTrainerAccount test` pasó (1/1): la cuenta fija `trainer` / `Trainer123!` autenticaba con rol TRAINER. Después, perfiles dev/test mantienen la semilla para el laboratorio, mientras la configuración normal deshabilita el sembrado y elimina únicamente las cuentas predeterminadas cuya contraseña aún coincide; conserva cuentas con contraseña cambiada. Se añadió `LegacyDatabaseTest` para base nueva sin semillas, eliminación de filas legadas/en hash y preservación de credencial personalizada. La primera `mvn clean verify` falló porque una prueba manual de reinicialización no activaba el flag de perfil y el test standalone necesitaba registrar el driver SQLite; se corrigieron esos fixtures. Verificación final: `mvn clean verify` pasó, 17/17 (14 AuthResourceTest, 1 LegacyDatabaseTest, 2 PasswordHasherTest). JaCoCo: LegacyDatabase 75/81 líneas (92.6%); PasswordHasher 32/34 (94.1%); JwtTokenService 40/44 (90.9%); AuthResource 24/25 (96%); AuthService 11/11 (100%). Base aislada en `auth/target/auth-default-credentials-fix.db`; no se añadieron dependencias. |
| 2026-10-26 | auth | H-AUTH-02 | Revisión local encontró que las credenciales demo fijas, aun hasheadas, podían autenticar en una instalación de producción nueva. Antes de la corrección, `mvn -Dtest=AuthResourceTest#logsInWithDefaultTrainerAccount test` pasó (1/1), confirmando el login `trainer` / `Trainer123!` con rol TRAINER. Corrección: el sembrado se habilita solo en `%dev`/`%test`; en perfiles normales se eliminan cuentas predeterminadas existentes solo cuando la contraseña almacenada (hash o legado en claro) aún coincide con el valor demo, preservando cuentas con contraseña cambiada. Prueba aislada `LegacyDatabaseTest` cubre una base nueva sin semillas, limpieza y preservación. La primera `mvn clean verify` tras el cambio falló por fixture de reinicialización sin flag de test y por carga JDBC fuera de Quarkus; ambos fixtures se ajustaron. Resultado final: `mvn clean verify` pasó, 17/17; cobertura `LegacyDatabase.java` 75/81 líneas (92.6%). Pendiente operativo: provisionar usuarios reales de producción por mecanismo separado; no se implementa en este alcance. |
| 2026-10-26 | auth | H-AUTH-04 | Antes del cambio, prueba directa controlada confirmó HTTP 500 con mensaje y clase interna. Después, `mvn verify` pasó (6/6 en esa etapa); respuesta genérica verificada y detalle registrado por logger solo en servidor. JaCoCo AuthResource 25/26 líneas (96.2%) en esa ejecución. |
| 2026-10-26 | auth | H-AUTH-06 | Antes del cambio, caracterización HTTP pasó (7/7) para 200, 400, 401 y cuerpo de éxito. Después de mover autenticación/DTO a AuthService, `mvn verify` pasó (7/7). JaCoCo: AuthResource 24/25 (96%), AuthService 11/11 (100%), LoginResponse 2/2 (100%). |
| 2026-10-26 | auth | H-AUTH-07 | Antes del cambio, la prueba de displayName escapado pasó (1/1) con Commons Text 1.9. Después de actualizar a 1.15.0, `mvn verify` pasó (11/11). `mvn dependency:tree` confirma Commons Text 1.15.0 y Quarkus 3.27.4. Apache documenta CVE-2022-42889 antes de 1.10.0 bajo uso de StringSubstitutor con entrada no confiable: https://commons.apache.org/proper/commons-text/security.html. Maven Central metadata reporta 1.15.0 como latest/release: https://repo.maven.apache.org/maven2/org/apache/commons/commons-text/maven-metadata.xml. Los avisos Red Hat consultados son para Red Hat build of Quarkus, no se atribuyen a este artefacto upstream: https://access.redhat.com/errata/RHSA-2026:36002 y https://access.redhat.com/errata/RHSA-2026:23808. No se encontró scanner local; CodeQL y Dependency Review remotos: no ejecutados. H-AUTH-07 permanece Pendiente para el análisis de Quarkus/transitivos. |
| 2026-10-26 | auth | H-AUTH-03 | Antes del cambio, `mvn verify` pasó con el flujo de login existente (12/12); tras añadir la prueba de caracterización JWT, su ejecución pre-cambio falló como se esperaba: token antiguo tenía 1 segmento Base64 en vez de 3 segmentos JWT (`expected: <3> but was: <1>`). Después del cambio, `mvn clean verify` pasó (15/15), incluida validación HS256/firma, claims `username`/`sub`/`role`, expiración de 3600 segundos, rechazo de firma alterada, token vencido, formato inválido, algoritmo distinto y secreto corto. JaCoCo: JwtTokenService 40/44 líneas (90.9%); AuthService 11/11 (100%); AuthResource 24/25 (96%). Se usó clave ficticia solo en `auth/src/test/resources/application.properties`; runtime exige `AUTH_JWT_SECRET`, y el secreto de despliegue no fue compartido. Sin dependencia nueva. |
| 2026-10-26 | auth | H-AUTH-05 | Se confirmó que el entorno proveerá externamente el secreto, que H-AUTH-03 configura como `AUTH_JWT_SECRET`; el valor no fue solicitado ni compartido. La allowlist CORS se respondió como pendiente, por lo que no se cambió `quarkus.http.cors.origins=/.*/` ni se ejecutó prueba de CORS. H-AUTH-05 sigue Pendiente; queda acción al propietario del despliegue para definir orígenes por entorno. |
| 2026-10-26 | auth | H-AUTH-01, H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-06 | Se completaron pruebas para `GET /q/health` y todas las respuestas documentadas de `POST /auth/login`, incluidos JSON mal formado (400) y `Content-Type: text/plain` (415) como observaciones de transporte, no como contrato estable. Una primera ejecución de `mvn clean verify` falló con 6 fallos y 2 errores porque `@InjectMock AuthService` sustituía el servicio para toda la clase; se cambió a `@InjectSpy` para limitar el stub de error 500 y preservar el servicio real en los demás casos. Verificación final desde `auth/`: `mvn clean verify`, con `AUTH_DB_URL=jdbc:sqlite:./target/auth-endpoint-tests-final.db`, código 0; 22 pruebas, 0 fallos, 0 errores y 0 omitidas. JaCoCo 0.8.12, cobertura de líneas: AuthResource 25/25 (100%), AuthService 11/11 (100%), LegacyDatabase 74/80 (92.5%), PasswordHasher 32/34 (94.1%), JwtTokenService 40/44 (90.9%). Se creó `docs/TESTING.md`; CodeQL, Dependency Review y análisis SCA no se ejecutaron. |
