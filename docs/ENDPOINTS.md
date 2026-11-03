# Contratos API — componente api

## Contrato observado antes del cambio

`docs/ENDPOINTS.md` no existía antes de esta implementación. Esta sección conserva el comportamiento observado y registrado en `PLAN.md` y `HALLAZGOS.md`:

| Ruta | Comportamiento observado |
|---|---|
| `GET /api/tasks` | Requería token Base64 legacy; permitía elegir `owner` en query y respondía 200 con tareas que incluían `id`, `title`, `description`, `status`, `owner`, `priority`, `createdAt`. |
| `POST /api/tasks` | Requería token; permitía elegir `owner` en el JSON y respondía 201 con esos campos. |
| `GET /api/summary` | Requería token; permitía elegir `owner` en query y respondía 200 con `owner`, `total`, `byStatus`, `highPriorityOpen`. |
| `POST /api/requirements` | Requería token; guardaba el nombre indicado por cliente y respondía 201 con `owner`, `fileName`, `storedAt`, `size`. |
| `GET /api/backend-health` | Requería token; respondía 200 con `requestedBy`, `status`, `body`; ante fallo de transporte exponía el texto de excepción. |

## Contrato acordado tras el plan

Todas las rutas requieren `Authorization: Bearer <JWT>`. Se verifica firma HS256, expiración, emisor y audiencia. `JWT_SECRET`, `JWT_ISSUER` y `JWT_AUDIENCE` son obligatorios; no se incluyen secretos reales ni defaults inseguros. Token inválido, expirado, legacy o con claims incorrectos responde 401 con `{"detail":"Invalid token"}`.

| Método y ruta | Request y autorización | Respuesta de éxito |
|---|---|---|
| `GET /api/tasks` | Query `owner` permanece aceptado por compatibilidad, pero se ignora; el propietario es el `sub` autenticado. | 200; array de tareas con `id`, `title`, `description`, `status`, `owner`, `priority`, `createdAt`. |
| `POST /api/tasks` | JSON con `title`, `description`, `status`, `priority` y `owner` opcionales; `owner` del cliente se ignora. Valores por defecto: título/descripción vacíos, `TODO` y `MEDIUM`. | 201; tarea con los mismos campos que el listado y propietario autenticado. |
| `GET /api/summary` | Query `owner` permanece aceptado, pero se ignora; resume tareas del `sub` autenticado. | 200; objeto `owner`, `total`, `byStatus`, `highPriorityOpen`. |
| `POST /api/requirements` | Multipart `file`. Extensiones permitidas por defecto: `.txt`, `.md`, `.pdf`; `MAX_UPLOAD_BYTES` configura el máximo (1 MiB por defecto). `UPLOAD_ALLOWED_EXTENSIONS` puede cambiar la lista. | 201; `owner`, `fileName`, `storedAt`, `size`. Nombre normalizado a basename. Extensión/nombre no permitido: 400; colisión de basename: 409; exceso de tamaño: 413. |
| `GET /api/backend-health` | Consulta `AUTH_HEALTH_URL` (por defecto `http://localhost:8080/q/health`). | 200; `requestedBy`, `status`, `body`. Si falla el transporte, `status` es 0 y `body` es `Backend unavailable`. |

## Riesgos pendientes

- No se ejecutó integración con el servicio auth; está fuera del alcance. Debe confirmarse que el emisor entregue JWT HS256 con `sub`, `role`, `exp`, `iss` y `aud`, usando la misma configuración acordada. El token Base64 legacy queda rechazado.
- `GET /api/backend-health` se validó con respuestas y errores simulados; no se verificó un servicio auth real.
- La ruta de carga conserva el contrato de campos, pero el nombre se normaliza y se aplican restricciones nuevas de extensión y tamaño.

## Carga de requisitos — H-API-04

El límite de `MAX_UPLOAD_BYTES` se aplica a los bytes del archivo durante el parseo multipart incremental; la envoltura multipart no cuenta para ese límite. Una carga que excede el máximo responde `413` sin solicitar bloques ASGI posteriores ni dejar un archivo final. El destino se crea exclusivamente: si el basename ya existe, la carga responde `409` y conserva el contenido almacenado. La respuesta válida mantiene `201` y los campos documentados arriba.
