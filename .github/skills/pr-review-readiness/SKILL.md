---
name: pr-review-readiness
description: "Valida si un cambio tiene la información y evidencia necesarias para revisión de PR en AI Labs. Úsalo para preparar o revisar datos de un PR, comprobar el detalle de cambios, pruebas y riesgos, o generar un checklist y diagrama de secuencia."
---

# Validación de información para revisión de PR

## 1) Metadata
- **Skill ID:** pr-review-readiness
- **Version:** 0.1.0
- **Propósito:** Comprobar que la descripción de un cambio permite una revisión humana informada, trazable y basada en evidencia.
- **Audiencia:** Autores, revisores y QA.
- **Cuándo usar:** Antes de solicitar revisión o para validar la descripción y evidencias de un PR.
- **Cuándo no usar:** Para reemplazar una revisión de código, aprobación humana, ejecución de pruebas o revisión de seguridad especializada.
- **Datos sensibles:** No copies secretos, tokens, credenciales, datos reales de clientes ni valores de producción.
- **Salida esperada:** Reporte Markdown con estado de preparación, datos faltantes, diagrama de secuencia si corresponde, checklist detallado y evidencia.

## 2) Datos de entrada

Usa la información disponible en la descripción del PR, el diff y los documentos del repositorio. Solicita únicamente los datos importantes que no puedan verificarse.

- **Issue o solicitud relacionada:**
- **Componente afectado:** `auth`, `api` o `web`
- **Problema o comportamiento anterior:**
- **Cambio realizado y resultado esperado:**
- **Archivos o áreas afectadas:**
- **Pruebas y comandos ejecutados, con resultados:**
- **Riesgos, compatibilidad y mitigaciones:**
- **Decisión de revisión humana:** pendiente hasta que la registre una persona revisora

Si hay un diff local disponible, úsalo para contrastar la descripción y no pidas al usuario que repita información visible en el diff. Si falta contexto que el diff no puede confirmar, márcalo como pendiente; no lo completes por inferencia.

## 3) Procedimiento

1. **Determina el alcance.** Identifica el componente principal y revisa solo los archivos necesarios dentro de ese alcance. No inspecciones otros componentes sin autorización.
2. **Consulta la plantilla del PR.** Verifica los apartados requeridos en `.github/PULL_REQUEST_TEMPLATE.md` y conserva sus encabezados:
   - **Cambio:** Issue, IDs H-xx, componentes, comportamiento anterior y resultado esperado.
   - **Comprobación:** comandos, resultados reales y pendientes; referencias a `PLAN.md`, `docs/ENDPOINTS.md` y `docs/TESTING.md` cuando sustenten el cambio.
   - **Revisión humana:** decisión y motivos registrados por una persona.
3. **Contrasta la descripción con los cambios.** Resume cada archivo o grupo de archivos por su efecto funcional; señala cambios relevantes que no estén descritos y afirmaciones sin sustento.
4. **Valida las comprobaciones.** Distingue claramente entre pruebas ejecutadas y aprobadas, fallidas, pendientes o no ejecutadas. No afirmes que una prueba pasó si no hay una salida verificable. No ejecutes comandos que modifiquen datos o el estado del repositorio sin autorización.
5. **Revisa riesgos y contratos aplicables.** Considera compatibilidad, endpoints, seguridad, datos y quality gates según el componente y sus instrucciones. No propongas ocultar alertas, debilitar controles ni excluir scanners para hacer pasar la revisión.
6. **Prepara el diagrama.** Si el cambio modifica un flujo observable o una interacción entre componentes, genera un diagrama Mermaid `sequenceDiagram` usando actores y pasos respaldados por el código o la descripción. Para cambios sin flujo secuencial, indica `No aplica` y explica brevemente por qué.
7. **Evalúa preparación.** Marca como **Listo para revisión** solo si los campos obligatorios están completos, los cambios están descritos y la evidencia comunicada es verificable. En otro caso, marca **Faltan datos** o **Bloqueado**, lista lo necesario y no simules aprobaciones.

## 4) Formato de salida

Devuelve un reporte Markdown conciso con estas secciones:

1. **Estado:** `Listo para revisión`, `Faltan datos` o `Bloqueado`, con una justificación breve.
2. **Datos requeridos**
   - Issue y componente.
   - IDs H-xx, si aplican.
   - Comportamiento anterior y resultado esperado.
   - Elementos faltantes o no verificables.
3. **Resumen y detalle de cambios**
   - Qué cambió y por qué.
   - Archivos afectados y su responsabilidad en el cambio, según evidencia disponible.
   - Impacto en endpoints, contratos o comportamiento visible, si aplica.
4. **Diagrama de secuencia**
   - Diagrama Mermaid si hay un flujo relevante; de lo contrario, `No aplica` con motivo.
5. **Checklist para revisión**
   - [ ] El Issue o solicitud está referenciado.
   - [ ] Se indican componente e IDs de hallazgo que aplican.
   - [ ] Se explica comportamiento anterior y resultado esperado.
   - [ ] Cada cambio relevante está descrito y concuerda con el diff disponible.
   - [ ] Se documentan compatibilidad, riesgos y mitigaciones relevantes.
   - [ ] Se listan comandos ejecutados con resultados y pruebas pendientes.
   - [ ] Se incluyen referencias a documentación que sustenta el cambio, cuando corresponda.
   - [ ] La decisión y los motivos de revisión humana quedan pendientes hasta que los registre una persona.
   - Añade ítems específicos para los archivos, contratos y riesgos de este cambio; no dejes el checklist solo en estos puntos genéricos.
6. **Comprobación**
   - Comando exacto — resultado observado (`aprobado`, `fallido`, `pendiente` o `no ejecutado`).
   - No presentes comandos sugeridos como si ya se hubieran ejecutado.
7. **Pendientes para el autor o revisor**
   - Preguntas concretas o evidencia que hace falta para poder revisar.

## 5) Reglas de validación

El reporte es válido si:
- Incluye todos los apartados de salida y refleja los encabezados de la plantilla del PR.
- El detalle de cambios se apoya en el diff o en información explícita; no inventa propósito, archivos, decisiones ni impacto.
- El diagrama representa únicamente interacciones verificables, o explica por qué no aplica.
- Cada prueba tiene un estado explícito y evidencia real; los resultados desconocidos quedan como pendientes.
- Los riesgos y contratos se evalúan solo para el alcance aplicable.
- La revisión humana no se marca como aprobada ni se atribuye a un agente.
- No incluye secretos, datos sensibles ni afirmaciones de seguridad no comprobadas.

## 6) Restricciones del repositorio

- Respeta las instrucciones del componente `auth`, `api` o `web` en `AGENTS.md`.
- Conserva los contratos de endpoints usados por los componentes relacionados y no reduzcas los quality gates.
- Para cambios en el Día 1, respeta el cierre local descrito en `AGENTS.md`: no crees PR, remotos ni tags, no hagas push y no inventes revisión, aprobación o pruebas reales.
- Este skill prepara información para una revisión humana; no modifica la descripción del PR ni crea, actualiza o integra un PR por cuenta propia.
