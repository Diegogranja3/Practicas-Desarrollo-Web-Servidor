# Entrega — Backend Académico (U12)

Instrucciones para construir, probar, ejecutar, observar y cerrar el sistema **desde una carpeta limpia**, sin IDE.
Todos los comandos son de PowerShell en Windows; en Linux/macOS usa `./mvnw` en lugar de `.\mvnw.cmd`.

## 1. Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | **25** recomendado (cualquier JDK ≥ 21 funciona) | Compilar. El `pom.xml` fija `maven.compiler.release=21`: el bytecode y la API son de **Java 21**, aunque compiles con JDK 25 |
| Docker Desktop | Con Compose v2 | Pruebas de integración (Testcontainers) y ejecución con `compose.yaml` |
| Git | Cualquiera | Obtener el código |

Maven **no** se instala: lo descarga el Maven Wrapper (`mvnw.cmd`) en la versión fijada por el proyecto.

Comprueba las versiones:

```powershell
java -version            # JDK 25 (o >= 21)
.\mvnw.cmd --version     # Maven del wrapper y el JDK que usa
docker version           # cliente y motor activos
```

La **imagen** de la aplicación ejecuta el JAR con **Java 25** (`eclipse-temurin:25-jre`, ver `Dockerfile`), aunque
se haya compilado con release 21. Así se separan la versión de compilación (21) y la de ejecución (25).

## 2. Obtener el código

```powershell
git clone https://github.com/Diegogranja3/Practicas-Desarrollo-Web-Servidor.git entrega
cd entrega
git checkout reto-u12-entrega      # o "master" / tag "u12" para la solución guiada
```

## 3. Construir y probar

Con Docker Desktop iniciado (lo necesita `PostgresIntegrationTest`, que usa un PostgreSQL temporal):

```powershell
.\mvnw.cmd clean test package
```

Resultado esperado: `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0` y `BUILD SUCCESS`, con el archivo
`target\backend-academico-0.0.12-SNAPSHOT.jar` generado. La prueba de integración **no** se omite si falta Docker:
falla, y eso es intencional.

## 4. Iniciar

```powershell
docker compose build        # imagen de la app con el JAR del paso anterior
docker compose up -d        # PostgreSQL (con healthcheck) y después la app
docker compose ps           # postgres "healthy" y app "Up"
docker compose logs app     # Flyway aplica V1 y V2; "Started BackendAcademicoApplication"
```

- La app escucha en `http://localhost:8080`. PostgreSQL **no** se publica fuera de la red de Compose: la app lo
  alcanza como `postgres:5432`.
- Las credenciales de la base llegan como variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`) y no están dentro
  del JAR.

## 5. Peticiones (todas las observaciones)

```powershell
powershell -ExecutionPolicy Bypass -File .\demo.ps1
```

`demo.ps1` usa `curl.exe` y requiere una base **nueva**, porque crea A001 y A002. Muestra:

| # | Observación | Esperado |
|---|---|---|
| 1 | Liveness y readiness | 200 `UP` |
| 2 | Éxito: crear alumno (docente + CSRF) | **201** con `Location` |
| 3 | Validación | **400** `DATOS_INVALIDOS` con `campos` |
| 4 | Ausencia | **404** `ALUMNO_NO_ENCONTRADO` |
| 5 | Conflicto | **409** `MATRICULA_DUPLICADA` |
| 6 | Seguridad (autenticación): sin credenciales | **401** |
| 7 | Seguridad (CSRF): docente válido sin token | **403** |
| 8 | Seguridad (rol): ALUMNO con token válido intenta crear | **403** |
| 9 | Propietario: `a001` ve su ficha / una ajena / la lista | 200 / **404** / **403** |
| 10 | Falla de PostgreSQL: liveness / readiness / lectura en caché / creación | 200 / **503** / 200 (desde la caché) / **500** con `referencia` |
| 11 | Recuperación de PostgreSQL | readiness 200, la creación responde 201 |

Las peticiones individuales también están en `requests.http` para el HTTP Client del IDE, pero no son necesarias.

Cuentas de laboratorio: `alumno/alumno123`, `docente/docente123`, `admin/admin123`, `a001/a001pass`, `a002/a002pass`.
Son públicas y solo sirven para el laboratorio.

## 6. Cerrar

```powershell
docker compose down          # detiene y quita contenedores y red; CONSERVA el volumen de datos
docker compose down -v       # además BORRA el volumen (los alumnos se pierden). Úsalo solo para empezar de cero.
```

## 7. Configuración relevante

| Variable | Predeterminado | Efecto |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Los de `compose.yaml` | Conexión a PostgreSQL |
| `ACADEMICO_CACHE_HABILITADA` | `true` | `false` desactiva la caché local (ver `RETO.md`, decisión sobre la caché) |
| `CALENDARIO_URL` | `https://calendario.example` | Proveedor de calendario (U11); el dominio de ejemplo no responde |
