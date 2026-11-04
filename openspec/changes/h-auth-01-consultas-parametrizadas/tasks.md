# Tasks

Componente: `auth`. Todas las pruebas se ejecutan desde la raíz con `python tools/lab.py test auth` (equivale a `mvn verify` en `auth/`). Cambios limitados a `auth/`.

## 1. Línea base y pruebas de caracterización

- [ ] 1.1 Ejecutar `python tools/lab.py test auth` sobre el código actual y registrar el resultado como línea base; verificar que termina con código 0 (si falta JDK 21 o Maven 3.9+, registrar el bloqueo y no continuar con la sección 3).
- [ ] 1.2 Agregar en `AuthResourceTest` una prueba de caracterización del 200 que verifique `token` no vacío, `expiresIn` igual a 3600, `user.username`, `user.role` igual a `STUDENT` y `debug`; verificar que pasa sin cambios en código funcional.
- [ ] 1.3 Agregar pruebas de caracterización del 400 para cuerpo nulo (`Request is null`), `username` en blanco (`Username is required`) y `password` en blanco (`Password is required`); verificar que las tres pasan sin cambios en código funcional.
- [ ] 1.4 Agregar pruebas de caracterización del 401 con contraseña incorrecta para `student` y con usuario inexistente, verificando que la respuesta no contiene `token`; verificar que pasan sin cambios en código funcional.
- [ ] 1.5 Agregar una prueba que verifique que `user.displayName` es `Workshop Student` en el login de `student`; verificar que pasa. Nota: los datos sembrados no contienen caracteres HTML, así que el escape no se puede observar con estas pruebas; esa limitación se documenta en la revisión.

## 2. Reproducción de la vulnerabilidad (pruebas que deben fallar antes del cambio)

- [ ] 2.1 Agregar una prueba con `username` `student` y `password` `' OR '1'='1` que espere 401 sin `token`; verificar que falla hoy con 200, que es la explotación esperada.
- [ ] 2.2 Agregar una prueba con `username` `' OR 1=1 --` y una contraseña cualquiera que espere 401 sin `token`; verificar que falla hoy con 200.
- [ ] 2.3 Agregar una prueba con `username` `' OR '1'='1` y `password` `' OR '1'='1` que espere 401 sin `token`; verificar que falla hoy con 200.
- [ ] 2.4 Agregar una prueba con `username` `' OR '1'='1` y `password` `cualquiera` que espere 401 sin `token`; verificar que pasa hoy, porque ya responde 401, y que se mantiene como guarda de regresión.
- [ ] 2.5 Agregar una prueba con un `username` que contiene comilla simple (por ejemplo `stu'dent`) y contraseña `Workshop123!` que espere 401 sin `token`; verificar que pasa hoy y que se mantiene como guarda de regresión.

## 3. Implementación incremental

- [ ] 3.1 Extraer la consulta de credenciales a una constante con nombre en `LegacyDatabase` usando parámetros `?` para `username` y `password`; verificar que el archivo no contiene concatenación de `username` ni `password` en el SQL con `grep` sobre `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`.
- [ ] 3.2 Cambiar `findByCredentials` a `PreparedStatement` con `setString(1, username)` y `setString(2, password)`, manteniendo firma, tipo de retorno y el `catch` que devuelve `null`; verificar que `python tools/lab.py test auth` compila.
- [ ] 3.3 Verificar que las pruebas de 2.1 a 2.3 ahora pasan y que todas las pruebas de caracterización de la sección 1 siguen en verde; verificar con `python tools/lab.py test auth`.
- [ ] 3.4 Confirmar que `AuthResource.java`, `LegacyTokenUtil.java`, `application.properties` y `pom.xml` no tienen cambios; verificar con `git --no-pager diff --stat -- auth/`.

## 4. Calidad y cobertura

- [ ] 4.1 Revisar el reporte de JaCoCo generado por `python tools/lab.py test auth` y verificar que `LegacyDatabase.findByCredentials` tiene cobertura de líneas de 85% o más.
- [ ] 4.2 Verificar que no se agregaron supresiones ni exclusiones de CodeQL, Dependency Review o JaCoCo (`@SuppressWarnings`, exclusiones en `pom.xml`, reglas en workflows) con `git --no-pager diff -- auth/ .github/`.
- [ ] 4.3 Verificar que el contrato de `POST /auth/login` no cambió comparando las respuestas de las pruebas de la sección 1 con la línea base de 1.1.

## Workflow follow-up

- Revisión humana de los cambios de `auth/` antes de cualquier commit. En Día 1 no hacer push ni crear PR.
- Crear commits por grupo (caracterización, reproducción, implementación) según `design.md`, solo tras la aprobación humana, con el trailer de Copilot.
- Limitación conocida: el escenario 500 de la spec (`Fallo inesperado fuera de la verificación`) no tiene prueba automatizada porque requiere forzar una excepción al construir la respuesta; se verifica por revisión de código.
- Archivar el change con `openspec-archive-change` cuando la implementación esté aprobada y verificada.
