# H-API-04 — Evitar sobrescrituras y limitar efectivamente las cargas

## Fuente

Hallazgo H-API-04, «Carga de archivos sin normalizar nombre ni limitar tipo o tamaño», en `HALLAZGOS.md`. Al iniciar esta implementación, la matriz registraba el hallazgo como **Pendiente**.

Evidencia previa al cambio: la carga normalizaba el nombre y validaba extensión y tamaño del archivo, pero guardar otro archivo con el mismo basename podía sobrescribir el anterior (`upload_service.py`). Además, el tamaño se comprobaba después de que FastAPI procesaba el multipart, por lo que `MAX_UPLOAD_BYTES` no limitaba el cuerpo recibido por el parser (`ENDPOINTS.md`).

**Impacto:** una colisión de nombres puede reemplazar datos existentes; y un cuerpo excesivo puede consumir recursos durante su procesamiento aunque el archivo se rechace después.

## Contexto actual

`POST /api/requirements` recibe un archivo multipart `file` y requiere autenticación. Acepta extensiones configurables —por defecto, `.txt`, `.md` y `.pdf`— y un máximo configurable mediante `MAX_UPLOAD_BYTES`, de 1 MiB por defecto. El éxito devuelve estado 201 y los campos `owner`, `fileName`, `storedAt` y `size`; los nombres o tipos no permitidos reciben 400, una colisión recibe 409 y el exceso de tamaño, 413.

## Problema confirmado antes del cambio

La protección actual no evita que una carga válida reemplace un archivo ya almacenado con el mismo basename. Asimismo, el límite de tamaño se aplica al contenido leído por el servicio, no al procesamiento previo del cuerpo multipart; por tanto, no evita que se reciba o procese el cuerpo completo antes de rechazar el archivo.

## Comportamiento a preservar

- Mantener `POST /api/requirements`, la autenticación y el campo multipart `file`.
- Mantener la normalización del nombre, las extensiones configurables y el límite configurable.
- Mantener la respuesta 201 con `owner`, `fileName`, `storedAt` y `size`.
- Mantener los errores 400 para nombre o extensión no permitidos y 413 para archivos que superen el máximo.
- Rechazar una colisión de basename con 409, sin reemplazar el archivo existente.
- No alterar los contratos de `POST /auth/login` ni de `/api/tasks`.

## Alcance

- Evitar que una carga con nombre coincidente reemplace silenciosamente el contenido de un archivo previamente almacenado.
- Hacer efectivo el límite de tamaño durante el procesamiento de la solicitud, no solo después de que el cuerpo multipart haya sido procesado.
- Verificar los casos de colisión, los límites de tamaño y la preservación del contrato de respuesta.

## Fuera de alcance

- Cambiar las extensiones permitidas, el directorio de almacenamiento o el límite predeterminado.
- Añadir análisis antimalware, políticas de retención o nuevas capacidades de carga.
- Modificar autenticación u otros endpoints.

## Criterios de aceptación

1. Cargar dos archivos con el mismo basename no altera ni elimina el contenido almacenado por la primera carga. Si la segunda carga no se acepta, el cliente recibe un error explícito; si se acepta, la respuesta permite identificar el archivo almacenado.
2. Un archivo cuyo contenido no excede `MAX_UPLOAD_BYTES` puede cargarse; si lo excede, la solicitud se rechaza con 413 y no queda un archivo parcial almacenado.
3. Una solicitud que excede el máximo no requiere que la aplicación procese o retenga íntegramente el contenido del archivo antes de rechazarla.
4. Los casos de nombre o extensión no permitidos siguen respondiendo 400.
5. Las cargas válidas conservan el estado 201 y los campos `owner`, `fileName`, `storedAt` y `size`.

## Contratos afectados/protegidos

- **Afectado y protegido:** `POST /api/requirements`, según el contrato acordado en `ENDPOINTS.md`. Se preservan método, ruta, autenticación, campo multipart, respuesta de éxito y códigos de error indicados.
- **Protegidos sin cambios:** `POST /auth/login` y `/api/tasks`.

## Riesgos y supuestos

- Se asume que `MAX_UPLOAD_BYTES` limita los bytes del archivo, no el tamaño total del multipart, que incluye delimitadores y encabezados.
- La política aprobada para colisiones es responder 409; no se generan nombres alternativos.
- El límite se aplica a cada parte de archivo mientras se recibe multipart; no limita el tamaño agregado de la solicitud ni reemplaza controles en proxy o servidor ASGI. Una unidad ASGI que ya entregó un bloque grande no puede recuperarse; el parser evita pedir los bloques posteriores al detectar el exceso.
- El parser extiende callbacks de Starlette; al actualizar FastAPI/Starlette debe ejecutarse de nuevo la regresión incremental para verificar compatibilidad.

## Aprobación y estado

- Issue H-API-04 y esta especificación funcional aprobados el 2026-11-03.
- La arquitectura aprobada es el rechazo exclusivo de colisiones con 409 y el parseo incremental con límite por archivo.
- Implementación y validación registradas en `docs/TESTING.md`; el hallazgo se marca resuelto en `HALLAZGOS.md`.