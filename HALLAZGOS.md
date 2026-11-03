# Hallazgos

## Contrato observado de api

No existía `docs/ENDPOINTS.md` antes de la implementación. Esta tabla conserva el comportamiento observado antes de los cambios y no debe leerse como el contrato corregido.

| Ruta | Comportamiento observado | Evidencia |
|---|---|---|
| `GET /api/tasks` | Requiere `Authorization: Bearer ...`; acepta `owner` opcional y devuelve tareas del propietario solicitado o de la identidad. | `api/app/main.py:17-23,65-75` |
| `POST /api/tasks` | Requiere token; recibe un objeto JSON y devuelve la tarea creada con estado 201. El payload puede indicar `owner`. | `api/app/main.py:78-89` |
| `GET /api/summary` | Requiere token; acepta `owner` opcional y calcula total, cantidades por estado y tareas abiertas de prioridad alta. | `api/app/main.py:91-106` |
| `POST /api/requirements` | Requiere token; recibe un archivo multipart, lo guarda en el directorio configurado y devuelve nombre, ruta y tamaño. | `api/app/main.py:108-112` |
| `GET /api/backend-health` | Requiere token; consulta `AUTH_HEALTH_URL` (por defecto `http://localhost:8080/q/health`) y devuelve estado y cuerpo del servicio remoto. | `api/app/main.py:115-121` |

El token observado es Base64 de `username|role|expiry|secret`; no se observa verificación criptográfica de firma, emisor o audiencia. SQLite se inicializa en `create_app`, usando `WORKSHOP_DB_PATH` o `./data/workshop.db`; las cargas usan `WORKSHOP_UPLOAD_DIR` o `./uploads`. Evidencia: `api/app/main.py:17-23,28-41`.

## Matriz de seguimiento

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-API-01 | api | Token Base64 con secreto embebido y sin firma | Vulnerabilidad | El contenido solo se decodificaba y se comparaba con un secreto fijo; no se validaban firma, emisor ni audiencia. Resuelto: JWT HS256 valida firma y claims requeridos; la configuración se obtiene del entorno y los errores devuelven un 401 genérico. | `api/app/security.py:18-42`, `api/app/main.py:15,55-89` | alta | Resuelto |
| H-API-02 | api | Acceso a datos de otros propietarios | Vulnerabilidad | `owner` podía venir del query y del JSON de creación sin limitarse al usuario autenticado. Resuelto: lectura, creación y resumen usan exclusivamente `identity["username"]`. | `api/app/services/tasks_service.py:19-46`, `api/app/repositories/tasks_repository.py:33-50,61-65` | alta | Resuelto |
| H-API-03 | api | Consultas SQL construidas por concatenación | Vulnerabilidad | Los valores recibidos se interpolaban en filtros e inserts. Resuelto: filtros, inserts y lookup por ID usan parámetros SQLite. | `api/app/repositories/tasks_repository.py:33-58,61-65` | alta | Resuelto |
| H-API-04 | api | Carga de archivos sin normalizar nombre ni limitar tipo o tamaño | Vulnerabilidad | La validación normaliza basename, extensión y lectura, pero queda pendiente evitar colisiones: `write_bytes` trunca un archivo previo con el mismo basename en el directorio compartido. Además, el tamaño se verifica en el handler después de FastAPI parsea el multipart, por lo que `MAX_UPLOAD_BYTES` no limita el cuerpo recibido por el parser. | `api/app/main.py:76-81`, `api/app/services/upload_service.py:58-68` | alta | Pendiente |
| H-API-05 | api | Dependencias fijadas requieren verificación de advisories | Sugerencia | Resuelto: `urllib3` y `python-multipart` se actualizaron a versiones corregidas; PyJWT se añadió para la autenticación y se fijó en 2.15.0 tras comprobar sus advisories. | `api/requirements.txt:1-6`; [GHSA-8988-9cw3-xx77](https://github.com/advisories/GHSA-8988-9cw3-xx77), [GHSA-vxq7-64xx-v4gw](https://github.com/advisories/GHSA-vxq7-64xx-v4gw), [GHSA-5rvq-cxj2-64vf](https://github.com/advisories/GHSA-5rvq-cxj2-64vf), [GHSA-x33g-cr3x-6449](https://github.com/advisories/GHSA-x33g-cr3x-6449) | media | Resuelto |
| H-API-06 | api | Rutas, persistencia, reglas y serialización concentradas en un módulo | Refactor | `main.py` concentraba inicialización, SQL, autorización y serialización. Resuelto: rutas, servicio de tareas, repositorio, modelos y servicio de carga quedaron separados. | `api/app/main.py:24-108`, `api/app/repositories/tasks_repository.py:13-65`, `api/app/services/tasks_service.py:19-58`, `api/app/schemas.py:9-52` | baja | Resuelto |
| H-API-07 | api | Tipado crudo, excepciones amplias, lógica anidada y duplicación | Refactor | Se usaban diccionarios sin modelo, excepciones genéricas y resumen anidado. Resuelto: modelos Pydantic tipados, transformaciones reutilizables, cálculo sin anidamiento y captura específica de errores de transporte/carga. | `api/app/schemas.py:9-52`, `api/app/services/tasks_service.py:33-58`, `api/app/main.py:76-100` | baja | Resuelto |