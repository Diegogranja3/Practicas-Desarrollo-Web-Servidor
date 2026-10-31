# Reto U06 — Matrícula sin espacios internos

Rama: `reto-u06-matricula-sin-espacios`. `master` (tag `u06`) conserva la solución guiada, que es la base de U07.

## Regla

En `CrearAlumnoRequest`:

```java
@NotBlank @Size(max = 30)
@Pattern(regexp = "\\s*\\S*\\s*", message = "no debe contener espacios internos") String matricula
```

`@Pattern` exige que **toda** la cadena coincida: espacios opcionales al inicio, un bloque sin espacios y espacios
opcionales al final. Por eso:

| Entrada | Frontera HTTP | Resultado |
|---|---|---|
| `"A 001"`, `"A\t002"` | La rechaza `@Pattern` | 400 `DATOS_INVALIDOS`, campo `matricula`: "no debe contener espacios internos" |
| `"  a003  "` | La acepta | 201; el dominio guarda `"A003"` |
| `""` | La rechaza solo `@NotBlank` | 400 con un único error para `matricula` |
| Matrícula repetida | La acepta (la forma es válida) | 409 `MATRICULA_DUPLICADA`, sin cambios |

Decisiones:

- **Se permiten espacios externos** para conservar la normalización que ya hace `Alumno` (`strip()` y
  `toUpperCase(Locale.ROOT)`). Si la frontera los rechazara, cambiaría el contrato que los clientes ya usan.
- **`\S*` en lugar de `\S+`**: con `+`, una cadena vacía o en blanco también fallaría el patrón y el cliente
  recibiría dos errores para la misma causa. Con `*`, la ausencia solo la reporta `@NotBlank`.
- **No se agregaron códigos**: el rechazo es un `MethodArgumentNotValidException` más, que el
  `GlobalExceptionHandler` ya traduce a `DATOS_INVALIDOS` con la lista de `campos`.
- `ActualizarAlumnoRequest` no cambia, porque PUT no edita la matrícula.

## Qué rechaza la frontera y qué guarda el dominio

- **Frontera HTTP (forma del mensaje)**: rechaza la matrícula ausente o en blanco, la de más de 30 caracteres y,
  desde este reto, la que tiene espacios o tabuladores **dentro** del valor.
- **Dominio (representación canónica)**: `Alumno` recorta los espacios externos y pasa a mayúsculas con
  `Locale.ROOT`. `"  a003  "` y `"A003"` se guardan igual, como `"A003"`, y por eso el segundo POST da 409.

El experimento de control muestra que la regla vive **solo** en la frontera: sin `@Pattern`, `"A 001"` se crea con
201 porque el dominio no prohíbe espacios internos. Si la regla debe valer también fuera de HTTP (por ejemplo, en
una carga masiva), tendría que agregarse al constructor de `Alumno`.

## Pruebas

`MatriculaSinEspaciosHttpTest` levanta la aplicación en un puerto aleatorio (`RANDOM_PORT`) y envía peticiones
reales con `java.net.http.HttpClient`, sin dependencias nuevas:

1. `rechazaEspacioInterno`: `"A 001"` → 400, `DATOS_INVALIDOS` y el mensaje del campo.
2. `rechazaTabuladorInterno`: `"A\t002"` → 400.
3. `conservaLaNormalizacionDeEspaciosExternos`: `"  a003  "` → 201 y `"matricula":"A003"`.
4. `vaciaSoloReportaNotBlank`: `""` → 400 sin el mensaje del patrón.

| Escenario | Resultado |
|---|---|
| Con la regla | Suite completa 8/8 (`evidencia/u06/reto-pruebas-resumen.txt`) |
| Control sin `@Pattern` | 2 de 4 fallan: `expected: <400> but was: <201>` (`evidencia/u06/reto-control-sin-pattern.txt`) |
