# user_story.md

> **Aviso de alcance:** el brief recibido es **H-AUTH-04**, sobre distinguir errores de base de datos de credenciales inválidas en `POST /auth/login`. La solicitud de este documento pide, en cambio, una transferencia entre cuentas propias. Son funcionalidades distintas. Para atender la solicitud, se documenta abajo una transferencia como **propuesta pendiente de aprobación**; no se afirma que forme parte de H-AUTH-04. Los criterios originales de login se conservan al final de los criterios y no deben interpretarse como criterios de transferencia.

## Contrato de API — propuesta pendiente de aprobación

**Operación propuesta:** `POST /transfers`. No consta una ruta de transferencias en el brief; Backend y Chapter deben aprobar la ruta y versión antes de implementarla.

**Cabeceras propuestas:**

- `Authorization: Bearer <token>`: identidad autenticada; mecanismo y claims por confirmar con Security.
- `Content-Type: application/json`.
- `Idempotency-Key: <valor opaco>`: obligatorio para evitar duplicados ante reintentos. Formato, alcance y periodo de retención pendientes.
- `X-Request-Id`: opcional de entrada; si falta, la API genera uno. Debe estar presente en la respuesta para correlación. Propuesta pendiente.

**Solicitud propuesta:** los montos se envían como string decimal y la moneda como código ISO 4217; nunca como número JSON ni `float`. Los identificadores son sintéticos en este ejemplo.

```http
POST /transfers
Authorization: Bearer <token>
Content-Type: application/json
Idempotency-Key: demo-key-001

{
  "sourceAccountId": "acct-demo-001",
  "destinationAccountId": "acct-demo-002",
  "amount": "25.40",
  "currency": "USD"
}
```

La gramática decimal, escala permitida, monto mínimo/máximo, monedas admitidas y formato de los identificadores quedan pendientes de definición.

**Respuesta exitosa propuesta — `201 Created`:**

```json
{
  "transferId": "tr-demo-001",
  "status": "COMPLETED",
  "sourceAccountId": "acct-demo-001",
  "destinationAccountId": "acct-demo-002",
  "amount": "25.40",
  "currency": "USD",
  "createdAt": "2026-01-15T12:00:00Z",
  "requestId": "req-demo-001"
}
```

Los valores son ilustrativos, no representan clientes, cuentas ni una transferencia real. El comportamiento HTTP y del cuerpo ante un reintento idempotente debe aprobarse antes de fijar el contrato.

**Errores propuestos:** cuerpo uniforme; no incluir excepciones, SQL, nombres de tablas, credenciales, saldos ni detalles internos.

```json
{
  "status": 503,
  "code": "SERVICE_UNAVAILABLE",
  "message": "No se pudo procesar la solicitud en este momento.",
  "requestId": "req-demo-002"
}
```

Ejemplo: indisponibilidad de persistencia, `503 Service Unavailable`. La respuesta no debe convertir ese fallo en rechazo de autenticación ni revelar su causa técnica. La redacción es propuesta.

| HTTP propuesto | Código estable | Significado |
|---|---|---|
| `409` | `INSUFFICIENT_FUNDS` | Fondos insuficientes para completar el débito. |
| `409` | `SAME_ACCOUNT` | Origen y destino son la misma cuenta. |
| `403` | `ACCOUNT_NOT_OWNED` | Al menos una cuenta no pertenece al cliente autenticado. La exposición de este código requiere revisión de Security. |
| `409` | `ACCOUNT_INACTIVE` | Al menos una cuenta no está activa. |
| `422` | `CURRENCY_NOT_SUPPORTED` | Moneda no admitida para la operación. |
| `409` | `LIMIT_EXCEEDED` | Se excede un límite aplicable. |
| `409` | `DUPLICATE_REQUEST` | Solicitud de negocio duplicada, si se define esa regla. |
| `400` | `INVALID_REQUEST` | Cuerpo ausente, inválido o con formato no aceptado. |
| `401` | `UNAUTHENTICATED` | Falta autenticación válida. |
| `503` | `SERVICE_UNAVAILABLE` | Dependencia necesaria no disponible, incluida persistencia. |
| `500` | `INTERNAL_ERROR` | Error inesperado; mensaje genérico, sin detalles internos. |

Los códigos y estados de esta tabla son una **propuesta**, no un contrato existente. Deben acordarse con Backend, Frontend y Security. `Idempotency-Key` repetida con el mismo contenido debería devolver el resultado original, sin ejecutar otra transferencia; la misma clave con contenido diferente debería rechazarse. El estado, código y cabeceras exactos de esos casos —incluida la posible distinción frente a `DUPLICATE_REQUEST`— están pendientes de aprobación.

## Criterios de aceptación

Los criterios de transferencia siguientes son **propuestas** derivadas de la solicitud de este documento; no constan en el brief H-AUTH-04.

1. **[Propuesta] Transferencia autorizada.**  
   **Dado** un cliente autenticado y dos cuentas activas que le pertenecen, con origen distinto de destino, moneda y monto admitidos, saldo suficiente y límites no excedidos,  
   **cuando** envía una transferencia válida con `Idempotency-Key`,  
   **entonces** la API completa débito y crédito de forma atómica y devuelve la respuesta de éxito acordada, con el mismo monto y moneda solicitados.

2. **[Propuesta] No transferir a la misma cuenta.**  
   **Dado** una solicitud cuyo origen y destino son la misma cuenta,  
   **cuando** la API la valida,  
   **entonces** la rechaza con `SAME_ACCOUNT` y no modifica saldos.

3. **[Propuesta] Propiedad y estado de las cuentas.**  
   **Dado** que una cuenta no pertenece al cliente autenticado, o que alguna cuenta está inactiva,  
   **cuando** se solicita la transferencia,  
   **entonces** se rechaza con el código estable acordado para esa condición y no se modifica ninguna cuenta.

4. **[Propuesta] Saldo, moneda y límites.**  
   **Dado** un monto que excede el saldo disponible, una moneda no admitida o un límite aplicable excedido,  
   **cuando** se solicita la transferencia,  
   **entonces** se devuelve respectivamente `INSUFFICIENT_FUNDS`, `CURRENCY_NOT_SUPPORTED` o `LIMIT_EXCEEDED`, según el contrato aprobado, y no se produce una transferencia parcial.

5. **[Propuesta] Idempotencia y duplicados.**  
   **Dado** que ya se procesó una solicitud con una `Idempotency-Key`,  
   **cuando** se reintenta con la misma clave y el mismo contenido,  
   **entonces** no se ejecuta un segundo débito ni crédito y se devuelve el resultado definido por el contrato aprobado.  
   **Y dado** que se reutiliza esa clave con contenido distinto,  
   **cuando** se recibe la nueva solicitud,  
   **entonces** se rechaza según el código estable que se acuerde y no se altera la primera operación.

6. **[Propuesta] Fallo de persistencia.**  
   **Dado** un fallo simulado de persistencia durante la operación,  
   **cuando** se solicita la transferencia,  
   **entonces** el fallo no se representa como una regla de negocio ni como un rechazo de autenticación, se devuelve el error de servicio acordado sin detalles internos y no queda débito sin crédito ni crédito sin débito.

7. **Criterio original de H-AUTH-04 — conservar sin cambios.**  
   **Dado** que se llama a `POST /auth/login` con credenciales incorrectas,  
   **cuando** se autentica la solicitud,  
   **entonces** las credenciales incorrectas siguen produciendo la respuesta de rechazo de autenticación definida por `POST /auth/login`.

8. **Criterio original de H-AUTH-04 — conservar sin cambios.**  
   **Dado** un fallo de base de datos durante el login,  
   **cuando** ocurre la consulta,  
   **entonces** un fallo de base de datos se propaga y se representa como error de servicio, no como credenciales inválidas.

9. **Criterio original de H-AUTH-04 — conservar sin cambios.**  
   **Dado** un error de servicio en el login,  
   **cuando** la API construye la respuesta,  
   **entonces** la respuesta del error de servicio no expone detalles internos ni credenciales.

10. **Criterio original de H-AUTH-04 — conservar sin cambios.**  
    **Dado** pruebas de credenciales incorrectas y de fallo simulado de persistencia,  
    **cuando** se ejecutan las pruebas del login,  
    **entonces** las pruebas cubren tanto credenciales incorrectas como fallo simulado de persistencia y verifican que el contrato funcional del login se conserva.

## Historia de usuario

**[Propuesta, alcance por confirmar]**  
Como cliente autenticado, quiero transferir un monto entre dos cuentas propias, para administrar mis fondos desde la aplicación sin generar movimientos parciales ni duplicados.

## Recorrido de la petición

1. **React:** solicita confirmación al usuario, representa el monto como texto decimal y envía la solicitud con autenticación e `Idempotency-Key`. No determina por sí mismo la titularidad, el saldo ni la autorización final.
2. **API / adaptador de entrada:** valida estructura y autenticación, convierte la solicitud a una entrada del caso de uso y asigna o propaga un identificador de correlación. No transforma errores de persistencia en errores de negocio.
3. **Caso de uso:** coordina la validación y ejecución de la transferencia mediante puertos; aplica las reglas aprobadas y devuelve un resultado de aplicación.
4. **Puertos de salida:** abstraen la consulta de cuentas y la operación transaccional. El dominio y la aplicación no dependen de tecnologías de persistencia.
5. **Adaptadores de salida / persistencia:** cargan y actualizan los datos mediante una transacción que garantice débito y crédito atómicos. Los errores técnicos se propagan a la aplicación para su traducción segura a error de servicio.

El flujo exacto de autenticación, autorización, límites y transacción queda sujeto a las decisiones pendientes.

## Reglas de negocio

La solicitud pide contemplar estas reglas; su implementación y detalles son **pendientes de aprobación**, salvo que se confirmen como requisitos del producto:

- Ambas cuentas deben pertenecer al cliente autenticado.
- La cuenta de origen debe ser distinta de la cuenta de destino.
- Ambas cuentas deben estar activas.
- El saldo debe ser suficiente para el débito. Qué saldo se considera disponible —incluidos sobregiros, retenciones y comisiones— está pendiente.
- La moneda debe ser admitida. Conversión de moneda, si aplica, está pendiente; no se debe inferir.
- Deben aplicarse límites, pero sus valores, ventanas de acumulación, ámbito y comportamiento están pendientes.
- Débito y crédito deben ser atómicos: ambos se confirman o ninguno. La tecnología o estrategia concreta es decisión de implementación compatible con esa garantía.
- Positividad del monto, escala decimal, comisiones, fechas de disponibilidad, reversos y prevención de duplicados de negocio no están definidos; no deben asumirse como requisitos confirmados.

## Componentes y límites — arquitectura hexagonal

- **`domain/model/`:** conceptos y reglas puras de transferencia, cuentas y monto; no importa infraestructura ni framework.
- **`domain/port/input/`:** interfaz del caso de uso invocada por el adaptador de entrada.
- **`domain/port/output/`:** contratos para consultar titularidad/estado/saldo y para persistir la operación atómicamente.
- **`application/service/`:** coordina el caso de uso y depende solo de puertos y dominio; define cómo los resultados y fallos se comunican hacia el adaptador.
- **`infrastructure/adapter/input/`:** endpoint REST, autenticación integrada y mapeo de petición/respuesta. No contiene reglas financieras.
- **`infrastructure/adapter/output/`:** implementación de persistencia y demás integraciones; encapsula transacciones y traduce errores técnicos a fallos del puerto sin ocultarlos.
- **`infrastructure/config/`:** configuración y ensamblaje de adaptadores y servicios.

Las dependencias apuntan hacia adentro: infraestructura → aplicación → dominio.

**Responsabilidades:** Backend implementa el caso de uso, puertos y adaptadores en Python y garantiza consistencia. Frontend implementa la interacción y presenta respuestas del contrato, sin autorizar ni calcular la decisión financiera final. Testing verifica contrato, reglas, concurrencia, idempotencia y fallos. Security revisa autenticación, autorización, enumeración de cuentas y exposición de datos. Chapter acuerda convenciones y revisa límites arquitectónicos.

**Datos:** no se indica si el rol/equipo Datos está activo. Si lo está, le corresponden el diseño y las garantías de persistencia que se le asignen —incluida la atomicidad— coordinado con Backend. Si no está activo, Backend cubre la persistencia necesaria y su garantía transaccional.

## Requisitos no funcionales verificables — propuestas

- **Consistencia:** una prueba de integración verifica que tras éxito se aplican débito y crédito, y que ante fallo no queda solo uno aplicado.
- **Idempotencia:** pruebas secuenciales y concurrentes con la misma clave verifican que no se genera más de una transferencia efectiva; pruebas con contenido distinto verifican el rechazo acordado.
- **Concurrencia:** pruebas con solicitudes simultáneas sobre saldo o límites compartidos verifican que no se permite exceder el saldo/límite por carreras. El mecanismo de concurrencia queda pendiente.
- **Auditoría:** verificar que una operación y su resultado pueden auditarse con identificador de transferencia y actor conforme a la política aprobada; no registrar credenciales ni secretos. Retención y campos obligatorios pendientes.
- **Trazabilidad:** pruebas verifican un identificador de solicitud en respuestas y registros correlacionados. Propagación entre servicios, si aplica, pendiente.
- **Observabilidad:** pruebas o revisión de configuración verifican que se distinguen errores de negocio y errores técnicos en métricas/registros, y que estos últimos no se contabilizan como credenciales inválidas. Nombres de métricas, alertas y umbrales pendientes.
- **Seguridad:** pruebas verifican que un usuario no puede transferir desde o hacia cuentas ajenas, que respuestas no contienen datos internos ni credenciales y que montos no se aceptan como `float`. Política de autenticación, autorización y tratamiento anti-enumeración requiere aprobación de Security.

## Supuestos, preguntas abiertas y riesgos

- **Backend / Product:** confirmar si el alcance real es H-AUTH-04 (login) o transferencias; no mezclar ambos contratos en una misma entrega funcional.
- **Product / Backend:** aprobar ruta, versionado, campos, precisión decimal, monedas permitidas, respuestas de reintento y semántica de duplicado.
- **Product / Riesgo / Security:** definir límites, disponibilidad de fondos, comisiones, overdraft, políticas de reverso y tratamiento de errores de titularidad.
- **Security:** decidir autenticación, autorización, idempotency key, controles anti-enumeración, auditoría y datos que pueden exponerse.
- **Backend / Datos, si está activo:** acordar cómo se garantiza atomicidad, aislamiento y concurrencia y quién opera el almacenamiento.
- **Testing / Chapter:** acordar estrategia de pruebas de contrato e integración y revisar consistencia con los estándares del capítulo.

**Riesgos:** la incompatibilidad entre el brief de login y el objetivo de transferencia puede llevar a implementar o probar el alcance equivocado; límites o reglas no acordadas producirían resultados inconsistentes entre clientes; una garantía de idempotencia o atomicidad incompleta podría causar movimientos duplicados o parciales. La implementación de transferencias debe quedar bloqueada hasta resolver el alcance y aprobar el contrato propuesto.