AMBITO
Workspace ai-labs abierto en su raiz. Lee codigo solo en api/.
Todas las rutas parten de la raiz. Lee README.md, RETO.md y
docs/ generales. Este pedido es de solo lectura; no escribas archivos.
Git se ejecuta desde la raiz; las pruebas propuestas usan cwd api/.
No leas ni modifiques codigo de auth/ ni web/. Usa IDs H-API-NN y componente api.

CONTEXTO
ai-labs expone una API FastAPI (Python) con GET/POST /api/tasks,
GET /api/summary y POST /api/requirements, protegida por un token Bearer
legado. Lee README.md, RETO.md y docs/ENDPOINTS.md si existe. Localiza la
ruta desde el endpoint HTTP hasta la consulta a SQLite (api/app/main.py y
api/tests/test_api.py). No des por cierta la lista de RETO.md.

OBJETIVO
Determinar si entradas de la API (query owner, cuerpo de POST /api/tasks y
cabecera Authorization) llegan concatenadas a una consulta SQL, y si el
entorno Python permite ejecutar la API y sus pruebas.

RESTRICCIONES
No edites codigo ni ejecutes ataques contra servicios remotos. Usa solo
codigo y datos ficticios locales (SQLite temporal, token de prueba con
secreto de prueba). Si falta un archivo o no puedes ejecutar una
comprobacion, dilo. No cambies contratos ni leas otros componentes.
No ejecutes comandos en este pedido: solo propongalos.

CRITERIOS DE ACEPTACION
Identifica el recorrido de cada entrada hasta la consulta con archivo y linea.
Distingue concatenacion, parametrizacion y cualquier caso no comprobado.
Valida que el contrato observado se conserva: token valido -> 200/201,
token invalido o ausente -> 401, formato de respuesta de las tareas.
Propone una prueba local (pytest con TestClient y base en tmp_path) con
token valido, token invalido y valor de owner/title con comilla, con
resultado esperado segun el contrato observado.
Propone la validacion de ejecucion con cwd api/: version de Python,
instalacion de requirements.txt y requirements-dev.txt en .venv, pytest y
arranque de uvicorn app.main:app en localhost:8000, indicando salida esperada.
No afirmes que algo paso si solo lo propusiste.

FORMATO DE RESPUESTA
Tabla: afirmacion | evidencia archivo:linea | comprobado o hipotesis |
prueba propuesta. Termina con preguntas abiertas y comandos no ejecutados.
