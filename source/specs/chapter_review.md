# `chapter_review.md`

## VEREDICTO: RECHAZADO

## Resumen

La entrega revisada corresponde principalmente a **H-AUTH-04**, que trata de distinguir fallos de persistencia de credenciales inválidas en `POST /auth/login`. El historial también contiene una propuesta de transferencia entre cuentas propias, pero esa funcionalidad tiene otro alcance y no forma parte del brief de H-AUTH-04. No se recibió un contrato aprobado ni evidencia de implementación de transferencias; por tanto, no se evalúa ni se considera aprobada esa propuesta.

Para H-AUTH-04 hay un diseño backend propuesto que separa “usuario inexistente” de “fallo del repositorio”, y una propuesta frontend que no muestra el cuerpo de errores del servidor. Sin embargo, no hay evidencia de que esos cambios estén integrados en `LegacyDatabase` y `AuthResource`, ni de que el endpoint devuelva el error de servicio requerido y una respuesta sanitizada. Las pruebas están descritas como planificadas, no ejecutadas; además, no se proporcionó el contrato HTTP completo para el error de servicio.

La decisión es **RECHAZADO**: los criterios no cuentan con evidencia suficiente de implementación y verificación de extremo a extremo. Este informe es una evaluación técnica; no concede, sustituye ni presume aprobaciones humanas de Producto, Backend, Seguridad u otras áreas.

## Evidencia por criterio

La evidencia citada es la disponible en el historial; no se proporcionó acceso verificable al repositorio completo ni una ejecución de pruebas. “Propuesta” indica código o diseños presentados, no un cambio confirmado en el producto.

| Criterio de aceptación | Arquitectura, persistencia y modelo de datos | Implementación y contrato | Pruebas | Seguridad | Estado |
|---|---|---|---|---|---|
| **1. Credenciales incorrectas siguen produciendo el rechazo definido por `POST /auth/login`.** | El backend propone `LoginService` y `LoginRepository`; el contrato propuesto reserva `Optional.empty()` para usuario inexistente. No hay evidencia de integración con el repositorio real. | El brief reporta que `AuthResource` presenta credenciales incorrectas como `401`. El frontend propuesto también clasifica `401` como error de credenciales. No se verificó el endpoint real ni el cuerpo exacto de rechazo. | El código propuesto de `LoginServiceTest` incluye contraseña incorrecta y usuario inexistente, pero no se presentó resultado de ejecución. No hay evidencia de prueba HTTP que confirme el contrato completo. | El frontend propuesto no presenta el cuerpo del error; no se verificó la respuesta real del backend. | **No verificado de extremo a extremo.** |
| **2. Un fallo de base de datos se propaga y se representa como error de servicio, no como credenciales inválidas.** | El diseño propuesto declara que el repositorio debe propagar errores y que `Optional.empty()` significa únicamente “usuario inexistente”. No se acreditó un cambio en el adaptador de `LegacyDatabase` ni en su inicialización o consulta. | El brief identifica capturas de excepciones en `LegacyDatabase.java:16-28,34-43` y una presentación del resultado como rechazo en `AuthResource.java:35-38`. El servicio propuesto no convierte la excepción del repositorio, pero no se aportó el manejador HTTP que la transforme en error de servicio. | Hay un test unitario propuesto que simula una excepción del repositorio. No hay resultado de ejecución ni prueba del estado y cuerpo HTTP ante un fallo real o simulado del adaptador. | No se acreditó que la respuesta del endpoint distinga operativamente el fallo del rechazo de autenticación sin filtrar información interna. | **No demostrado en los adaptadores reales ni en HTTP.** |
| **3. La respuesta del error de servicio no expone detalles internos ni credenciales.** | `PersistenceFailureException` propuesta conserva la causa. Eso puede servir para diagnóstico interno, pero requiere que el manejador HTTP no serialice la excepción ni su causa. No hay evidencia del modelo real de error HTTP. | El manejador de `AuthResource` y su esquema de error no fueron proporcionados. El frontend propuesto no muestra el cuerpo recibido, pero esto no garantiza que la respuesta del backend esté sanitizada. | El test unitario propuesto comprueba el mensaje de `PersistenceFailureException`; no inspecciona el cuerpo HTTP ni comprueba ausencia de SQL, stack trace, causa, credenciales u otros datos internos. | La no exposición se considera pendiente hasta comprobar la respuesta HTTP real. | **No verificado.** |
| **4. Las pruebas cubren credenciales incorrectas y fallo simulado de persistencia, y verifican que el contrato funcional del login se conserva.** | La propuesta separa las responsabilidades a nivel de servicio, pero no hay evidencia de integración con persistencia ni del flujo completo. | El contrato conocido incluye `POST /auth/login`; el estado y cuerpo exactos del error de servicio, y el contrato completo de éxito, no fueron proporcionados. No se debe inferir el estado HTTP a partir de la propuesta de transferencias. | El informe de pruebas marca su estado como **PLANIFICADO — NO EJECUTADO**. Los tests backend son fragmentos propuestos, no resultados. No se presentó una prueba HTTP ejecutada que cubra ambos casos y el contrato funcional. | La propuesta frontend clasifica estados HTTP y oculta el cuerpo del error; no sustituye pruebas de backend ni de seguridad de la respuesta. | **No cumplido con evidencia disponible.** |

### Alcance y fuentes revisadas

- **Brief H-AUTH-04:** describe la conversión de errores de inicialización o consulta de `LegacyDatabase` en `null` y su presentación como rechazo `401` en `AuthResource`.
- **Propuesta de backend:** fragmentos para `LoginRepository`, `LoginCredential`, `LoginService`, `PersistenceFailureException` y `LoginServiceTest`. No se acredita que estén aplicados al proyecto.
- **Propuesta de frontend:** servicio y componente Angular para `/auth/login`, clasificación de `401` frente a `5xx` y manejo de errores sin mostrar el cuerpo del servidor. El fragmento de `auth.service.spec.ts` está truncado en el historial, por lo que su cobertura completa no puede determinarse.
- **Informe de pruebas:** declara explícitamente **PLANIFICADO — NO EJECUTADO**.
- **Informe de seguridad:** también declara que no se ejecutaron herramientas ni pruebas y que la integración con los adaptadores reales no está demostrada.
- **Propuesta de transferencia:** el documento de arquitectura la identifica como pendiente de aprobación y reconoce que es distinta de H-AUTH-04. No constituye evidencia de implementación de login ni un contrato aprobado para transferencias.

## Hallazgos y correcciones requeridas

### H1 — El cambio no está acreditado en los adaptadores reales

- **Criterio afectado:** 1 y 2.
- **Impacto:** un fallo de persistencia todavía podría convertirse en rechazo de autenticación si el comportamiento señalado en el brief permanece en `LegacyDatabase` y `AuthResource`. Esto oculta indisponibilidad o errores operativos.
- **Evidencia:** el brief cita `LegacyDatabase.java:16-28,34-43` y `AuthResource.java:35-38`. La propuesta backend declara expresamente que falta conectarla a esos adaptadores.
- **Responsable/área:** equipo responsable del componente `auth` / Backend. La persona responsable no está identificada en la evidencia.
- **Cambio requerido:** eliminar la conversión de errores de inicialización o consulta a `null` o a “usuario inexistente”; propagar o envolver los fallos con una excepción de persistencia. En `AuthResource`, conservar el rechazo de credenciales inválidas y mapear la excepción de persistencia al error de servicio definido para el login.
- **Cómo verificar:** inspeccionar el flujo real desde `LegacyDatabase` hasta `AuthResource`; probar credenciales incorrectas y una excepción simulada del repositorio en el endpoint; confirmar que solo el primer caso produce el rechazo de autenticación.

### H2 — Falta cerrar el contrato HTTP del error de servicio

- **Criterio afectado:** 2, 3 y 4.
- **Impacto:** no se puede afirmar que el endpoint conserva un contrato funcional compatible ni que el fallo se representa de forma interoperable. Tampoco se puede comprobar la respuesta exacta sin conocer estado y esquema aprobados.
- **Evidencia:** el informe de pruebas indica que el estado y el cuerpo del error de login están pendientes de confirmar. La propuesta de transferencias no es un contrato para `POST /auth/login`.
- **Responsable/área:** responsable de API/Backend junto con el dueño del contrato del login; no se identificó quién tiene la autoridad de aprobación.
- **Cambio requerido:** documentar el estado HTTP y cuerpo aprobados para el error de servicio, así como el contrato de rechazo por credenciales incorrectas y, si corresponde, el contrato de éxito. No asumir `503` ni un esquema de transferencia para el login.
- **Cómo verificar:** revisar la especificación aprobada y comprobar mediante pruebas HTTP que el endpoint cumple esos estados y cuerpos para rechazo, fallo de persistencia y éxito.

### H3 — Sanitización de la respuesta HTTP no demostrada

- **Criterio afectado:** 3.
- **Impacto:** si el manejador HTTP serializa la excepción, su causa o un error de base de datos, podría revelar detalles internos o información de autenticación.
- **Evidencia:** `PersistenceFailureException` propuesta conserva la causa; no se proporcionó el manejador HTTP real ni una respuesta observada. El frontend propuesto no presenta el cuerpo, lo que no demuestra que el backend lo sanitice.
- **Responsable/área:** Backend/Auth para el manejo de errores; Security debe revisar la respuesta. Los responsables individuales no constan.
- **Cambio requerido:** construir la respuesta de servicio desde un mensaje y código públicos genéricos aprobados. Mantener los detalles técnicos únicamente en canales internos autorizados y evitar incluir credenciales o datos sensibles.
- **Cómo verificar:** provocar un error con un marcador interno sintético y revisar la respuesta HTTP completa; comprobar que no contiene marcador, SQL, nombres de tablas, stack trace, clase o causa de la excepción, username ni contraseña.

### H4 — Pruebas propuestas, pero no ejecutadas ni suficientes para el contrato HTTP

- **Criterio afectado:** 1, 2, 3 y 4.
- **Impacto:** no hay evidencia de que la corrección compile, funcione en el endpoint real o preserve el contrato de login. Los tests de servicio no prueban el mapeo HTTP ni la sanitización de la respuesta.
- **Evidencia:** `testing_report.md` indica **PLANIFICADO — NO EJECUTADO**; el informe de seguridad también indica que no se ejecutaron pruebas. Los tests Java aparecen como código propuesto, sin salida de ejecución.
- **Responsable/área:** QA/Testing para ejecución y evidencia; Backend/Auth para pruebas de integración. No se identificó quién mantiene el pipeline o la configuración de pruebas.
- **Cambio requerido:** implementar o completar pruebas unitarias y de integración HTTP para credenciales incorrectas, usuario inexistente, fallo de persistencia y respuesta sanitizada; verificar también que el contrato de éxito existente no se haya alterado.
- **Cómo verificar:** ejecutar las pruebas en el proyecto con el entorno aislado aprobado, presentar comandos y resultados, y revisar las respuestas reales del endpoint.

### H5 — Incompatibilidad de alcance: transferencia frente a H-AUTH-04

- **Criterio afectado:** trazabilidad y definición de alcance de la entrega; no es un criterio funcional de H-AUTH-04.
- **Impacto:** puede mezclarse una propuesta no aprobada de transferencias con los criterios de login y producirse una aprobación sobre una funcionalidad distinta de la solicitada.
- **Evidencia:** la historia de arquitectura identifica que el brief recibido es H-AUTH-04 y que la transferencia es una propuesta pendiente de aprobación. El resto del historial de desarrollo y pruebas se concentra en login.
- **Responsable/área:** solicitante/producto y responsable de arquitectura para aclarar el alcance; no se identificó quién debe emitir la decisión de producto.
- **Cambio requerido:** mantener esta revisión limitada a H-AUTH-04. Si también se requiere una transferencia, solicitar una historia, criterios y contrato aprobados por separado y evaluar esa solución en una revisión independiente.
- **Cómo verificar:** confirmar que el alcance de la entrega y los criterios de aceptación aprobados corresponden a H-AUTH-04; mantener cualquier propuesta de transferencias claramente separada.

## Observaciones por rol

- **Arquitectura:** la propuesta de transferencia reconoce que es distinta de H-AUTH-04 y que su API está pendiente de aprobación. Es correcto no tratarla como contrato existente. Para el login, falta evidencia de la arquitectura aplicada al producto real y de la conexión entre puerto de repositorio, adaptador de persistencia y adaptador HTTP.
- **Backend:** la separación propuesta entre usuario inexistente y error del repositorio es adecuada a nivel de servicio. No demuestra una corrección aplicada en `LegacyDatabase` o `AuthResource`; el mapeo HTTP continúa pendiente.
- **Frontend:** la propuesta distingue `401` de fallos de red y `5xx`, y no muestra el cuerpo del error. Esto es favorable como presentación, pero no verifica el comportamiento del backend. Los nombres de campos y el contrato de respuesta deben confirmarse contra la API vigente.
- **Testing:** el informe declara que los casos están planificados, no ejecutados. Hace falta evidencia ejecutable de integración HTTP, no solo tests de servicio o una matriz de resultados esperados.
- **Seguridad:** la respuesta genérica es un requisito pendiente de demostrar. Conservar causas de excepciones internamente puede ser compatible con el requisito solo si no se filtran al cliente y se gestionan adecuadamente.

## Pruebas ejecutadas y pendientes

### Ejecutadas

No se proporcionó evidencia de pruebas ejecutadas ni resultados. En esta revisión no se ejecutaron pruebas. Los fragmentos de pruebas del historial se consideran propuestas, no ejecuciones.

### Pendientes

| Prueba | Verificación requerida | Razón pendiente |
|---|---|---|
| Unitarias de rechazo por contraseña incorrecta y usuario inexistente | El servicio conserva la excepción de credenciales inválidas y no clasifica esos casos como fallos de servicio. | El código propuesto no tiene resultados de ejecución; tampoco se aportó la configuración de compilación del proyecto. |
| Unitaria de fallo de persistencia | La excepción se propaga o envuelve y no se convierte en credenciales inválidas ni en ausencia de usuario. | Solo hay un test propuesto, sin resultado ejecutado; falta confirmar la integración con el repositorio real. |
| HTTP de credenciales incorrectas | `POST /auth/login` conserva el estado y cuerpo de rechazo aprobados. | No hay resultado del endpoint y no se documentó el cuerpo completo del contrato. |
| HTTP de fallo de consulta | El endpoint devuelve el error de servicio aprobado, distinto de rechazo de autenticación, y conserva el contrato funcional. | No hay prueba de integración ejecutada; falta confirmar el estado y esquema de error aprobados. |
| HTTP de error sanitizado | La respuesta no expone excepciones, causas, SQL, stack traces ni credenciales. | No se proporcionó ni probó el manejador HTTP real. |
| Regresión de login exitoso | La respuesta de éxito conserva el contrato existente. | El contrato de éxito no está disponible en la evidencia y no se presentó un resultado de integración. |
| Fallo durante inicialización de persistencia | El comportamiento de la aplicación y del login ante fallo de inicialización coincide con el contrato operativo aprobado. | La matriz del informe de pruebas lo deja pendiente; no se especificó el comportamiento esperado de inicio/readiness ni se presentó una ejecución aislada. |

## Condición para reconsiderar

Reconsiderar la aprobación cuando se disponga de: cambios verificables en los adaptadores reales; contrato HTTP de login confirmado; pruebas ejecutadas de rechazo, fallo de persistencia, sanitización y regresión; y resultados que demuestren que el error de servicio no se presenta como credenciales inválidas. La aprobación técnica no reemplaza las aprobaciones humanas que correspondan.

VEREDICTO: RECHAZADO