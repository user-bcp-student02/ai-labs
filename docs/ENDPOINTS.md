# Contratos de endpoints

## Estado del contrato de auth

Esta sección describe el contrato observado en el código actual de `auth/`.
No se encontró un contrato previamente acordado en `docs/ENDPOINTS.md`, por
lo que no se afirma que consumidores externos hayan aprobado todos sus
detalles. El endpoint de login y sus DTOs están definidos en
`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`,
`LoginRequest.java` y `LoginResponse.java`.

## `GET /q/health`

### 1. Qué hace

Permite a quien opera o integra el sistema comprobar si el servicio de
autenticación está respondiendo. La ruta la provee la extensión
`quarkus-smallrye-health` (`auth/pom.xml`); el README la publica como URL de
salud local.

### 2. Autenticación

No requiere autenticación ni un header `Authorization` en la configuración
observada. No se encontró protección adicional para esta ruta en `auth/`.

### 3. Request

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| — | — | no aplica | Este GET no recibe campos ni cuerpo JSON. |

No hay ejemplo JSON de request porque no existe cuerpo. Ejemplo de solicitud:

```http
GET /q/health HTTP/1.1
Host: localhost:8080
```

La ruta y el puerto local `8080` se documentan en `README.md`; en el código,
el puerto se configura en `auth/src/main/resources/application.properties`.

### 4. Response exitosa

Estado observado: `200 OK`.

| campo | tipo | descripción |
|---|---|---|
| `status` | string | Estado global reportado por SmallRye Health. En la respuesta local comprobada fue `UP`. |
| `checks` | array | Comprobaciones registradas por la extensión. En la respuesta local comprobada estaba vacío. |

Respuesta obtenida localmente en `http://127.0.0.1:18080/q/health`:

```json
{
  "status": "UP",
  "checks": []
}
```

El puerto `18080` se usó solo para esta comprobación local; no cambia el puerto
configurado del servicio.

### 5. Respuestas de error

No se encontró un health check propio de `auth/` ni una respuesta de error
implementada por el componente. En la comprobación local la ruta respondió
`200`; no se comprobó el comportamiento cuando una comprobación de salud
externa falla. La forma/código de esa respuesta queda como pregunta abierta,
no como contrato confirmado. Si el proceso no está disponible, no hay
respuesta HTTP del proceso para documentar.

### 6. Notas de seguridad

La ruta de salud es pública según la configuración observada. No debe incluirse
en ella información de configuración o secretos; la respuesta local comprobada
solo contiene estado y una lista vacía de comprobaciones.

## `POST /auth/login`

### 1. Qué hace

Autentica a una persona usuaria con sus credenciales y, si son válidas, devuelve
un token para su sesión junto con los datos de perfil que consume el cliente.
La ruta y el flujo están en
`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:15-47`.

### 2. Autenticación

No requiere un token previo ni un header `Authorization`: es el endpoint que
establece la sesión. Requiere `Content-Type: application/json`; produce
`application/json` (`AuthResource.java:15-18`).

### 3. Request

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| `username` | string | sí | Nombre de usuario. Si falta, es `null` o contiene solo espacios, la ruta responde 400. |
| `password` | string | sí | Contraseña. Si falta, es `null` o contiene solo espacios, la ruta responde 400. |
| `client` | string | no | Campo admitido por el DTO y presente en una prueba del cliente; el recurso actual no lo consulta para autenticar. |

Los campos y tipos se definen en `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java`;
la validación está en `AuthResource.java:49-60`. Ejemplo completo:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

Las credenciales son de demostración/locales y se siembran en los perfiles
`dev` y `test`, no en la configuración normal
(`auth/src/main/resources/application.properties:10-12`).

### 4. Response exitosa

Estado: `200 OK`.

| campo | tipo | descripción |
|---|---|---|
| `token` | string | JWT firmado con HS256. Se genera al autenticar y su valor no debe tratarse como constante. |
| `expiresIn` | integer | Tiempo de vida del token en segundos. El valor observado es `3600`. |
| `user` | object | Perfil asociado a las credenciales válidas. |
| `user.username` | string | Nombre de usuario autenticado. |
| `user.displayName` | string | Nombre visible; el servicio aplica escape HTML antes de devolverlo. |
| `user.role` | string | Rol guardado para el usuario. |
| `debug` | string | Valor actual: `authenticated-with-legacy-flow`. Está en el DTO de respuesta observado. |

La estructura se declara en `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java`;
la construcción está en `AuthService.java:20-36`. La prueba de integración
comprueba los campos de respuesta en
`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:45-60`.

Ejemplo JSON completo de la forma de respuesta. `token` es un marcador
descriptivo, no un token literal utilizable; el servidor genera un JWT distinto
en cada autenticación:

```json
{
  "token": "<JWT HS256 generado por el servidor>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

### 5. Respuestas de error

Las respuestas de error definidas por `AuthResource` usan JSON con un campo
`message`:

| código | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `400 Bad Request` | El cuerpo JSON se convierte a `null`. | `{"message":"Request is null"}` |
| `400 Bad Request` | `username` está ausente, es `null` o solo tiene espacios. | `{"message":"Username is required"}` |
| `400 Bad Request` | `password` está ausente, es `null` o solo tiene espacios. | `{"message":"Password is required"}` |
| `401 Unauthorized` | No hay usuario coincidente o la contraseña no es válida. El mensaje actual incluye el username recibido. | `{"message":"Invalid user student or password"}` |
| `500 Internal Server Error` | Ocurre una excepción durante la autenticación. El detalle se registra en el servidor y el cuerpo no lo expone. | `{"message":"Authentication failed"}` |

Estos casos se construyen en `AuthResource.java:27-47,49-64`; las aserciones de
400 y 401 están en `AuthResourceTest.java:144-185`, y el cuerpo genérico de
500 se caracteriza en `AuthResourceTest.java:251-270`.

Errores de transporte/conversión JSON gestionados por Quarkus, como un JSON
mal formado o un `Content-Type` no admitido, no tienen un cuerpo definido por
el recurso. En una comprobación local se observaron `400` para JSON mal
formado y `415` para `text/plain`, ambos sin cuerpo (`Content-Length: 0`).
No se documenta ese formato vacío como contrato estable del recurso.

### 6. Notas de seguridad

- El token es JWT HS256 con los claims `username`, `sub`, `role`, `iat` y
  `exp`; la expiración se calcula a 3600 segundos
  (`auth/src/main/java/com/bcp/workshop/auth/JwtTokenService.java:23-60`).
- La clave se configura mediante `AUTH_JWT_SECRET`; la aplicación rechaza una
  clave de menos de 32 bytes y no declara una clave secreta de respaldo
  (`JwtTokenService.java:31-42`;
  `auth/src/main/resources/application.properties:9`).
- El componente genera el token en login, pero no expone un endpoint para
  validar tokens. La validación de JWT se limita al servicio interno
  `JwtTokenService.isValidToken`; no debe asumirse una ruta de autenticación
  protegida por este recurso.
- La configuración CORS actual mantiene `/.*/`
  (`auth/src/main/resources/application.properties:4-7`); la allowlist por
  entorno sigue pendiente.
- Pregunta abierta de integración: confirmar que los consumidores externos
  interpretan `role`, `sub` y la expiración del JWT de la misma forma, además
  de aceptar HS256; no se inspeccionaron consumidores fuera del alcance
  `auth/`.
