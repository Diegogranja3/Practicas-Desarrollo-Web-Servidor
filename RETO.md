# Reto U08 — Dos admisiones simultáneas a una materia con un solo lugar

Rama: `reto-u08-cupo-concurrente`. `master` (tag `u08`) conserva la solución guiada, que es la base de U09.

## Prueba

`src/test/java/mx/edu/backendacademico/service/InscripcionConcurrenteTest.java`

Corre contra **PostgreSQL real**: Testcontainers levanta un `postgres:17-alpine` temporal (con la misma versión
2.0.5 que fija U09), Flyway le aplica V1 y V2, y al terminar se descarta. No toca la base del laboratorio. Para
ejecutarla, Docker debe estar iniciado.

`InscripcionService` llega como **bean real** (`@Autowired`), así que cada `inscribir` atraviesa el proxy transaccional
igual que una petición HTTP.

### 1. `dosAdmisionesSimultaneasDejanUnaInscripcionYUnRechazoPorCupo` (×10)

1. Prepara una materia con `cupo = 1` y dos alumnos activos distintos.
2. Dos tareas en un pool de dos hilos esperan el mismo `CountDownLatch` y salen al mismo tiempo, cada una con su alumno.
3. Espera con `Future.get(timeout)`, sin `sleep`.
4. Verifica: exactamente un `INSCRITO`, exactamente un `SIN_CUPO` y **una sola fila** en `inscripciones`.

### 2. `laSegundaAdmisionEsperaElBloqueoDentroDePostgreSQL`

Muestra **dónde** espera el segundo hilo:

1. Otra transacción (`TransactionTemplate`) ejecuta `materias.bloquearPorId(...)`, es decir, el mismo
   `SELECT ... FOR UPDATE` que usa `inscribir`, y retiene el bloqueo.
2. Se lanza `servicio.inscribir(...)` en otro hilo.
3. Se consulta `pg_stat_activity` y aparece la sesión de la admisión con `wait_event_type = Lock`.
4. Se inspecciona el hilo Java: está `RUNNABLE` dentro del driver `org.postgresql`, esperando la respuesta de red. **No**
   está `BLOCKED` en un monitor.
5. Al confirmar la primera transacción, la admisión continúa y termina.

## Evidencia

`evidencia/u08/reto-pruebas-resumen.txt`:

```
[RETO-U08] pg_stat_activity de la admisión en espera: {pid=62, wait_event_type=Lock, wait_event=transactionid, state=active, query=select me1_0.id,me1_0.clave,... fro}
[RETO-U08] Estado del hilo de la admisión: RUNNABLE | en el driver org.postgresql: true | marco superior: java.base/sun.nio.ch.Net.poll(Native Method)
Tests run: 11, Failures: 0  -- InscripcionConcurrenteTest
Tests run: 15, Failures: 0  -- suite completa
```

Control (`evidencia/u08/reto-control-sin-lock.txt`): al quitar temporalmente `@Lock(PESSIMISTIC_WRITE)` de
`MateriaRepository`, **fallan las 11**. En las 10 repeticiones, las dos admisiones devuelven `[INSCRITO, INSCRITO]`, así
que quedan 2 inscripciones con cupo 1, y ninguna sesión espera en PostgreSQL. El archivo se restauró: el commit no toca
el código de producción.

## ¿Dónde esperan los hilos?

`inscribir` corre con `@Transactional(READ_COMMITTED)` y su **primer** paso es `bloquearPorId`, que Hibernate traduce
a un `SELECT ... FROM materias WHERE id = ? FOR NO KEY UPDATE` (un bloqueo de fila de escritura).

- El primer hilo obtiene el bloqueo de la fila de la materia y sigue: valida alumno, duplicado y cupo (0 < 1), inserta
  y hace commit. **El bloqueo dura hasta el commit**, no hasta que regresa `bloquearPorId`.
- El segundo hilo envía el mismo `SELECT ... FOR UPDATE`. **PostgreSQL no le responde** hasta que el primero termina:
  la espera ocurre en el servidor de base de datos (`wait_event = transactionid`, la transacción que tiene la fila). En
  Java, el hilo está dentro de la llamada JDBC, leyendo el socket.
- Al liberarse la fila, la consulta del segundo devuelve la materia y, con READ COMMITTED, su `countByMateriaId`
  posterior ya ve la inscripción confirmada: 1 ≥ cupo 1, así que lanza `SIN_CUPO` y hace rollback.

## ¿Qué distingue la exclusión en PostgreSQL de un bloqueo local de Java?

| | Exclusión en PostgreSQL (esta solución) | Bloqueo local de Java (`synchronized`, U04) |
|---|---|---|
| Dónde espera | En el servidor de base de datos | En el monitor de la JVM |
| `pg_stat_activity` | La sesión aparece con `wait_event_type = Lock` | Nada: la consulta ni siquiera se envía |
| Estado del hilo Java | `RUNNABLE`, dentro de `org.postgresql` (socket) | `BLOCKED` en el monitor |
| Alcance | Todas las JVM, réplicas o clientes SQL que usen el mismo protocolo | Solo los hilos de esa instancia de la JVM |
| Duración | Hasta commit o rollback | Hasta salir del bloque `synchronized` |

La prueba 2 verifica justo esas dos señales: la sesión bloqueada en `pg_stat_activity` y el hilo que **no** está
`BLOCKED`. Por eso, si la aplicación corriera en dos instancias, el cupo seguiría protegido, cosa que el repositorio en
memoria de U04 no puede garantizar.

## Límites

- 10 repeticiones con dos hilos no prueban todos los entrelazados posibles. La prueba 2 fuerza el orden para observar
  la espera, y el control demuestra que sin el bloqueo la invariante se rompe.
- La garantía depende de que **todas** las vías de admisión pasen por `bloquearPorId`. Un `INSERT` directo en
  `inscripciones` que no bloquee la materia podría sobrepasar el cupo.
- Para que las tareas compartan la base, la prueba no usa `@Transactional` de prueba (que haría rollback y aislaría
  los datos). Cada repetición crea su propia materia y sus propios alumnos.
