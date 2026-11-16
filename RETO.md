# Reto U11 — Clave de idempotencia para crear alumnos

Rama: `reto-u11-idempotencia`. `master` (tag `u11`) conserva la solución guiada, que es la base de U12.

## 1. Diseño

### Problema

Un cliente envía `POST /api/v1/alumnos`, la conexión se corta y no recibe respuesta. No sabe si el alumno se creó
(sección 11.4: la falta de respuesta no significa que no hubo efecto). Si reintenta sin protección, puede crear el
alumno o recibir un 409 por matrícula duplicada, que no le dice si **su** solicitud original tuvo éxito.

### Contrato

- El cliente envía la cabecera `Idempotency-Key: <texto único por intento lógico>` (1 a 100 caracteres).
- **Primera vez**: se crea el alumno → `201`, `Location` y el cuerpo, igual que sin clave.
- **Repetición con la misma clave y el mismo contenido**: no se crea nada. Se responde **lo mismo que la primera vez**
  (`201`, mismo `Location`, mismo cuerpo) más la cabecera `Idempotency-Replayed: true`.
- **Misma clave con contenido distinto**: `422 CLAVE_IDEMPOTENCIA_REUTILIZADA`. La clave ya está ligada a otra
  solicitud; aceptar el nuevo contenido o devolver la respuesta vieja sería engañoso.
- **Sin cabecera**: el comportamiento es exactamente el de U06, así que el contrato existente no cambia.

### Tabla (migración `V3__solicitudes_idempotentes.sql`)

| Columna | Uso |
|---|---|
| `usuario` | Principal autenticado. **Las claves son por usuario**: dos clientes pueden usar la misma clave sin chocar |
| `clave` | Valor de `Idempotency-Key` |
| `huella` | SHA-256 (hex) del contenido **canónico** |
| `creada` | Fecha (con el `Clock` inyectado) |
| `alumno_id`, `matricula`, `nombre`, `correo`, `estatus` | **Respuesta original** guardada, para repetirla aunque el alumno cambie después |
| `UNIQUE (usuario, clave)` = `uk_solicitud_idempotente` | **El punto de exclusión** |

### Huella del contenido

Se calcula sobre la forma canónica que guardaría el dominio: matrícula con `strip()` y mayúsculas (`Locale.ROOT`),
nombre y correo con `strip()`. Así, `" a001 "` y `"A001"` son el **mismo** contenido (el dominio los guarda igual),
pero un nombre distinto es contenido distinto.

### Transacción

Todo el efecto ocurre en **una sola transacción**:

1. `INSERT` en `solicitudes_idempotentes` (usuario, clave, huella): la **reserva** de la clave.
2. `AlumnoService.crear(...)`, que se une a la misma transacción.
3. Se guarda la respuesta en la misma fila de la reserva.
4. Commit: la reserva y el alumno se confirman juntos. Si la creación falla (por ejemplo, 409 por matrícula), se
   revierten juntos y la clave queda libre. No hay reservas huérfanas ni alumnos sin reserva.

### Concurrencia: por qué funciona sin memoria compartida

La exclusión la da **PostgreSQL**, no la JVM. Si dos solicitudes con la misma `(usuario, clave)` llegan a la vez:

- La primera inserta la reserva y sigue.
- El `INSERT` de la segunda choca con el índice único de una fila **aún no confirmada**. PostgreSQL la hace **esperar**
  hasta que la primera termina.
  - Si la primera hace **commit**, el `INSERT` falla con `unique_violation`. La segunda lee la fila ya confirmada y
    compara la huella: si es igual, repite la respuesta; si es distinta, responde 422.
  - Si la primera hace **rollback**, el `INSERT` de la segunda procede y ejecuta la creación normalmente.

Funciona igual con varias instancias de la aplicación y después de reiniciar, porque el estado vive en la base. Un
`Map` en un bean singleton no daría ninguna de las dos garantías y perdería las claves al reiniciar.

## 2. Pruebas (escritas antes de la implementación)

`service/IdempotenciaTest`: PostgreSQL real con Testcontainers y el **bean real** del servicio.

| # | Caso | Esperado |
|---|---|---|
| 1 | **Dos solicitudes simultáneas**, misma clave y mismo contenido (dos hilos, `CountDownLatch`, ×5) | Ambas devuelven **el mismo id**; exactamente una es `repetida`; **1** alumno y **1** fila de idempotencia |
| 2 | **Misma clave, distinto contenido** (secuencial) | La segunda lanza `CLAVE_IDEMPOTENCIA_REUTILIZADA`; sigue habiendo 1 alumno |
| 3 | Misma clave y contenido distinto **simultáneos** (×5) | Una crea y la otra recibe 422; 1 alumno |
| 4 | Misma clave en **usuarios distintos** | Son independientes: la segunda no se trata como repetición |
| 5 | Repetición después de modificar el alumno con PUT | Se repite la **respuesta original** guardada |

## 3. Implementación

| Archivo | Papel |
|---|---|
| `db/migration/V3__solicitudes_idempotentes.sql` | Tabla, `UNIQUE (usuario, clave)` y columnas de la respuesta original |
| `repository/jpa/SolicitudIdempotenteEntity` / `...Repository` | Mapeo y `findByUsuarioAndClave` |
| `service/IdempotenciaService` | `TransactionTemplate` con reserva, creación y respuesta. Ante `unique_violation` de `uk_solicitud_idempotente`: compara la huella y repite la respuesta o lanza `IdempotenciaConflictoException` |
| `controller/AlumnoIdempotenteController` | `@PostMapping(headers = "Idempotency-Key")`: MVC lo elige solo si llega la cabecera. Sin ella responde el `AlumnoController` original |
| `exception/GlobalExceptionHandler` | `IdempotenciaConflictoException` → **422** `CLAVE_IDEMPOTENCIA_REUTILIZADA` |

No hay ningún `Map` ni campo mutable con claves: el único estado es la tabla.

## 4. Evidencia

- `IdempotenciaTest`: **13/13** contra PostgreSQL real (5 repeticiones de cada caso simultáneo). Suite completa del
  reto: **28/28** (`evidencia/u11/reto-pruebas-resumen.txt`).
- **Control** (`evidencia/u11/reto-control-sin-unique.txt`): al quitar temporalmente `UNIQUE (usuario, clave)` fallan
  **12 de 13**. Sin el punto de exclusión, las dos solicitudes simultáneas intentan crear el alumno y la segunda termina en
  `MatriculaDuplicadaException` (409): el cliente vuelve a no saber si su solicitud tuvo efecto. La migración se restauró.
- **Aplicación real** (`evidencia/u11/reto-idempotencia-http.txt`), sobre una **copia** de la base del laboratorio
  (`academico_u11`), para que la base principal siga en V2 y U12 arranque normalmente:

| Solicitud | Resultado |
|---|---|
| `Idempotency-Key: alta-ada-1`, primera vez | 201, `Location: /api/v1/alumnos/5` |
| Misma clave y contenido (reintento) | 201, mismo `Location` y cuerpo, `Idempotency-Replayed: true` |
| Misma clave, otro contenido | 422 `CLAVE_IDEMPOTENCIA_REUTILIZADA` |
| Sin cabecera, misma matrícula | 409 `MATRICULA_DUPLICADA` (contrato de U06 intacto) |
| Dos solicitudes **simultáneas** con `alta-grace-1` | Ambas 201 con id 6; una con `Idempotency-Replayed: true`; 1 alumno y 1 fila de idempotencia |

## 5. Límites y siguientes pasos

- Las filas de idempotencia no caducan. En producción se depuran después de una ventana (por ejemplo, 24 h) con una
  tarea programada, que es un tema de U12.
- Solo se guardan respuestas **exitosas**: si la creación falla, la transacción revierte la reserva y un reintento
  vuelve a ejecutar la operación. Es correcto aquí porque los fallos (400, 409) son deterministas para el mismo contenido.
- `openapi.yaml` no se modificó en el reto. Documentar la cabecera opcional y el 422 sería parte de publicar este
  contrato.
