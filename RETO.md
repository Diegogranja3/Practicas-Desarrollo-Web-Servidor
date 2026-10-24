# Reto U05 — Filtro opcional de estatus en `GET /api/v1/alumnos`

Rama: `reto-u05-filtro-estatus`. `master` (tag `u05`) conserva la solución guiada, que es la base de U06.

## Cambios

| Archivo | Cambio |
|---|---|
| `AlumnoController` | `listar(@RequestParam(required = false) EstatusAlumno estatus)` |
| `AlumnoService` | Nuevo `listar(EstatusAlumno estatus)`: si es `null`, delega en `listar()`; si no, filtra con un `Stream` |
| `AlumnoServiceFiltroTest` | 4 pruebas: sin filtro, ACTIVO, BAJA y que consultar no modifique el repositorio |
| `requests.http` | Las tres peticiones del reto |

```java
public List<Alumno> listar(EstatusAlumno estatus) {
    if (estatus == null) return listar();
    return repository.buscarTodos().stream()
        .filter(alumno -> alumno.estatus() == estatus)
        .toList();
}
```

El filtro va en el **servicio** y no en el controlador: es una regla de consulta de la aplicación y debe funcionar
igual si la llamada no llega por HTTP. El controlador solo convierte `?estatus=` en un `EstatusAlumno`.

## Las tres peticiones y su resultado esperado

Estado previo: dos alumnos creados con `POST` (`A001` y `A002`). Evidencia: `evidencia/u05/reto-peticiones-http.txt`.

| Petición | Resultado | Por qué |
|---|---|---|
| `GET /api/v1/alumnos` | 200, `A001` y `A002` | Sin parámetro, `estatus` llega `null` y se usa el `listar()` original: el contrato no cambia para los clientes existentes. |
| `GET /api/v1/alumnos?estatus=ACTIVO` | 200, `A001` y `A002` | `crear` siempre asigna `ACTIVO`, así que todo alumno creado por la API cumple el filtro. |
| `GET /api/v1/alumnos?estatus=BAJA` | 200, `[]` | La API todavía no ofrece una operación de baja, así que ningún alumno está en `BAJA`. Una lista vacía es una colección válida, no un error (no es 404). |

Extra: `?estatus=PAUSA` responde **400** antes de invocar el método, porque MVC no puede convertir el texto al enum.
En U06 se le dará a ese error un cuerpo propio.

La prueba unitaria sí cubre el caso `BAJA` con datos: prepara a un alumno con `darDeBaja()` directo en el
repositorio y comprueba que el filtro lo devuelve.

## Consultar no modifica el repositorio

- `buscarTodos()` entrega una copia (`List.copyOf`). `filter` solo lee esa copia y `toList()` produce **otra** lista
  no modificable, así que ni el mapa del repositorio ni los `Alumno` (inmutables) cambian.
- `consultarNoModificaElRepositorio` guarda `buscarTodos()` antes, ejecuta los tres filtros y comprueba que la lista
  posterior sea igual (mismos alumnos, orden y valores) y que el alumno en `BAJA` siga en `BAJA`.
- En HTTP, el último `GET` sin filtro devuelve los mismos dos alumnos después de las consultas filtradas.

El recorrido es lineal en el número de alumnos. Con PostgreSQL (U07), el filtro conviene hacerlo en la consulta
(`WHERE estatus = ?`) para no traer todos los registros a memoria.
