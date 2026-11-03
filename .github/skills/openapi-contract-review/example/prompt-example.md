Ejecuta la skill "openapi-contract-review" usando este contrato:
- OpenAPI source: .github/skills/openapi-contract-review/example/auth-contract-example.yaml

Contexto de validación:
- Taxonomía esperada:
  - Dominio: auth
  - Tags permitidos: auth
  - Rutas versionadas opcionales para auth, pero /auth/login debe conservarse
  - operationId único en toda la especificación
- Endpoints esperados:
  - POST /auth/login
  - POST /auth/refresh
- Política de errores HTTP:
  - /auth/login: 400, 401, 429, 500
  - /auth/refresh: 400, 401, 500
  - Formato de error: code (string), message (string), traceId (string opcional)
- Reglas funcionales:
  - LoginRequest debe requerir username y password
  - LoginResponse debe incluir accessToken, refreshToken, tokenType y expiresIn
- Reglas técnicas:
  - OpenAPI 3.0.x o 3.1.x
  - requestBody en JSON para operaciones POST
  - Responses 2xx y 4xx/5xx documentadas por operación
  - Esquemas reutilizables en components/schemas

Devuélveme el reporte con el formato de la skill:
1) Estado de revisión
2) Cobertura de taxonomía
3) Cobertura de endpoints
4) Validación de errores HTTP
5) Errores funcionales
6) Errores técnicos
7) Hallazgos consolidados
8) Pendientes
9) Recomendación final
