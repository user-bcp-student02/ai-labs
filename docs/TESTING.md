# Evidencia de pruebas — componente api

## Entorno y comando final

- Python: 3.13.16.
- Ejecutado desde `api/`: `python -m pytest --cov=app --cov-report=term-missing`.
- Resultado final: 18 pruebas pasaron; cobertura total de `app` 97% (219 sentencias, 4 no cubiertas; 16 ramas, 4 parciales).
- Cobertura por módulo modificado: `main.py`, `schemas.py` y `services/tasks_service.py` 100%; `security.py`, `repositories/tasks_repository.py` y `services/upload_service.py` 93%. Todos superan 85%.
- Diagnósticos del editor: sin errores en los módulos y pruebas modificados.

## Antes y después por hallazgo

| ID | Caracterización antes de corregir | Resultado después |
|---|---|---|
| H-API-01 | La suite de caracterización inicial pasó con el token Base64 legacy; las rutas protegidas rechazaban el token inválido con 401. | 18 pruebas pasan. JWT válido mantiene 200/201; token legacy, firma/claims inválidos, `exp` ausente y configuración faltante responden 401 genérico. |
| H-API-02 | `test_owner_is_bound_to_authenticated_identity` falló antes del cambio: `owner=trainer` devolvía tareas de `trainer`. La creación también atribuía el owner solicitado por el cliente. | La misma prueba pasa: listado, resumen y creación usan solo `student` del token. |
| H-API-03 | `test_task_title_with_apostrophe_is_stored_as_plain_text` falló antes del cambio con 500. | La prueba pasa con 201 y conserva el título `Owner's notes`; las consultas usan parámetros SQLite. |
| H-API-04 | Las pruebas de regresión reprodujeron que la segunda carga con el mismo basename respondía 201 y reemplazaba el archivo, y que el transporte síncrono consumía todos los bloques antes de responder 413. | 24 pruebas pasan: colisión secuencial y concurrente responde 409 sin reemplazo, el tamaño exacto se acepta, el exceso responde 413 sin archivo parcial y el parser incremental no consume el resto del cuerpo. OpenAPI conserva el campo multipart obligatorio y documenta 400/409/413. |
| H-API-05 | Baseline anterior a los pins: 4 pruebas pasaron con `urllib3==2.5.0` y `python-multipart==0.0.21`. La consulta de GitHub Advisory Database confirmó versiones afectadas. | Instalación de `urllib3==2.8.0`, `python-multipart==0.0.30` y `PyJWT==2.15.0` exitosa; 18 pruebas pasan. La revisión de advisories fue manual, no se ejecutó `pip-audit`. |
| H-API-06 | Caracterización anterior: 7 pruebas pasaron y fijaron las formas de respuesta y estados de éxito. | 18 pruebas pasan; las rutas delegan en servicios/repositorio y conservan las formas documentadas en `ENDPOINTS.md`. |
| H-API-07 | La prueba de fallo de backend falló antes del cambio porque el cuerpo devolvía `None: internal-proxy-secret`. | La misma prueba pasa con `status: 0` y `body: Backend unavailable`; respuesta tipada y cálculo de resumen sin anidamiento profundo. |

## Validación intermedia registrada

`PLAN.md` conserva la secuencia completa, incluidos los resultados fallidos de caracterización y dos intentos iniciales de validación de H-API-01 que detectaron un módulo omitido y un helper de test todavía legacy. No se ocultaron ni se confundieron con el resultado final.

- `python -m compileall -q app`: terminó correctamente.
- Suite tras conectar capas: 12 pruebas pasaron; cobertura 96%.
- La prueba nueva de JWT sin `exp` reprodujo 200 antes del ajuste; después de exigir el claim, los cinco casos parametrizados de claims inválidos pasaron.
- Suite final tras ampliar casos JWT y fijar PyJWT 2.15.0: 18 pasaron; cobertura 97%.
- Los únicos avisos en la ejecución final fueron deprecaciones de AnyIO/Starlette en `TestClient`; no afectaron los resultados.

## Evidencia externa de dependencias

- `urllib3==2.5.0` estaba afectado por [GHSA-8988-9cw3-xx77](https://github.com/advisories/GHSA-8988-9cw3-xx77) y [GHSA-vxq7-64xx-v4gw](https://github.com/advisories/GHSA-vxq7-64xx-v4gw); el parche indicado es 2.8.0. [GHSA-gh4c-6fx4-qh6g](https://github.com/advisories/GHSA-gh4c-6fx4-qh6g) afecta 2.6.2–2.7.0 y también está corregido en 2.8.0.
- `python-multipart==0.0.21` estaba afectado por [CVE-2026-53539 / GHSA-5rvq-cxj2-64vf](https://github.com/advisories/GHSA-5rvq-cxj2-64vf); el parche indicado es 0.0.30.
- PyJWT 2.10.1 no se conservó: [GHSA-pxh4-856f-4h89](https://github.com/advisories/GHSA-pxh4-856f-4h89) y [GHSA-9j54-fg26-wv3r](https://github.com/advisories/GHSA-9j54-fg26-wv3r) indican corrección en 2.14.0, y [GHSA-x33g-cr3x-6449](https://github.com/advisories/GHSA-x33g-cr3x-6449) en 2.15.0. El pin final es `PyJWT==2.15.0`.

## Pendientes

No se ejecutó integración real con auth ni un scanner automatizado de dependencias. La compatibilidad del emisor JWT debe coordinarse fuera de `api/`; cobertura y pruebas de TestClient no sustituyen esa verificación.

## Validación de H-API-04

- Baseline después de restaurar las dependencias declaradas: `python tools/lab.py test api` — 18 pasaron; cobertura total 96.60%.
- Caracterización antes de implementar: la misma suite tuvo 2 fallos esperados (sobrescritura por colisión y consumo completo del cuerpo); los otros 19 casos pasaron.
- Resultado final desde la raíz: `python tools/lab.py test api` — 24 pasaron; cobertura total 93.31%; `upload_service.py` 86% y `main.py` 97%, sobre la meta de 85%.
- La prueba de recepción incremental usa `httpx.ASGITransport` con un cuerpo asíncrono sin `Content-Length` y confirma que se rechaza al primer byte sobre el límite sin solicitar el bloque restante.
- Permanece un aviso de deprecación de AnyIO/Starlette desde `TestClient`; no afecta los resultados. No se ejecutó integración real con auth ni un scanner automatizado de dependencias.
- El parser extiende callbacks de Starlette; repetir esta suite al actualizar FastAPI/Starlette es una validación de compatibilidad pendiente.
