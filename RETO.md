# Reto U03 — `reactivar()` e igualdad frente a identidad

Rama: `reto-u03-reactivar`. `master` conserva la solución guiada sin cambios, que es la base de U04.

## Cambios

- `Alumno.reactivar()` devuelve un **nuevo** `Alumno` con estatus `ACTIVO` y conserva id, matrícula,
  nombre y correo. Es la transición inversa de `darDeBaja()`.
- `AlumnoTest` agrega dos pruebas:
  - `reactivarConservaIdentidadSinModificarElOriginal`: BAJA → ACTIVO. Comprueba el resultado y que el
    original siga en `BAJA`.
  - `mismoIdConDistintoCorreoNoSonIgualesComoValor`: dos valores con `id = 1` y distinto correo.

Evidencia: `evidencia/reto-pruebas-alumno-resumen.txt` (AlumnoTest 5/5) y
`evidencia/reto-pruebas-resumen.txt` (suite completa 6/6).

## Igualdad del record frente a identidad académica

`equals` de un record es **igualdad por valor**: compara los cinco componentes (`id`, `matricula`, `nombre`,
`correo`, `estatus`). Dos instantáneas con el mismo `id` y distinto correo son, por lo tanto, **distintas**
(`assertNotEquals`).

La **identidad académica** responde otra pregunta: ¿se trata del mismo alumno? Eso lo determina un
identificador estable (`id` o la matrícula normalizada), no el resto de los datos. El correo de un alumno puede
cambiar sin que cambie el alumno. Las dos instantáneas de la prueba representan a **la misma persona en dos
momentos**, aunque como valores no sean iguales.

Consecuencias:

- Para preguntar "¿es el mismo alumno?" se comparan `id` o `matricula()`, no los objetos completos con `equals`.
- En un `Set<Alumno>` o como llave de un `Map`, las dos versiones cuentan como elementos distintos, porque
  `hashCode` también usa todos los componentes.
- Más adelante, una entidad JPA definirá su identidad con la llave primaria y el contexto de persistencia,
  no con la igualdad estructural del record.
