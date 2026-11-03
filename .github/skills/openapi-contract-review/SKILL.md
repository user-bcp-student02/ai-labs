---
name: openapi-contract-review
description: "Valida contratos OpenAPI contra la taxonomía esperada, endpoints y errores HTTP. Úsalo para detectar errores funcionales y técnicos y generar un reporte de revisión de un contrato API."
---

# Skill: OpenAPI Contract Review

## 1) Metadata
- **Skill ID:** openapi-contract-review
- **Version:** 0.1.0
- **Propósito:** Revisar un contrato OpenAPI y reportar cumplimiento de taxonomía, cobertura de endpoints, manejo de errores HTTP y hallazgos funcionales/técnicos.
- **Audiencia:** Backend, Arquitectura, QA, Cyber y revisores de API.
- **Cuándo usar:** Cuando se diseñe, cambie o audite un contrato API en OpenAPI antes de implementación o publicación.
- **Cuándo no usar:** Para reemplazar pruebas de integración reales, pentesting o monitoreo en producción.
- **Datos sensibles:** No incluir secretos, tokens, credenciales ni datos reales de clientes.
- **Salida esperada:** Reporte Markdown estructurado con estado, hallazgos y recomendación de aprobación/bloqueo.

## 2) Inputs

Proporciona (o deduce del repositorio) la información en este formato:

- **OpenAPI source:** ruta del archivo o contenido (`yaml`/`json`).
- **Taxonomía esperada:** catálogo o reglas de clasificación (dominios, recursos, naming, tags, versionado).
- **Endpoints esperados:** lista de rutas y métodos requeridos.
- **Política de errores HTTP:** códigos requeridos por operación (4xx/5xx, formato de error, campos mínimos).
- **Reglas funcionales:** reglas de negocio que el contrato debe reflejar.
- **Reglas técnicas:** estándares técnicos (seguridad, headers, paginación, idempotencia, límites).
- **Criterio de severidad:** opcional (`critical`, `high`, `medium`, `low`).

Si falta información obligatoria, no la inventes: repórtala en **Pendientes**.

## 2.1) Fragmentos de contrato de referencia (basado en `SOURCES.md`)

Úsalos como guía durante la revisión. No reemplazan el contrato real.

### A) Estructura YAML mínima (OpenAPI 3.x)

```yaml
openapi: 3.0.3
info:
  title: Tasks API
  version: 1.0.0
servers:
  - url: https://api.example.com
paths:
  /v1/tasks:
    get:
      summary: List tasks
      operationId: listTasks
      tags: [tasks]
      responses:
        '200':
          description: OK
```

Checklist asociado:
- `openapi`, `info` y `paths` presentes (API-VERSION, INFO-REQUIRED, PATHS-REQUIRED).
- `servers` definido según entorno (SERVERS-DEFINED).
- YAML válido y consistente (VALID-YAML).

### B) Fragmento de operación (operation object)

```yaml
/v1/tasks/{taskId}:
  get:
    operationId: getTaskById
    tags: [tasks]
    parameters:
      - name: taskId
        in: path
        required: true
        description: Task identifier
        schema:
          type: string
    responses:
      '200':
        description: Task found
      '404':
        description: Task not found
```

Checklist asociado:
- `operationId` único y descriptivo (OPERATION-ID).
- Parámetros `in: path` marcados como `required: true` (PATH-PARAMETERS, PARAM-REQUIRED).
- Métodos HTTP coherentes con semántica REST (HTTP-METHODS, RFC 7231).

### C) Fragmento de errores HTTP estandarizados

```yaml
responses:
  '400':
    description: Bad Request
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/ErrorResponse'
  '401':
    description: Unauthorized
  '404':
    description: Not Found
  '500':
    description: Internal Server Error
components:
  schemas:
    ErrorResponse:
      type: object
      required: [code, message]
      properties:
        code:
          type: string
        message:
          type: string
        traceId:
          type: string
```

Checklist asociado:
- Respuestas de error definidas por operación crítica (ERROR-RESPONSES, RESPONSE-REQUIRED).
- Códigos HTTP alineados a RFC 7231 (RESPONSE-CODES).
- Esquema de error reutilizable en `components` (COMPONENTS-REUSE).

## 3) Procedimiento

1. **Cargar y validar estructura OpenAPI.** Confirmar versión, secciones mínimas (`info`, `paths`, `components` cuando aplique) y consistencia sintáctica.
2. **Evaluar taxonomía.** Verificar naming, tags, agrupación de recursos, versionado y convenciones definidas.
3. **Contrastar endpoints.** Comparar rutas/métodos esperados vs. definidos; marcar faltantes, sobrantes y diferencias.
4. **Revisar errores HTTP.** Por cada operación, validar cobertura de errores esperados, esquema de respuesta y estandarización de payload de error.
5. **Detectar errores funcionales.** Identificar inconsistencias de negocio (campos obligatorios, reglas de estado, contratos ambiguos o contradictorios).
6. **Detectar errores técnicos.** Identificar fallas de diseño técnico (seguridad, tipos, formatos, headers, status codes mal usados, paginación, rate limit, ejemplos inválidos).
7. **Emitir decisión.** Clasificar el contrato como `Aprobable`, `Aprobable con observaciones` o `Bloqueado`, con justificación.

## 4) Formato de salida

Devuelve **solo** un reporte Markdown con estas secciones:

1. **Estado de revisión**
   - `Aprobable`, `Aprobable con observaciones` o `Bloqueado`
   - Resumen ejecutivo (3-6 bullets)
2. **Cobertura de taxonomía**
   - Reglas evaluadas
   - Cumplidas / incumplidas
3. **Cobertura de endpoints**
   - Esperados y encontrados
   - Faltantes, sobrantes y diferencias
4. **Validación de errores HTTP**
   - Matriz por endpoint+operación con códigos esperados/definidos
   - Hallazgos de formato de error
5. **Errores funcionales**
   - Lista priorizada con severidad, endpoint y descripción
6. **Errores técnicos**
   - Lista priorizada con severidad, endpoint y descripción
7. **Hallazgos consolidados**
   - Tabla: `ID | Severidad | Tipo (Funcional/Técnico) | Endpoint | Hallazgo | Recomendación`
8. **Pendientes**
   - Datos faltantes para cerrar revisión
9. **Recomendación final**
   - Acciones mínimas para pasar a `Aprobable`

## 5) Reglas de validación

El reporte es válido si:
- Incluye todas las secciones de salida en el orden indicado.
- Evalúa explícitamente taxonomía, endpoints y errores HTTP.
- Separa hallazgos funcionales y técnicos.
- Cada hallazgo indica severidad y endpoint afectado (o `global` si aplica).
- No inventa reglas ni resultados sin evidencia del contrato o de los inputs.
- Declara pendientes cuando falte contexto crítico.
- No contiene datos sensibles ni secretos.

## 6) Restricciones

- No modificar el contrato original durante la revisión; solo analizar y reportar.
- No proponer excepciones que reduzcan controles de seguridad o calidad.
- No marcar `Aprobable` si existe al menos un hallazgo `critical` abierto.
- Mantener lenguaje claro, accionable y trazable a evidencia del contrato.
