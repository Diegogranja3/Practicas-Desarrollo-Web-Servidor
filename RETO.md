# Reto U07 — Migración V2: nombre no vacío

Rama: `reto-u07-nombre-no-vacio`. `master` (tag `u07`) conserva la solución guiada, que es la base de U08.

## Migración

`src/main/resources/db/migration/V2__nombre_no_vacio.sql`

```sql
ALTER TABLE alumnos
    ADD CONSTRAINT ck_nombre_no_vacio CHECK (LENGTH(TRIM(nombre)) > 0);
```

- **No se reescribe V1.** V1 ya está aplicada y Flyway guarda su checksum en `flyway_schema_history`. Si se editara
  `V1__crear_alumnos.sql`, la validación del siguiente arranque fallaría por checksum distinto en toda base donde V1
  ya corrió. Un cambio de esquema se expresa como una versión nueva.
- **`ALTER TABLE ... ADD CONSTRAINT`** modifica la tabla existente: no hay `DROP`/`CREATE` y las filas se conservan.
- La regla es la misma que `@NotBlank` en la frontera HTTP (U06), pero aquí la aplica la autoridad compartida:
  también protege inserciones que no pasan por la API.

## Laboratorio: copias de la base, no la original

El reto pide trabajar sobre una copia. En el mismo PostgreSQL de `compose.yaml` se crearon dos copias de la base
`academico` (que tenía a `A001` guardado en U07):

```sql
CREATE DATABASE academico_reto TEMPLATE academico;
CREATE DATABASE academico_incompatible TEMPLATE academico;
```

La aplicación se dirigió a cada copia con la variable de U07, sin cambiar el código:
`$env:DB_URL = 'jdbc:postgresql://localhost:5432/academico_reto'`. La base `academico` siguió en V1.

## 1. Base con datos compatibles: V2 se aplica y no recrea la tabla

Evidencia: `evidencia/u07/reto-copias.txt` y `evidencia/u07/reto-migracion.txt`.

| | Antes de V2 | Después de V2 |
|---|---|---|
| `oid` de la tabla `alumnos` | 16395 | **16395** |
| Filas | 1 (`A001`) | **1** (`A001`, mismo id y datos) |
| Versión en `flyway_schema_history` | 1 | 2 |

- El **oid** es el identificador interno de PostgreSQL para la tabla. Si V2 la hubiera borrado y recreado, sería otro.
  Que no cambie demuestra que es la misma tabla, modificada en su lugar.
- El historial queda con dos filas, `V1__crear_alumnos.sql` y `V2__nombre_no_vacio.sql`, cada una con su checksum y
  `success = t`.
- En el arranque, Flyway valida 2 migraciones, detecta que la versión actual es 1 y aplica solo la 2.
- Después de V2, un `INSERT` directo con nombre `'   '` falla con `ck_nombre_no_vacio`. Por la API, el mismo caso lo
  rechaza antes `@NotBlank` (400 `DATOS_INVALIDOS`).

## 2. ¿Y si ya existieran filas incompatibles?

Evidencia: `evidencia/u07/reto-incompatible.txt`.

Con V1, un nombre `'   '` es válido para la base (`NOT NULL` no lo impide), así que en `academico_incompatible` se
insertó `B001` con nombre en blanco. Al arrancar la aplicación con V2:

```
Migrating schema "public" to version "2 - nombre no vacio"
Migration V2__nombre_no_vacio.sql failed
SQL State  : 23514
Message    : ERROR: check constraint "ck_nombre_no_vacio" of relation "alumnos" is violated by some row
APPLICATION FAILED TO START
```

Qué ocurre:

1. **PostgreSQL valida la restricción contra todas las filas existentes** al agregarla. Una sola fila que la viole
   aborta el `ALTER TABLE`.
2. **Flyway detiene el arranque.** Spring no llega a levantar Tomcat: la aplicación no corre con un esquema a medias.
3. **Nada queda a medias.** En PostgreSQL el DDL es transaccional: la migración se revierte completa. El historial
   sigue en V1 (sin fila fallida), la restricción no existe y los datos, incluido `B001`, siguen intactos. Al
   corregir los datos, el siguiente arranque vuelve a intentar V2.

Cómo resolverlo sin reescribir V1 (fuera del alcance de este reto):

- **Corregir los datos en la misma migración antes de la restricción**, por ejemplo
  `UPDATE alumnos SET nombre = 'SIN NOMBRE' WHERE LENGTH(TRIM(nombre)) = 0;`. Esto exige decidir qué valor es correcto
  para el negocio.
- **`ADD CONSTRAINT ... NOT VALID`** y después `VALIDATE CONSTRAINT`: la regla aplica a filas nuevas de inmediato y
  las antiguas se validan cuando ya estén limpias.
- No se debe "arreglar" borrando filas ni recreando la tabla: se perderían datos.

## Base original

La base `academico` conserva su dato (`A001`) y sigue en V1. Las copias de laboratorio pueden borrarse con:

```sql
DROP DATABASE academico_reto;
DROP DATABASE academico_incompatible;
```
