# Informe de seguridad — H-AUTH-04

**Feature:** Distinguir fallos de base de datos de credenciales inválidas en `POST /auth/login`  
**Componente:** `auth`  
**Severidad del hallazgo:** Media  
**Evaluación:** No aprobado para liberación con la evidencia disponible  
**Estado de verificación:** Pendiente; no se ejecutaron herramientas ni pruebas

## 1. Alcance

Se revisó la evidencia proporcionada para H-AUTH-04 y las propuestas de desarrollo y pruebas del historial. La propuesta de transferencias entre cuentas no corresponde a esta feature y queda fuera de alcance.

El problema descrito es que un error de inicialización o consulta en `LegacyDatabase` se convierte en `null`, y `AuthResource` puede presentarlo como rechazo de autenticación (`401`). Esto oculta una falla operativa y hace que un error de persistencia sea indistinguible de credenciales inválidas.

No se proporcionó evidencia de cambios aplicados en los archivos reales ni de una ejecución de pruebas. Por ello, este informe evalúa las propuestas, no una corrección verificada en el producto.

## 2. Evidencia revisada y limitaciones

- Referencias del hallazgo: `LegacyDatabase.java:16-28,34-43` y `AuthResource.java:35-38`.
- Propuesta de backend con `LoginRepository`, `LoginService` y `PersistenceFailureException`.
- Propuesta de frontend que distingue `401` de estados `5xx` y no muestra el cuerpo del error del servidor.
- Informe de pruebas que indica **PLANIFICADO — NO EJECUTADO**.
- No se proporcionó el contrato completo vigente de respuesta de `POST /auth/login`, ni evidencia de que las propuestas estén conectadas a `LegacyDatabase` y `AuthResource`.

## 3. Evaluación de criterios de aceptación

| Criterio | Evaluación | Estado |
|---|---|---|
| Credenciales incorrectas conservan el rechazo de autenticación definido por el login. | La propuesta de servicio conserva una excepción de credenciales inválidas, pero no se verificó el endpoint real ni se ejecutaron pruebas HTTP. El cuerpo exacto del rechazo tampoco está documentado en la evidencia recibida. | **No verificado** |
| Un fallo de base de datos se propaga y se representa como error de servicio, no como credenciales inválidas. | La abstracción propuesta separa la excepción del repositorio de la ausencia de usuario. Sin embargo, no se demostró que el adaptador real de base de datos deje de devolver `null` ni que el error llegue al manejador HTTP. | **No verificado** |
| La respuesta del error de servicio no expone detalles internos ni credenciales. | La propuesta frontend no muestra el cuerpo del error, lo cual es favorable para la interfaz. No hay evidencia de la respuesta HTTP real ni de que el manejador del backend omita excepción, causa, SQL, stack trace y datos de autenticación. | **No verificado** |
| Las pruebas cubren credenciales incorrectas y fallo simulado de persistencia, y verifican el contrato funcional. | El informe de pruebas es un plan y marca los casos como no ejecutados. Las pruebas unitarias propuestas no bastan para demostrar el comportamiento del endpoint ni su respuesta HTTP. | **No verificado** |

## 4. Hallazgos de seguridad

### H-AUTH-04 — Conversión de errores de persistencia en rechazo de autenticación

**Severidad:** Media  
**Estado:** Abierto; no hay evidencia de corrección aplicada y validada.

La implementación descrita puede representar una indisponibilidad o error de persistencia como credenciales inválidas. Esto dificulta el diagnóstico y la respuesta operativa, y puede llevar a clientes o sistemas de monitoreo a tratar un incidente de servicio como un rechazo normal de autenticación.

La evidencia no indica una vía de omisión de autenticación ni permite concluir que el problema sea crítico. Sin embargo, el comportamiento de error debe corregirse y comprobarse antes de aprobar la liberación de esta feature.

### Observación sobre la propuesta de backend

La propuesta acierta en el principio de diseño: el repositorio debe distinguir “usuario inexistente” de “error de persistencia”, y el servicio no debe convertir la segunda condición en credenciales inválidas. También es razonable conservar la causa para diagnóstico interno, siempre que no se serialice ni se exponga al cliente.

La propuesta no demuestra la conexión con las clases reales citadas por el hallazgo. Además, el manejador HTTP y el formato de error continúan pendientes, por lo que todavía no se acredita el contrato requerido.

### Observación sobre la propuesta de frontend

Clasificar `401` como error de credenciales y los errores de red o `5xx` como error de servicio es coherente como comportamiento de presentación. No demuestra por sí solo que el backend propague los fallos correctamente. La interfaz debe seguir mostrando mensajes genéricos y no usar el cuerpo no confiable del error como mensaje visible.

## 5. Condiciones para una nueva revisión

Para reconsiderar la aprobación, se requiere evidencia de que:

1. `LegacyDatabase` propaga o envuelve los fallos de inicialización y consulta; no los convierte en `null` ni en “usuario inexistente”.
2. `AuthResource` conserva el rechazo de autenticación definido para credenciales incorrectas y transforma los fallos de persistencia en el error de servicio especificado por el contrato aprobado.
3. La respuesta de servicio es genérica y no incluye credenciales, SQL, nombres de tablas, clases de excepción, causas ni stack traces.
4. Se confirma el estado HTTP y el esquema de error aprobados para el login. No debe inferirse un `503` a partir de la propuesta de transferencias, que es ajena a este alcance.
5. Se ejecutan pruebas que cubran, como mínimo:
   - contraseña incorrecta y usuario inexistente, con el rechazo de autenticación esperado;
   - fallo simulado de persistencia, sin conversión a rechazo de autenticación;
   - respuesta HTTP del endpoint ante el fallo, verificando el estado y cuerpo conforme al contrato;
   - ausencia de detalles internos y credenciales en la respuesta.
6. Se presenta la salida de pruebas y se confirma que se realizaron en un entorno aislado con datos sintéticos.

## 6. Decisión

**No recomiendo conceder la aprobación de seguridad para H-AUTH-04 en este momento.** La separación propuesta es adecuada como diseño, pero la corrección no está acreditada en los adaptadores reales y las pruebas están planificadas, no ejecutadas.

No se identifican hallazgos críticos en la evidencia recibida. El hallazgo medio H-AUTH-04 permanece abierto y no se proporcionó una excepción formal y documentada. La aprobación queda pendiente de la evidencia indicada en este informe.