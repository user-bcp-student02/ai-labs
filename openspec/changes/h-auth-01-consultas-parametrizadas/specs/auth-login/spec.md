## ADDED Requirements

### Requirement: Verificación de credenciales con consultas parametrizadas
El sistema SHALL verificar `username` y `password` en `POST /auth/login` mediante consultas parametrizadas. Ningún valor enviado por el cliente SHALL concatenarse en el texto SQL.

#### Scenario: Credenciales válidas se verifican sin alterar la consulta
- **WHEN** un cliente envía `username` `student` y `password` `Workshop123!`
- **THEN** el sistema verifica las credenciales usando los valores como parámetros y responde 200

#### Scenario: Caracteres especiales se tratan como datos
- **WHEN** un cliente envía un `username` o `password` que contiene comillas simples o SQL, como `' OR '1'='1`
- **THEN** el sistema trata el valor como texto literal y no ejecuta ninguna parte de él como SQL

### Requirement: Rechazo de intentos de inyección en el login
El sistema SHALL rechazar con 401 cualquier intento de login cuyas credenciales no coincidan con un usuario registrado, incluidos los intentos de inyección SQL, y SHALL NOT devolver `token` en esas respuestas.

#### Scenario: Inyección en el campo username
- **WHEN** un cliente envía `username` `' OR '1'='1` y `password` `cualquiera`
- **THEN** el sistema responde 401 y el cuerpo de la respuesta no contiene `token`

#### Scenario: Inyección en el campo password
- **WHEN** un cliente envía `username` `student` y `password` `' OR '1'='1`
- **THEN** el sistema responde 401 y el cuerpo de la respuesta no contiene `token`

#### Scenario: Contraseña incorrecta para un usuario existente
- **WHEN** un cliente envía `username` `student` y una `password` distinta de `Workshop123!`
- **THEN** el sistema responde 401 y el cuerpo de la respuesta no contiene `token`

#### Scenario: Fallo de la consulta de credenciales
- **WHEN** la consulta de credenciales falla por un error de base de datos
- **THEN** el sistema responde 401 y el cuerpo de la respuesta no contiene `token`

### Requirement: Contrato de respuesta exitosa del login
El sistema SHALL responder 200 ante credenciales válidas con un cuerpo que contenga `token`, `expiresIn` con valor 3600, y `user` con `username`, `displayName` y `role`, además del campo `debug`, sin cambios respecto del comportamiento actual.

#### Scenario: Login exitoso del usuario student
- **WHEN** un cliente envía `username` `student` y `password` `Workshop123!`
- **THEN** el sistema responde 200 con `user.username` igual a `student`, `token` no vacío, `expiresIn` igual a 3600 y `user.role` igual a `STUDENT`

#### Scenario: displayName se devuelve escapado en HTML
- **WHEN** el login es exitoso
- **THEN** el valor de `user.displayName` está escapado en HTML

### Requirement: Validación de entrada del login
El sistema SHALL responder 400 cuando el cuerpo de la solicitud es nulo, o cuando `username` o `password` están vacíos o contienen solo espacios, y SHALL NOT consultar credenciales en esos casos.

#### Scenario: Username vacío
- **WHEN** un cliente envía `username` vacío o solo con espacios
- **THEN** el sistema responde 400 con el mensaje `Username is required`

#### Scenario: Password vacío
- **WHEN** un cliente envía `password` vacío o solo con espacios
- **THEN** el sistema responde 400 con el mensaje `Password is required`

#### Scenario: Cuerpo nulo
- **WHEN** un cliente envía una solicitud sin cuerpo
- **THEN** el sistema responde 400 con el mensaje `Request is null`

### Requirement: Error interno del login
El sistema SHALL responder 500 con `message` y `exception` cuando ocurra un error no controlado durante el login fuera de la consulta de credenciales, sin cambiar el formato de esa respuesta. Los fallos de la consulta de credenciales se tratan como no autenticado (ver *Rechazo de intentos de inyección en el login*).

#### Scenario: Fallo inesperado fuera de la verificación
- **WHEN** ocurre una excepción no controlada al construir la respuesta del login exitoso
- **THEN** el sistema responde 500 con `message` y `exception` y no devuelve `token`
