# Reto U10 — "Mi expediente" con acceso por objeto

Rama: `reto-u10-mi-expediente`. `master` (tag `u10`) conserva la solución guiada, que es la base de U11.

## 1. Regla de acceso (definida antes de implementar)

**Identidad.** La identidad sale **solo del principal autenticado** (`Authentication.getName()`), que Spring Security
fija al verificar las credenciales de HTTP Basic. Ninguna operación de expediente acepta un usuario, matrícula o id en
la petición como prueba de "quién soy". Si el cliente manda `?usuario=otro`, se ignora.

**Vínculo cuenta → alumno.** En este laboratorio, la cuenta de un alumno se llama como su matrícula. Por ejemplo, la
cuenta `a001` corresponde a la matrícula `A001`, porque el dominio normaliza a mayúsculas. Es un vínculo controlado por
el servidor (las cuentas se dan de alta en `SecurityConfig`), no por el cliente. En un sistema real sería una columna
`usuario` o el `sub` de un token OIDC.

**Operaciones:**

| Operación | Quién | Regla |
|---|---|---|
| `GET /api/v1/expediente` (mi expediente) | Rol `ALUMNO` | Devuelve el expediente del alumno cuya matrícula corresponde al principal. Sin alumno vinculado: 404 |
| `GET /api/v1/expediente/{id}` (expediente por id) | Roles `ALUMNO` y `ADMIN` | **Por objeto**: `ADMIN` puede ver cualquiera; `ALUMNO` solo el suyo (matrícula del alumno `id` = su cuenta) |

**Decisiones:**

1. **Dos niveles de control.** La cadena de filtros decide por **rol** (sin credenciales, 401; `DOCENTE`, 403). El
   servicio decide por **objeto**, porque solo después de cargar el alumno se sabe a quién pertenece. El rol `ALUMNO` no
   basta para leer expedientes ajenos.
2. **Un expediente ajeno responde 404, no 403.** Para un `ALUMNO`, "no existe" y "existe pero no es tuyo" dan la misma
   respuesta (`ALUMNO_NO_ENCONTRADO`). Si respondiera 403, un alumno podría recorrer ids y descubrir cuáles existen. Las
   pruebas distinguen el caso ajeno del propio y del administrador por el resultado: 404, 200 y 200.
3. **El expediente es más sensible que `GET /alumnos/{id}`**, porque incluye el historial de inscripciones. Por eso
   `DOCENTE` no está en la regla del expediente, aunque sí puede leer la ficha del alumno.
4. La regla vive en `ExpedienteService`, no en el controlador, para que aplique igual a cualquier vía que lo use.

## 2. Casos de prueba (escritos antes de la implementación)

`controller/ExpedienteSeguridadTest`: slice MVC con la **cadena real de filtros** (`SecurityConfig`) y el
**`ExpedienteService` real**; solo se sustituyen `AlumnoService` e `InscripcionService` para controlar los datos.

| # | Caso | Esperado |
|---|---|---|
| 1 | Propietario `a001` pide **mi expediente** | 200 con matrícula A001 e inscripciones |
| 2 | Propietario `a001` manda `?usuario=a010` | 200 con **A001**: el parámetro se ignora |
| 3 | Propietario `a001` pide `/expediente/1` (A001) | 200 |
| 4 | Usuario ajeno `a001` pide `/expediente/4` (A010) | 404 `ALUMNO_NO_ENCONTRADO`; no se consultan inscripciones |
| 5 | Ajeno con id inexistente (`/expediente/999`) | 404 idéntico al caso 4 |
| 6 | `admin` pide `/expediente/4` | 200 con A010 |
| 7 | `docente` pide `/expediente/4` | 403 (filtro, por rol) |
| 8 | Sin credenciales | 401 |
| 9 | `admin` pide **mi expediente** | 403: no es una cuenta de alumno |

## 3. Implementación

| Archivo | Cambio |
|---|---|
| `service/ExpedienteService.java` | **Regla por objeto.** `delPrincipal(Authentication)` busca por la matrícula de la cuenta. `porId(id, Authentication)` carga el alumno y, si el principal no es `ADMIN` ni propietario, lanza `AlumnoNoEncontradoException(id)` (el mismo 404 que si no existiera) |
| `controller/ExpedienteController.java` | `GET /api/v1/expediente` y `GET /api/v1/expediente/{id}`. Recibe el `Authentication` que inyecta Spring Security; no tiene parámetros de identidad |
| `config/SecurityConfig.java` | Reglas de rol: `/expediente` solo `ALUMNO`; `/expediente/*` `ALUMNO` o `ADMIN`. Cuentas de laboratorio `a001` y `a010` vinculadas por matrícula |
| `AlumnoRepository` (+ memoria, JPA, Spring Data) y `AlumnoService` | `buscarPorMatricula`, con la misma normalización que el dominio |

## 4. Evidencia

- `ExpedienteSeguridadTest`: **9/9**. Suite completa del reto: **22/22** (`evidencia/u10/reto-pruebas-resumen.txt`).
- **Control** (`evidencia/u10/reto-control-sin-regla.txt`): al desactivar temporalmente la comprobación de
  propietario/administrador, falla `usuarioAjenoRecibe404SinConsultarInscripciones` (`expected 404 but was 200`): un
  alumno leería el expediente de otro. Se restauró.
- **Aplicación real con PostgreSQL** (`evidencia/u10/reto-expediente-http.txt`):

| Petición | Usuario | Resultado |
|---|---|---|
| `GET /expediente` | `a001` | 200, A001 con su inscripción en MAT101 |
| `GET /expediente?usuario=a010` | `a001` | 200, **A001** (el parámetro no cambia la identidad) |
| `GET /expediente/4` (A010) | `a001` | 404 `ALUMNO_NO_ENCONTRADO` |
| `GET /expediente/999` | `a001` | 404 `ALUMNO_NO_ENCONTRADO`, misma forma que el anterior |
| `GET /expediente/4` | `admin` | 200, A010 |
| `GET /expediente/4` | `docente` | 403 (filtro de rol) |

## 5. Límites

- El vínculo cuenta→matrícula por convención de nombre es propio del laboratorio. Con usuarios reales, el vínculo
  debe guardarse explícitamente (columna o `sub` de OIDC) y no deducirse de un texto.
- `GET /api/v1/alumnos/{id}` (U05) sigue abierto a cualquier `ALUMNO` según la regla guiada de U10. El expediente no
  cierra esa ruta; aplicar la misma política por objeto ahí sería el siguiente paso (U12 lo pide como mejora).
