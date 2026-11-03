---
description: Ejecuta la skill openapi-contract-review sobre un contrato OpenAPI y genera su reporte de revisión.
---

Usa la skill `openapi-contract-review` y sigue su procedimiento, reglas y formato de salida sin omitir secciones.

Revisa el contrato indicado. No modifiques el contrato ni otros archivos; analiza solamente y no inventes contexto o resultados que no estén respaldados por la especificación o los datos proporcionados.

## Entradas

- **OpenAPI source:** `${input:openapiSource:ruta del archivo YAML/JSON o contenido del contrato}`
- **Taxonomía esperada:** `${input:taxonomy:reglas de dominio, recursos, naming, tags y versionado; o indica que se deduzca del repositorio}`
- **Endpoints esperados:** `${input:endpoints:rutas y métodos requeridos; o indica que se deduzcan del repositorio}`
- **Política de errores HTTP:** `${input:errorPolicy:códigos y formato de error requeridos por operación}`
- **Reglas funcionales:** `${input:functionalRules:reglas de negocio que debe reflejar el contrato}`
- **Reglas técnicas:** `${input:technicalRules:estándares técnicos que debe cumplir el contrato}`
- **Criterio de severidad:** `${input:severity:critical, high, medium, low, o predeterminado de la skill}`

Si una entrada no se proporciona o no puede deducirse con evidencia disponible, no la asumas: registra la información faltante en **Pendientes**. Usa solo datos ficticios y no expongas secretos, tokens, credenciales ni datos reales. Distingue los hallazgos observados de cualquier hipótesis y vincúlalos con la operación afectada o con `global`.

Devuelve únicamente el reporte Markdown, en el orden y con las secciones exactas definidas por la skill:
1. Estado de revisión
2. Cobertura de taxonomía
3. Cobertura de endpoints
4. Validación de errores HTTP
5. Errores funcionales
6. Errores técnicos
7. Hallazgos consolidados
8. Pendientes
9. Recomendación final
