# PLAN DE CORRECCIÓN

## api

### Aprobación humana
- **Aprobó**: Jordi Montanez.
- **Fecha**: 2026-11-02.
- **Alcance aprobado**: implementación del plan para H-API-01 a H-API-07, respetando las pruebas de caracterización previas y el alcance de archivos indicado para cada hallazgo.

### H-API-01 — Token Base64 con secreto embebido y sin firma
- **Hallazgo de referencia**: H-API-01 (`api/app/main.py:14,17-23`).
- **Problema**: la autenticación confía en un token Base64 con secreto hardcodeado; no hay verificación criptográfica ni validación de `iss`/`aud`.
- **Cambio propuesto**: implementar verificación real de JWT (firma, expiración, emisor, audiencia) en una función de autenticación dedicada y mantener el comportamiento HTTP observado (`401` ante token inválido).
- **Archivos a tocar**:
  - `api/app/main.py` (integración de dependencia de autenticación).
  - `api/app/security.py` (nuevo módulo para validación JWT).
  - `api/tests/test_api.py` (o archivo de pruebas de auth dedicado en `api/tests/`).
- **Prueba de caracterización previa**: agregar pruebas que congelen el contrato actual de rechazo/aceptación en endpoints protegidos (`/api/tasks`, `/api/summary`, `/api/requirements`, `/api/backend-health`) y verifiquen que sin token válido se mantiene `401`.
- **Reglas de diseño (Clean Code)**: separar autenticación de rutas, eliminar constantes mágicas embebidas y evitar bloques `try/except` amplios en la capa HTTP.

### H-API-02 — Acceso a datos de otros propietarios
- **Hallazgo de referencia**: H-API-02 (`api/app/main.py:68-70,78-85,91-93`).
- **Problema**: el cliente puede indicar `owner` en query/body y leer o crear tareas para otra identidad.
- **Cambio propuesto**: aplicar autorización ligada a identidad del token verificado (`identity["username"]`) en lectura, creación y resumen; mantener rutas y forma de respuesta del contrato observado.
- **Archivos a tocar**:
  - `api/app/main.py` (consumo de servicio/autorización).
  - `api/app/services/tasks_service.py` (nuevo servicio con reglas de autorización).
  - `api/tests/test_api.py`.
- **Prueba de caracterización previa**: crear pruebas que documenten el comportamiento actual de `owner` en `GET /api/tasks`, `POST /api/tasks` y `GET /api/summary`, para luego validar el cambio controlado sin romper estructura de respuesta ni códigos esperados.
- **Reglas de diseño (Clean Code)**: una sola responsabilidad por función (ruta vs autorización), sin duplicar resolución de propietario.

### H-API-03 — Consultas SQL construidas por concatenación
- **Hallazgo de referencia**: H-API-03 (`api/app/main.py:70,84,87,93`).
- **Problema**: interpolación directa de datos de entrada en SQL para filtros, inserts y lecturas por ID.
- **Cambio propuesto**: migrar a SQL parametrizado (`?`) en todas las consultas de tareas y resumen, concentrándolo en repositorio dedicado.
- **Archivos a tocar**:
  - `api/app/main.py` (retirar SQL de rutas).
  - `api/app/repositories/tasks_repository.py` (nuevo repositorio con consultas parametrizadas).
  - `api/tests/test_api.py`.
- **Prueba de caracterización previa**: agregar pruebas que congelen resultado de listado, creación y summary para entradas válidas actuales, y un caso con caracteres especiales en texto para comprobar que el contrato funcional se conserva tras parametrizar.
- **Reglas de diseño (Clean Code)**: eliminar duplicación de SQL y separar persistencia de reglas de negocio.

### H-API-04 — Carga de archivos sin normalizar nombre ni limitar tipo/tamaño
- **Hallazgo de referencia**: H-API-04 (`api/app/main.py:108-112`).
- **Problema**: se persiste `file.filename` directo y se lee todo el archivo en memoria sin límites ni validación de extensión.
- **Cambio propuesto**: implementar carga segura de archivos (normalización de nombre, lista permitida de extensiones y tamaño máximo configurable) preservando endpoint y payload de respuesta.
- **Archivos a tocar**:
  - `api/app/main.py` (wire de ruta).
  - `api/app/services/upload_service.py` (nuevo servicio de validación y guardado seguro).
  - `api/tests/test_api.py`.
- **Prueba de caracterización previa**: añadir prueba que congele el contrato observado de éxito actual en `POST /api/requirements` (status `201`, campos `owner`, `fileName`, `storedAt`, `size`) antes de endurecer validaciones.
- **Reglas de diseño (Clean Code)**: encapsular validaciones de archivo en funciones pequeñas y reemplazar números mágicos por constantes con nombre.

### H-API-05 — Dependencias fijadas requieren verificación de advisories
- **Hallazgo de referencia**: H-API-05 (`api/requirements.txt:4-5`).
- **Problema**: dependencias fijadas sin evidencia de evaluación de advisories aplicables para las versiones usadas en el laboratorio.
- **Cambio propuesto**: revisar advisories aplicables y, si corresponde, ajustar versiones a parches seguros compatibles; registrar evidencia y riesgo residual en documentación del componente.
- **Archivos a tocar**:
  - `api/requirements.txt` y/o `api/requirements-dev.txt` (si aplica actualización).
  - `api/tests/test_api.py` (ajustes solo si una actualización cambia comportamiento observable).
  - `HALLAZGOS.md` y `docs/TESTING.md` (evidencia de validación, sin ampliar alcance fuera de api).
- **Prueba de caracterización previa**: ejecutar y registrar pruebas existentes del componente para establecer baseline antes de cualquier ajuste de versión.
- **Reglas de diseño (Clean Code)**: cambios mínimos y trazables, sin introducir dependencias nuevas no justificadas.

### H-API-06 — Rutas, persistencia, reglas y serialización concentradas en un módulo
- **Hallazgo de referencia**: H-API-06 (`api/app/main.py:28-45,58-60,65-121`).
- **Problema**: acoplamiento alto en un único archivo (`main.py`) con inicialización, acceso a datos, reglas y serialización.
- **Cambio propuesto**: separar por capas (rutas → servicio → repositorio) manteniendo endpoints y contrato observado, con funciones de responsabilidad única.
- **Archivos a tocar**:
  - `api/app/main.py` (enrutamiento e inyección de dependencias).
  - `api/app/services/tasks_service.py` (nuevo).
  - `api/app/repositories/tasks_repository.py` (nuevo).
  - `api/app/schemas.py` (nuevo, modelos de request/response).
  - `api/tests/test_api.py`.
- **Prueba de caracterización previa**: crear suite de caracterización de endpoints actuales (`/api/tasks`, `/api/summary`, `/api/requirements`, `/api/backend-health`) para validar que la extracción por capas no altera contratos.
- **Reglas de diseño (Clean Code)**: sin duplicación, sin anidamiento profundo y sin mezclar responsabilidades en las rutas.

### H-API-07 — Tipado crudo, excepciones amplias, lógica anidada y duplicación
- **Hallazgo de referencia**: H-API-07 (`api/app/main.py:17,25,58-60,68,78,70,84-93,91-106,120-121`).
- **Problema**: uso de `dict[str, Any]` crudo, manejo genérico de excepciones, condicionales anidados en summary y lógica repetida de mapeo/proyección.
- **Cambio propuesto**: introducir modelos Pydantic para request/response, extraer transformaciones repetidas a funciones reutilizables, reemplazar condicionales anidados por guard clauses y acotar errores a excepciones explícitas.
- **Archivos a tocar**:
  - `api/app/main.py`.
  - `api/app/schemas.py` (nuevo).
  - `api/app/services/tasks_service.py`.
  - `api/tests/test_api.py`.
- **Prueba de caracterización previa**: agregar pruebas que documenten estructura exacta de respuestas (tipos/campos) y comportamiento actual de `summary`, para asegurar que el refactor mejora calidad interna sin cambiar contrato externo.
- **Reglas de diseño (Clean Code)**: una responsabilidad por función, eliminar duplicación, evitar anidamiento profundo y reemplazar valores mágicos por constantes con nombre.

### Contrato observado antes del cambio — api

No existía `docs/ENDPOINTS.md`; esta referencia conserva el comportamiento visto antes de aplicar el plan.

| Ruta | Comportamiento observado antes del cambio |
|---|---|
| `GET /api/tasks` | 200 con tareas que incluyen `id`, `title`, `description`, `status`, `owner`, `priority`, `createdAt`; requería token y usaba el `owner` opcional enviado por el cliente. |
| `POST /api/tasks` | 201 con esos mismos campos; permitía que el JSON eligiera `owner`. |
| `GET /api/summary` | 200 con `owner`, `total`, `byStatus`, `highPriorityOpen`; aceptaba `owner` elegido por el cliente. |
| `POST /api/requirements` | 201 con `owner`, `fileName`, `storedAt`, `size`; conservaba el nombre indicado por cliente. |
| `GET /api/backend-health` | 200 con `requestedBy`, `status`, `body`; ante error de transporte devolvía el texto de la excepción en `body`. |
| Autenticación | Las rutas protegidas aceptaban Base64 de `username|role|expiry|secret`, además de responder 401 a tokens inválidos. |

El plan aprobó restringir lectura, creación y resumen a la identidad verificada, rechazar tokens legacy, normalizar y validar cargas, y devolver un error de salud genérico. Se conservan rutas, métodos, estados de éxito y forma/campos de respuesta.

## Registro de ejecución — api

| Fecha | Componente e IDs | Ejecución / resultado |
|---|---|---|
| 2026-11-02 | api — H-API-01 a H-API-07 | Aprobación humana registrada: Jordi Montanez aprobó el alcance descrito en este plan antes de implementar. |
| 2026-11-02 | api — H-API-01 a H-API-07 | Baseline previo: `python -m pytest --cov=app --cov-report=term-missing`; 4 pasaron, cobertura total 91%. |
| 2026-11-02 | api — H-API-01 a H-API-07 | Caracterización antes de editar código: `python -m pytest tests/test_api.py -q`; 7 pasaron, 95% de cobertura en `main.py`, incluido éxito con el token Base64 legacy y formas de respuesta. |
| 2026-11-02 | api — H-API-01 | Primera validación tras el cambio: error de colección `ModuleNotFoundError: app.security` porque el módulo nuevo no quedó aplicado en ese parche; se creó el módulo antes de continuar. |
| 2026-11-02 | api — H-API-01 | Segunda validación: 6 fallaron porque las pruebas todavía emitían tokens Base64; se corrigió el helper de pruebas para firmar JWT de test. |
| 2026-11-02 | api — H-API-01 | Caracterización corregida: `python -m pytest tests/test_api.py -q`; 8 pasaron, incluido rechazo de token legacy y conservación de respuestas. |
| 2026-11-02 | api — H-API-01, H-API-05 | Instalación de `requirements.txt`: éxito con `urllib3==2.8.0`, `python-multipart==0.0.30` y `PyJWT==2.10.1`; la consulta posterior detectó advisories para PyJWT, por lo que se cambió a 2.15.0. |
| 2026-11-02 | api — H-API-01, H-API-05 | `python -m pytest tests/test_api.py -q` con urllib3 2.8.0 y python-multipart 0.0.30: 8 pasaron. |
| 2026-11-02 | api — H-API-02 a H-API-04 | Pruebas de corrección antes de cambiar sus módulos: 5 fallaron como se esperaba (owner cliente, apóstrofo SQL, normalización, extensión y tamaño). |
| 2026-11-02 | api — H-API-02 a H-API-04, H-API-07 | Caracterización de error de transporte antes de corregir: 6 fallaron como se esperaba; se observó exposición del texto interno de excepción. |
| 2026-11-02 | api — H-API-01 a H-API-07 | `python -m compileall -q app`: terminó correctamente tras crear modelos, repositorio y servicios. |
| 2026-11-02 | api — H-API-01 a H-API-07 | Regresión tras conectar capas: `python -m pytest tests/test_api.py -q`; 12 pasaron. Cobertura: 96%. |
| 2026-11-02 | api — H-API-01, H-API-07 | Pruebas adicionales de expiración, issuer, audience, subject y configuración JWT faltante: 17 pasaron en la suite completa. |
| 2026-11-02 | api — H-API-01 | Prueba enfocada de claim obligatorio: antes del ajuste, 4 casos pasaron y el caso sin `exp` falló (respondió 200); tras exigir `exp`, los 5 casos pasaron. |
| 2026-11-02 | api — H-API-01 a H-API-07 | Actualización final a `PyJWT==2.15.0` e instalación de dependencias: éxito. `python -m pytest --cov=app --cov-report=term-missing`: 18 pasaron; cobertura total 97% (219 sentencias, 4 no cubiertas, 16 ramas, 4 parciales). Cobertura por módulo modificado: 100% en rutas, modelos y servicio de tareas; 93% en seguridad, repositorio y cargas. |
| 2026-11-02 | api — H-API-01 a H-API-07 | Documentación: `HALLAZGOS.md` actualizado; creados `docs/ENDPOINTS.md` y `docs/TESTING.md` con contrato observado, correcciones, resultados y riesgo de integración pendiente. |
| 2026-11-02 | api — H-API-04, H-API-05, H-API-01 | Revisión local `git diff main -- api/` y archivos nuevos: H-API-04 vuelve a Pendiente por sobrescritura de basename y porque el límite se aplica tras el parseo multipart. PyJWT 2.15.0 verificado contra advisories consultados. La rama observada fue `main`, no `day1/prompting`; no se presenta como revisión de esa rama. |
| 2026-11-03 | api — H-API-04 | Aprobación humana en sesión del issue, la spec funcional y la arquitectura ampliada: rechazo de colisión con `409` y recepción multipart incremental con límite por archivo. Rama local: `day1/prompting`. |
| 2026-11-03 | api — H-API-04 | Baseline tras restaurar las dependencias declaradas: `python tools/lab.py test api`, 18 pasaron y cobertura total 96.60%. La primera ejecución no recolectó pruebas porque faltaba PyJWT en el entorno virtual; no se modificaron manifests. |
| 2026-11-03 | api — H-API-04 | Caracterización previa a implementar: 2 pruebas fallaron como se esperaba (colisión sobrescribía; el cuerpo se consumía completo antes de 413); los otros 19 casos pasaron. |
| 2026-11-03 | api — H-API-04 | Validación final: `python tools/lab.py test api`, 24 pasaron; cobertura total 93.31%, `upload_service.py` 86% y `main.py` 97%. Sin integración real con auth; queda el aviso preexistente de deprecación AnyIO/Starlette. |
