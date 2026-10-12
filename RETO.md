# Reto U04 — Dos registros simultáneos de la misma matrícula

Rama: `reto-u04-concurrencia`. `master` (tag `u04`) conserva la solución guiada, que es la base de U05.

## Prueba

`src/test/java/mx/edu/backendacademico/repository/AlumnoRepositoryConcurrenciaTest.java`

1. Dos tareas `Callable` en un `ExecutorService` de dos hilos llaman a `AlumnoService.crear("A001", …)`.
2. Un `CountDownLatch` **listas(2)** confirma que ambas tareas llegaron al punto de partida, y otro **salida(1)**
   las libera al mismo tiempo para que compitan.
3. La prueba espera a que terminen con `Future.get(timeout)`, sin `Thread.sleep`.
4. Verifica **exactamente un registro** (`true`), **exactamente un conflicto** (`MatriculaDuplicadaException`) y
   que el repositorio tenga un solo alumno con matrícula `A001`.

Se repite 50 veces (`@RepeatedTest(50)`) para observar varios entrelazados.

## Evidencia

| Escenario | Resultado |
|---|---|
| Solución guiada (`synchronized` en el repositorio) | 50/50 repeticiones correctas; suite completa 54/54 (`evidencia/u04/reto-pruebas-resumen.txt`) |
| Control: se quitó `synchronized` temporalmente | **32 de 50 fallan**: `Exactamente un registro ==> expected: <1> but was: <2>` (`evidencia/u04/reto-control-sin-synchronized.txt`) |

El experimento de control muestra que la prueba sí detecta la carrera: sin el monitor, ambas tareas leen
"no existe A001" antes de que alguna escriba, y las dos insertan. El archivo se restauró después; el commit
contiene el repositorio de la solución guiada sin cambios.

## ¿Por qué un contador atómico aislado no demostraría la propiedad?

La invariante es **"no hay dos alumnos con la misma matrícula"**. Abarca dos pasos: *comprobar* que la
matrícula no existe y *escribir* el alumno. Un `AtomicLong` solo hace indivisible el incremento del id:
garantiza ids distintos, pero no impide que las dos tareas pasen la comprobación antes de que una escriba. El
resultado serían dos alumnos con ids 1 y 2 y la misma matrícula. Es decir, el contador sería correcto y la
invariante estaría rota.

Por eso la prueba cuenta **registros y conflictos** en el almacén y no ids generados. En la solución, la
comprobación y la escritura ocurren dentro del mismo monitor (`synchronized guardar`), así que la segunda tarea
siempre ve el registro de la primera. La comprobación previa de `AlumnoService.crear` (`existePorMatricula`) no
basta, porque ocurre fuera de ese monitor. Solo mejora el mensaje de error, y la protección real es la segunda
comprobación dentro de `guardar`.

## Límites

- Que pasen 50 ejecuciones con dos hilos no prueba que el código sea correcto en **todos** los entrelazados
  posibles, ni con más hilos o más carga. Solo muestra que no se observó la carrera, y el control demuestra que,
  cuando la carrera existe, la prueba la detecta.
- `synchronized` protege una instancia dentro de una JVM. Con dos réplicas de la aplicación, la unicidad tendrá
  que garantizarla una autoridad compartida, como la restricción única de PostgreSQL (U07–U08).
