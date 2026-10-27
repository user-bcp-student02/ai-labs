# Pruebas de endpoints

## Auth

La matriz corresponde únicamente a los endpoints de auth descritos en
[`ENDPOINTS.md`](./ENDPOINTS.md). Los casos de error de transporte se prueban
como comportamiento observado de Quarkus; no se elevan a contrato estable del
recurso.

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /q/health` | Servicio disponible; lista `checks` vacía | Camino feliz y borde | `200`; `status` igual a `UP`; `checks` con cero elementos |
| `GET /q/health` | Fallo de una comprobación de salud | Error | No aplica: auth no registra health checks propios y el formato/código de fallo queda como pregunta abierta en `ENDPOINTS.md` |
| `POST /auth/login` | Credenciales válidas de `student` | Camino feliz | `200`; token no nulo; usuario, rol, `expiresIn` y `debug` documentados |
| `POST /auth/login` | Credenciales válidas de `trainer`, sin `client` opcional | Borde | `200`; se autentica correctamente aunque se omita `client` |
| `POST /auth/login` | Request JSON `null` | Error | `400`; `{"message":"Request is null"}` |
| `POST /auth/login` | Username ausente o compuesto por espacios | Borde/error | `400`; `{"message":"Username is required"}` |
| `POST /auth/login` | Password compuesto por espacios | Borde/error | `400`; `{"message":"Password is required"}` |
| `POST /auth/login` | Password incorrecto | Error | `401`; mensaje `Invalid user student or password` |
| `POST /auth/login` | Excepción durante autenticación | Error | `500`; cuerpo genérico `{"message":"Authentication failed"}` sin campo `exception` |
| `POST /auth/login` | JSON mal formado (`{`) | Error de transporte observado | `400`; cuerpo vacío, según el comportamiento local observado de Quarkus |
| `POST /auth/login` | `Content-Type: text/plain` | Error de transporte observado | `415`; cuerpo vacío, según el comportamiento local observado de Quarkus |
| `POST /auth/login` | Username válido que contiene una comilla | Borde/seguridad | `200`; username devuelto literalmente, sin alterar la consulta SQL |

No se inventa una respuesta de error para `GET /q/health`: el endpoint no
define una respuesta de error propia y no se simuló la caída de un health check.
Los errores JSON mal formado y tipo de contenido no admitido sí se ejecutan,
pero su código/cuerpo dependen de la capa de transporte y no se documentan
como contrato estable de auth.

## Código de las pruebas

El código completo y ejecutable está en
`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`. Los métodos
de camino feliz, borde, validación, credenciales incorrectas, JWT, transporte,
500 y comilla en username están en las líneas 51-318; la prueba unitaria de respuesta
genérica del recurso está en las líneas 320-341. La prueba HTTP 500 usa
`@InjectSpy` para forzar una excepción en un único caso sin reemplazar el
`AuthService` real para el resto de la clase.

Las pruebas añadidas para cubrir las respuestas de transporte observadas son:

```java
@Test
void rejectsMalformedJsonAtTransportBoundary() {
    String responseBody = given()
            .contentType("application/json")
            .body("{")
            .when().post("/auth/login")
            .then()
            .statusCode(400)
            .extract()
            .asString();

    assertEquals("", responseBody);
}

@Test
void rejectsUnsupportedContentTypeAtTransportBoundary() {
    String responseBody = given()
            .contentType("text/plain")
            .body("plain text payload")
            .when().post("/auth/login")
            .then()
            .statusCode(415)
            .extract()
            .asString();

    assertEquals("", responseBody);
}
```

La prueba health exige simultáneamente `200`, `status=UP` y lista `checks`
vacía. El login exitoso verifica campos del contrato y token; las pruebas
negativas comprueban los status y cuerpos exactos indicados en la matriz. Los
tests de persistencia, hashes y contraseñas están en
`auth/src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java` y
`PasswordHasherTest.java`.

## Ejecuciones y cobertura

| componente | IDs relacionados | comando y directorio | código de salida | resultado |
|---|---|---|---:|---|
| auth | H-AUTH-01, H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-06 | Desde `auth/`: `mvn clean verify`, con `AUTH_DB_URL=jdbc:sqlite:./target/auth-endpoint-tests-final.db` | 0 | `BUILD SUCCESS`; 22 pruebas, 0 fallos, 0 errores, 0 omitidas. Incluye 19 `AuthResourceTest`, 1 `LegacyDatabaseTest` y 2 `PasswordHasherTest`. |

La cobertura se midió en esa ejecución con JaCoCo Maven Plugin 0.8.12,
generada por `verify` en `auth/target/site/jacoco/` a partir de
`auth/target/jacoco.exec`. El criterio informado es cobertura de líneas de
clases de producción modificadas o añadidas, calculada como
`LINE_COVERED / (LINE_COVERED + LINE_MISSED)` del reporte CSV:

| clase de auth | líneas cubiertas | cobertura de líneas |
|---|---:|---:|
| `AuthResource` | 25/25 | 100% |
| `AuthService` | 11/11 | 100% |
| `LegacyDatabase` | 74/80 | 92.5% |
| `PasswordHasher` | 32/34 | 94.1% |
| `JwtTokenService` | 40/44 | 90.9% |

### Fallos y correcciones durante esta tarea

Una ejecución preliminar de `mvn clean verify` terminó con 6 fallos y 2
errores: `@InjectMock AuthService` sustituía el servicio en toda la clase,
haciendo que pruebas de login no ejercitaran la implementación real. Se cambió
a `@InjectSpy`, se mantuvo el stub únicamente en la prueba de excepción y se
repitió `mvn clean verify`; el resultado final es el éxito registrado arriba.
El fallo preliminar no se presenta como resultado de la verificación final.

### Pendientes y no ejecutado

- No hay prueba de fallo de un health check: auth no define tal respuesta y
  `ENDPOINTS.md` la mantiene como pregunta abierta.
- CodeQL y Dependency Review remotos no se ejecutaron en esta validación local.
- No se ejecutó un análisis SCA adicional.
