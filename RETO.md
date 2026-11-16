# Reto U09 — 201 con `Location` y unicidad real en PostgreSQL

Rama: `reto-u09-location-y-unicidad`. `master` (tag `u09`) conserva la solución guiada, que es la base de U10.

## Pruebas

| Prueba | Infraestructura | Qué comprueba |
|---|---|---|
| `controller/AlumnoCreacionHttpTest` | Slice MVC (`@WebMvcTest`), servicio sustituido con `@MockitoBean`, sin red ni base | `POST /api/v1/alumnos` responde **201**, `Location: /api/v1/alumnos/42` y el cuerpo con `id` 42 y `estatus` ACTIVO |
| `repository/UnicidadPostgresTest` | Contexto completo + **PostgreSQL real** (Testcontainers), Flyway aplica V1 y V2 | Dos `guardar` con la misma matrícula sobre `AlumnoJpaAdapter`: el segundo choca con `uk_alumnos_matricula` y sale como `MatriculaDuplicadaException`; queda una sola fila |

Decisiones:

- El id simulado es **42**, no 1, para que un `Location` fijo o construido con otro valor no pase por casualidad.
- La prueba de unicidad llama al **adaptador**, no al servicio, porque `AlumnoService.crear` hace antes
  `existePorMatricula` y rechazaría el duplicado sin llegar a la base. Así, lo único que puede rechazar el segundo
  `INSERT` es la restricción de PostgreSQL. Es la situación de dos solicitudes simultáneas que pasan la comprobación
  previa al mismo tiempo.

Suite completa del reto: **11/11** (`evidencia/u09/reto-pruebas-resumen.txt`).

## Experimentos de control

Cada control rompe **una** cosa de la implementación y ejecuta **ambas** pruebas. Después se restauró el archivo: el
diff contra `u09` en `src/main` está vacío (`evidencia/u09/reto-controles.txt`).

| Control | Cambio temporal | `AlumnoCreacionHttpTest` | `UnicidadPostgresTest` |
|---|---|---|---|
| A | `Location` sin el id: `URI.create("/api/v1/alumnos")` | **Falla**: `expected </api/v1/alumnos/42> but was </api/v1/alumnos>` | Pasa |
| B | El adaptador busca `"uk_otra_restriccion"` en lugar de `"uk_alumnos_matricula"` | Pasa | **Falla**: `expected MatriculaDuplicadaException but was DataIntegrityViolationException` |

Cada prueba falla **por la razón esperada** (el mensaje describe exactamente el defecto introducido) y **solo** ante
su defecto.

## ¿Por qué detectan problemas distintos?

Comprueban fronteras diferentes con condiciones de experimento diferentes:

- **La prueba HTTP** verifica el **contrato de transporte**: que el controlador traduzca el resultado del caso de uso a
  201, a la cabecera `Location` correcta y al JSON publicado. El servicio es un mock, así que no puede ver nada de la
  base: aunque PostgreSQL aceptara duplicados, seguiría en verde (control B). Es rápida y aislada.
- **La prueba de PostgreSQL** verifica la **integridad en la autoridad compartida**: que V1 realmente cree la
  restricción en PostgreSQL, que el `INSERT` duplicado falle en la base y que el adaptador traduzca esa falla concreta
  a la excepción de dominio. No pasa por MVC, así que un `Location` incorrecto le es invisible (control A).

Un mock no puede demostrar una restricción UNIQUE real y una prueba de base no observa cabeceras HTTP. Por eso se
necesitan las dos. Además, la de PostgreSQL detecta un tipo de defecto que una base simulada (o H2) podría ocultar: el
nombre de la restricción que PostgreSQL reporta y la ruta de excepciones de Hibernate.
