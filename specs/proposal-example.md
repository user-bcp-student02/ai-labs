**📄 Archivo: `proposal.md`**

**Proposal**

- **Por qué:** hoy el cliente puede mover cualquier monto entre sus cuentas en un solo día. Riesgo y cumplimiento piden un tope diario como control ante accesos indebidos.
- **Qué cambia:** se agrega un límite diario configurable por segmento de cliente a las transferencias entre cuentas propias.
- **Alcance:** solo transferencias entre cuentas propias del mismo cliente. Quedan fuera las transferencias a terceros y las interbancarias.
- **Cómo se sabrá que está hecho:** los criterios de las specs pasan en las pruebas y no cambia el contrato de la API existente.

**📄 Archivo: `specs/transferencias/spec.md`**

**Specs (criterios verificables)**

1. Si la suma de transferencias del día más la nueva supera el límite del segmento, el sistema rechaza la operación.
2. El rechazo responde con el código `LIMITE_DIARIO_EXCEDIDO` y el mensaje "Superaste tu límite diario de transferencias".
3. Si la suma queda exactamente en el límite, la operación se acepta.
4. El acumulado del día se reinicia a las 00:00 en la zona horaria del cliente.
5. Cada rechazo queda registrado con el identificador del cliente, el monto y la hora, sin guardar datos de la cuenta destino.

**📄 Archivo: `design.md`**

**Decisiones de diseño**

- No se modifica el contrato de la API existente de transferencias.
- Ningún dato sensible aparece en logs ni en mensajes de error.
- El límite se lee de configuración, no se escribe en el código.

**📄 Archivo: `tasks.md`**

**Lista de tareas** (pasos a ejecutar, para ir marcando):

- [ ] Crear la validación del límite diario en el servicio de transferencias.
- [ ] Leer el límite desde la configuración, no desde el código.
- [ ] Rechazar la transferencia que supera el límite, con código y mensaje claros.
- [ ] Escribir las pruebas automáticas de los criterios 1 a 4.
- [ ] Registrar cada rechazo (criterio 5).