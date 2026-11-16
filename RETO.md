# Reto U12 — Entrega reproducible del sistema

Rama: `reto-u12-entrega`. `master` (tag `u12`) conserva la solución guiada.

| Entregable | Archivo |
|---|---|
| Instrucciones desde una carpeta limpia: JDK 25 / Java 21, construcción, pruebas, inicio, peticiones y cierre | `ENTREGA.md` |
| Todas las observaciones, repetibles sin IDE | `demo.ps1` (PowerShell + `curl.exe`) |
| Mejora de acceso por propietario | `controller/AccesoPorPropietario.java`, `SecurityConfig`, `AccesoPorPropietarioTest` |
| Caché configurable según la decisión de despliegue | `config/CacheConfig.java` (`ACADEMICO_CACHE_HABILITADA`) |

## 1. Entrega desde una carpeta limpia

Para probar la entrega se **clonó el repositorio desde GitHub en una carpeta nueva** y se siguió `ENTREGA.md` sin el
IDE (`evidencia/u12/reto-entrega-*.txt`):

1. `git clone` y `git checkout reto-u12-entrega`: sin `target`, sin nada compilado.
2. `.\mvnw.cmd clean test package` con JDK 21 (release 21): **19/19**, `BUILD SUCCESS`, JAR generado.
3. `docker compose build` y `up -d`: PostgreSQL `healthy`. La app arranca **con Java 25.0.5** dentro de la imagen y
   Flyway aplica V1 y V2 sobre una base vacía.
4. `demo.ps1`: todas las observaciones de la tabla siguiente.
5. `docker compose down -v` sobre **ese** proyecto de prueba (`entrega_*`). El volumen del laboratorio
   (`practicas-desarrollo-web-servidor_datos_academicos`) no se tocó.

## 2. Observaciones (`evidencia/u12/reto-entrega-3-demo.txt`)

| Observación | Petición | Resultado |
|---|---|---|
| **Éxito** | `POST /alumnos` A001 (docente + CSRF) | **201**, `Location: /api/v1/alumnos/1` |
| **Validación** | Cuerpo con matrícula vacía, nombre corto y correo inválido | **400** `DATOS_INVALIDOS` con 3 campos |
| **Ausencia** | `GET /alumnos/999` | **404** `ALUMNO_NO_ENCONTRADO` |
| **Conflicto** | `POST` A001 otra vez | **409** `MATRICULA_DUPLICADA` |
| **Seguridad 1 — autenticación** | `GET /alumnos/1` sin credenciales | **401**: no se sabe quién es |
| **Seguridad 2 — CSRF** | `POST` de docente con credenciales válidas, **sin token** | **403**: el usuario y el rol son correctos, falta la prueba de origen |
| Seguridad 3 — rol (extra) | `POST` de `alumno` **con** token válido | **403**: el token es válido, el rol no tiene permiso |
| Propietario | `a001` → su ficha / ficha ajena / lista | 200 / **404** / **403** |
| Falla de PostgreSQL | Ver sección 3 | |

Los dos rechazos de seguridad requeridos son de **causas distintas**: el 401 es falta de **autenticación**, y el 403 de
CSRF ocurre con un usuario autenticado y autorizado al que le falta el **token de la sesión**. El tercero aísla la causa
de **rol**. Ninguno pasa por `GlobalExceptionHandler` (ocurren en los filtros), por eso su cuerpo no es `ProblemDetail`.

Nota: dentro del contenedor, los mensajes de Bean Validation salen en inglés (`must not be blank`) porque la JVM de la
imagen no tiene configurado el locale `es_MX`. Los códigos (`DATOS_INVALIDOS`, nombres de campo) no cambian.

## 3. Falla controlada de PostgreSQL y readiness

`demo.ps1` ejecuta `docker compose stop postgres` con la app corriendo:

| Sonda / petición | Con PostgreSQL detenido | Tras `docker compose start postgres` |
|---|---|---|
| `/actuator/health/liveness` | **200 UP** | 200 UP |
| `/actuator/health/readiness` | **503 DOWN** | **200 UP** (sin reiniciar la app) |
| `GET /alumnos/1` (A001 ya en caché) | **200** (respuesta desde la caché local) | 200 |
| `POST /alumnos` A003 | **500 `ERROR_INTERNO`** con `referencia` | **201** |

Qué ocurre:

- **Liveness** responde "¿el proceso está vivo?". Sí lo está, así que reiniciarlo no arreglaría nada: el problema está
  en una dependencia. Por eso liveness **no** incluye la base.
- **Readiness** incluye `db` (`management.endpoint.health.group.readiness.include=readinessState,db`) y responde "¿puedo
  atender tráfico?". Con la base caída es **503 DOWN**. Un orquestador (Kubernetes, un balanceador) deja de enviarle
  solicitudes, pero no mata el contenedor.
- Cuando PostgreSQL vuelve, el pool de conexiones (Hikari) se reconecta solo y readiness regresa a **UP**: la app se
  recupera **sin reiniciarse**.
- Las solicitudes que llegan de todos modos y necesitan la base terminan en **500 `ERROR_INTERNO`** con una
  `referencia`. El cliente no ve detalles internos, y el log registra esa referencia y el tipo de excepción (U06).
- **Hallazgo:** la lectura de A001 respondió **200 con la base caída**, porque venía de la **caché local**. Readiness
  dice "no estoy listo", pero una parte del tráfico se sigue sirviendo con datos que no se pueden validar contra la base.
  Esto alimenta directamente la decisión siguiente.

## 4. Decisión argumentada sobre la caché

Evidencia (`evidencia/u12/reto-cache.txt`): tres `GET /alumnos/6` seguidos.

| `ACADEMICO_CACHE_HABILITADA` | SQL por consulta |
|---|---|
| `true` (predeterminado) | 1, **0**, **0** |
| `false` | 1, 1, 1 |

**Decisión: mantener la caché solo en el despliegue de UNA instancia (el de `compose.yaml`) y desactivarla con
`ACADEMICO_CACHE_HABILITADA=false` en cuanto haya más de una réplica.** En ese caso, la alternativa sería una caché
compartida con TTL (por ejemplo Redis).

Argumentos:

1. **A favor, en una instancia:** `buscar` y `listar` se repiten con frecuencia y sus datos cambian poco. La caché evita
   consultas, como muestran las mediciones. `TransactionAwareCacheManagerProxy` invalida **al confirmar**, así que un
   rollback no deja entradas inconsistentes, y en una sola JVM toda escritura invalida la única caché que existe.
2. **En contra, con varias réplicas:** cada JVM tiene su propio `ConcurrentMap`. Un `PUT` atendido por la réplica A
   invalida solo la caché de A, y la réplica B sigue respondiendo el valor viejo **indefinidamente**, porque no hay TTL.
   Eso no es un retraso acotado: es una inconsistencia sin límite de tiempo.
3. **La falla de PostgreSQL** mostró otro efecto: la caché sigue sirviendo lecturas aunque la base no esté. Es
   aceptable para una ficha de catálogo, pero no para datos que deban reflejar el estado actual. Por eso la caché nunca
   se usa para decidir cupo (U08 consulta la base bajo bloqueo) ni para la regla de acceso.
4. **Seguridad:** la clave de caché es el `id` y el valor es el alumno, **igual para todos los usuarios**. La regla de
   propietario se evalúa **después** de obtener el alumno de la caché, en el controlador. Si se cacheara la respuesta
   ya autorizada con una clave sin el usuario, un alumno podría recibir la ficha que se cacheó para otro.
5. **Sin límites:** no hay TTL ni tamaño máximo. Con el tamaño del laboratorio no importa, pero con un catálogo real
   habría que agregarlos.

La opción se implementó sin tocar el resto del código: `CacheConfig` devuelve un `NoOpCacheManager` cuando la propiedad
es `false`. Las anotaciones `@Cacheable`/`@CacheEvict` quedan iguales y simplemente no guardan nada.

## 5. Mejora de acceso por propietario

En la solución guiada, cualquier cuenta con rol `ALUMNO` podía leer la ficha de **cualquier** alumno y la lista
completa. La mejora:

- `GET /api/v1/alumnos` (lista): solo `DOCENTE` y `ADMIN`.
- `GET /api/v1/alumnos/{id}`: `DOCENTE` y `ADMIN` ven cualquiera. Una cuenta `ALUMNO` solo la **suya**: el vínculo es
  cuenta = matrícula (`a001` → `A001`), y lo fija el servidor, nunca un parámetro del cliente. Una ficha ajena responde
  **404**, igual que una inexistente, para no revelar qué ids existen.
- `service.buscar(id)` se llama desde el controlador, atravesando el proxy de caché. La comprobación es una función pura
  (`AccesoPorPropietario.exigir`) aplicada al resultado. Si estuviera **dentro** de `AlumnoService` llamando a
  `this.buscar`, esa llamada interna no pasaría por el proxy (U12, sección 12.2).

`AccesoPorPropietarioTest` (cadena real de filtros): propietario 200, ajeno e inexistente 404, lista 403, docente y
admin 200. **4/4**. Suite completa del reto: **19/19**.

## 6. Límites

- Las credenciales de laboratorio están en memoria y en texto conocido. Fuera de `localhost` se necesita TLS y un
  proveedor de identidad.
- `docker compose down` conserva el volumen, pero un volumen no es un respaldo: falta una copia recuperable
  (`pg_dump`) y una prueba de restauración.
- La tarea programada corre en cada instancia; con varias réplicas, el resumen se duplicaría.
