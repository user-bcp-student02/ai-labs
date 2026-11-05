**Proposal (ya escrito, no se modifica)**

- **Por qué:** hoy el cliente que pierde su tarjeta debe llamar al centro de atención. Un bloqueo inmediato desde la app reduce el riesgo de fraude mientras decide si la reporta como perdida.
- **Qué cambia:** el cliente puede bloquear y desbloquear su tarjeta de forma temporal desde la app.
- **Alcance:** tarjetas de débito y crédito del propio cliente. Quedan fuera el bloqueo definitivo, la reposición y el reporte de robo.
- **Cómo se sabrá que está hecho:** los criterios de las specs pasan en las pruebas.

**📄 Archivo: `specs/tarjetas/spec.md`**

**Specs (completar al menos 3 criterios verificables)**

1. Si el cliente solicita el bloqueo o desbloqueo de su tarjeta, previamente, debe de solicitarse el ingreso de su 2FA (biometría, otp, token físico) y proceder con la validación correspondiente para continuar o rechazar la solicitud.
2. En caso, el 2FA sea correcta, se procede con el bloqueo de la tarjeta que pasa a un estado bloqueo temporal o desbloqueo.
3. Mientras la tarjeta está bloqueada, todas las operaciones realizadas sobre esta tarjeta deberán ser rechazadas.
4. Cuando se realiza el desbloqueo, las operaciones realizadas con la tarjeta vuelven a ser procesadas.
5. Toda operación realizada deberá ser registrada en un log auditoría independiente, indicando la tipificación "RBT (Rechazo por bloqueo temporal) / ATB (Activación de tarjeta bloqueada)".

**📄 Archivo: `design.md`**

**Decisiones de diseño (completar al menos 2)**

- No se modifican funcionalidades existentes de la aplicación.
- La aprobación del bloqueo temporal debe ser realizada mediante el 2FA configurado por el cliente
- La opción de bloqueo o desbloqueo de tarjeta debe de encontrarse dentro de la configuración de la tarjeta.
- Ningún dato sensible aparece en logs ni en mensajes de error.

**📄 Archivo: `tasks.md`**

**Lista de tareas** (pasos a ejecutar, para ir marcando):
- [ ] Crear la opción de solicitar bloqueo temporal en el apartado "Configuración" de la UI.
- [ ] Crear la opción de desbloqueo en el apartado "Configuración" de la UI.
- [ ] Determinar modelo de datos y eventsourcing para manejo de estados en capas intermedias (backend)
- [ ] Los estados de bloqueo y desbloqueo de la tarjeta  debe de persistirse en core de tarjetas.
- [ ] Agregar la validación de rechazo de operación mientras bloqueo RBT esté activo.
- [ ] Implementar capacidad de bloqueos y desbloqueos.
- [ ] Registrar logs de auditoria de operaciones: bloqueos, desbloqueos, rechazos
- [ ] Se debe de implementar 2FA para realizar bloqueo y desbloqueo de las tarjetas. (criterio 1)
- [ ] Escribir las pruebas automáticas para las funcionalidades definidas.