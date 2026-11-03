## Impacto

La spec H-API-04 identifica dos fallos en la carga actual: `upload_service.py` guarda con `write_bytes`, que puede truncar un archivo previo con el mismo basename; y el servicio comprueba el tamaño después de que FastAPI ya procesó el multipart (`main.py`). El límite actual protege la lectura del servicio, no el procesamiento completo de la solicitud.

## Componentes afectados

- **`api/app/services/upload_service.py`**: preservar la validación existente y evitar escrituras que sobrescriban archivos; asegurar que una carga rechazada no deje archivos parciales.
- **`api/app/main.py`**: integrar una recepción que pueda rechazar cargas excesivas mientras llegan, antes de completar el procesamiento multipart.
- **`api/tests/test_api.py`**: añadir caracterización y regresiones para colisiones, límites y consumo anticipado del cuerpo.
- **Documentación**: actualizar `ENDPOINTS.md`, `TESTING.md` y el estado/evidencia de H-API-04 en `HALLAZGOS.md`.

No se justifican dependencias nuevas: `python-multipart` ya está presente.

## Contratos protegidos

- Mantener `POST /api/requirements`, autenticación y campo multipart `file`.
- Mantener extensiones configurables, normalización, y `MAX_UPLOAD_BYTES` como límite de **bytes del archivo**, no del envoltorio multipart.
- Mantener cargas válidas en `201` con `owner`, `fileName`, `storedAt` y `size`.
- Mantener `400` para nombre/extensión no permitidos y `413` para exceso de tamaño; devolver `409` ante colisión de basename.
- No modificar `/auth/login` ni `/api/tasks`.

El contrato `409` para colisiones queda documentado en `docs/ENDPOINTS.md`.

## Estrategia incremental

1. Añadir pruebas que demuestren la sobrescritura y que el límite actual se aplica después del parseo.
2. **Colisiones — aprobado:** crear el destino de forma exclusiva y responder `409` si ya existe. Esto evita la carrera de “comprobar si existe y luego escribir”.
3. **Tamaño — aprobado:** recibir y parsear el multipart incrementalmente, detenerse al superar el máximo de bytes de la parte del archivo y cerrar los temporales. Un límite genérico del cuerpo ASGI no basta por sí solo: el multipart añade bytes de envoltura y podría rechazar archivos válidos en el límite o no detectar temprano excesos pequeños.
4. Ejecutar regresión y documentar contrato, validaciones y evidencia antes de cerrar H-API-04.

## Pruebas necesarias

- Dos cargas con el mismo basename: la primera conserva exactamente su contenido; la segunda devuelve el error elegido y no deja datos parciales. Incluir concurrencia si el mecanismo lo permite.
- Archivo de tamaño exacto al límite: aceptado; un byte por encima: `413`, sin archivo final ni temporal residual.
- Verificar que la carga excesiva se rechaza sin consumir el cuerpo completo, incluyendo una solicitud sin `Content-Length` si el transporte de pruebas lo permite.
- Preservar casos existentes de normalización, extensión `400`, autenticación, y forma de respuesta `201`.
- Validar la cobertura del código modificado frente a la meta del componente de **85% o superior**.

La validación ejecutada y sus resultados reales se registran en `docs/TESTING.md`.

## Riesgos / trade-offs

- Rechazar colisiones con `409` es sencillo y predecible, pero obliga al cliente a resolver el conflicto; esta política quedó aprobada.
- El parser incremental cumple mejor el requisito estricto de rechazo temprano, a costa de más complejidad que el handler actual.
- La implementación extiende callbacks de `Starlette MultiPartParser`; una actualización de FastAPI/Starlette requiere volver a validar la regresión de recepción incremental.
- El directorio de cargas es compartido: este alcance evita sobrescrituras, pero no introduce aislamiento por propietario, cuotas, retención ni protección de red/proxy.
- `storedAt` seguirá exponiendo la ubicación conforme al contrato actual; si el nombre se genera, debe identificar el archivo realmente guardado.

**Decisión arquitectónica aprobada:** rechazo atómico de colisiones con `409` y límite por archivo durante recepción incremental.

## Aprobaciones

- Issue H-API-04 y esta especificación funcional aprobados el 2026-11-03.
- Arquitectura aprobada: `409` ante colisión y recepción incremental con límite por archivo.
- La evidencia de implementación y pruebas está en `docs/TESTING.md`; el estado del hallazgo se conserva en `HALLAZGOS.md`.