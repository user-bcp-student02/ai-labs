# Tasks

Componente: `auth`. Todas las pruebas se ejecutan desde la raíz con `python tools/lab.py test auth` (equivale a `mvn verify` en `auth/`). Cambios limitados a `auth/`.

## 1. Línea base y pruebas de caracterización

- [x] 1.1 Ejecutar `python tools/lab.py test auth` sobre el código actual y registrar el resultado como línea base; verificar que termina con código 0 (si falta JDK 21 o Maven 3.9+, registrar el bloqueo y no continuar con la sección 3).
- [x] 1.2 Agregar en `AuthResourceTest` una prueba de caracterización del 200 que verifique `token` no vacío, `expiresIn` igual a 3600, `user.username`, `user.role` igual a `STUDENT` y `debug`; verificar que pasa sin cambios en código funcional.
- [x] 1.3 Agregar pruebas de caracterización del 400 para cuerpo nulo (`Request is null`), `username` en blanco (`Username is required`) y `password` en blanco (`Password is required`); verificar que las tres pasan sin cambios en código funcional.
- [x] 1.4 Agregar pruebas de caracterización del 401 con contraseña incorrecta para `student` y con usuario inexistente, verificando que la respuesta no contiene `token`; verificar que pasan sin cambios en código funcional.
- [x] 1.5 Agregar una prueba que verifique que `user.displayName` es `Workshop Student` en el login de `student`; verificar que pasa. Nota: los datos sembrados no contienen caracteres HTML, así que el escape no se puede observar con estas pruebas; esa limitación se documenta en la revisión.

## 2. Reproducción de la vulnerabilidad (pruebas que deben fallar antes del cambio)

- [x] 2.1 Agregar una prueba con `username` `student` y `password` `' OR '1'='1` que espere 401 sin `token`; verificar que falla hoy con 200, que es la explotación esperada.
- [x] 2.2 Agregar una prueba con `username` `' OR 1=1 --` y una contraseña cualquiera que espere 401 sin `token`; verificar que falla hoy con 200.
- [x] 2.3 Agregar una prueba con `username` `' OR '1'='1` y `password` `' OR '1'='1` que espere 401 sin `token`; verificar que falla hoy con 200.
- [x] 2.4 Agregar una prueba con `username` `' OR '1'='1` y `password` `cualquiera` que espere 401 sin `token`; verificar que pasa hoy, porque ya responde 401, y que se mantiene como guarda de regresión.
- [x] 2.5 Agregar una prueba con un `username` que contiene comilla simple (por ejemplo `stu'dent`) y contraseña `Workshop123!` que espere 401 sin `token`; verificar que pasa hoy y que se mantiene como guarda de regresión.

## 3. Implementación incremental

- [x] 3.1 Extraer la consulta de credenciales a una constante con nombre en `LegacyDatabase` usando parámetros `?` para `username` y `password`; verificar que el archivo no contiene concatenación de `username` ni `password` en el SQL con `grep` sobre `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`.
- [x] 3.2 Cambiar `findByCredentials` a `PreparedStatement` con `setString(1, username)` y `setString(2, password)`, manteniendo firma, tipo de retorno y el `catch` que devuelve `null`; verificar que `python tools/lab.py test auth` compila.
- [x] 3.3 Verificar que las pruebas de 2.1 a 2.3 ahora pasan y que todas las pruebas de caracterización de la sección 1 siguen en verde; verificar con `python tools/lab.py test auth`.
- [x] 3.4 Confirmar que `AuthResource.java`, `LegacyTokenUtil.java`, `application.properties` y `pom.xml` no tienen cambios; verificar con `git --no-pager diff --stat -- auth/`.

## 4. Calidad y cobertura

- [x] 4.1 Revisar el reporte de JaCoCo generado por `python tools/lab.py test auth` y verificar que `LegacyDatabase.findByCredentials` tiene cobertura de líneas de 85% o más.
- [x] 4.2 Verificar que no se agregaron supresiones ni exclusiones de CodeQL, Dependency Review o JaCoCo (`@SuppressWarnings`, exclusiones en `pom.xml`, reglas en workflows) con `git --no-pager diff -- auth/ .github/`.
- [x] 4.3 Verificar que el contrato de `POST /auth/login` no cambió comparando las respuestas de las pruebas de la sección 1 con la línea base de 1.1.

## Workflow follow-up

- Revisión humana de los cambios de `auth/` antes de cualquier commit. En Día 1 no hacer push ni crear PR.
- Crear commits por grupo (caracterización, reproducción, implementación) según `design.md`, solo tras la aprobación humana, con el trailer de Copilot.
- Limitación conocida: el escenario 500 de la spec (`Fallo inesperado fuera de la verificación`) no tiene prueba automatizada porque requiere forzar una excepción al construir la respuesta; se verifica por revisión de código.
- Archivar el change con `openspec-archive-change` cuando la implementación esté aprobada y verificada.

## Evidence

- Baseline: `python tools/lab.py test auth` initially failed because port 8081 was already bound (PID 5548); no process was stopped. With `QUARKUS_HTTP_TEST_PORT=8181`, the same command completed successfully: 10 tests, 0 failures, 0 errors, 0 skipped. The pre-existing apostrophe username case logged the expected SQLite syntax error and returned 401.
- Injection reproduction: `python tools/lab.py test auth` with `QUARKUS_HTTP_TEST_PORT=8181` ran 13 tests; the three new cases (password injection, username comment injection, and injection in both fields) each received 200 instead of 401, confirming the expected vulnerable behavior.
- Final verification: `python tools/lab.py test auth` with `QUARKUS_HTTP_TEST_PORT=8181` completed successfully: 13 tests, 0 failures, 0 errors, 0 skipped.
- SQL check: `Select-String` found no concatenation of `username` or `password`; `rg` was unavailable in the terminal.
- Scope and controls: `git --no-pager diff --stat -- auth/` showed only `LegacyDatabase.java` and `AuthResourceTest.java`; the excluded AuthResource, token utility, properties, and POM had no diff. `git --no-pager diff -- auth/ .github/` showed no new suppressions or quality-gate exclusions.
- JaCoCo: `LegacyDatabase.findByCredentials` has 9 covered and 1 missed line (90%). The section 1 response assertions passed both at baseline and after implementation, preserving the login contract.
