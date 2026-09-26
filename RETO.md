# Reto U01 — Ejecutar en 8081 sin editar la clase principal

Rama: `reto-u01-puerto-8081`. `master` conserva la configuración base (`server.port=8080`).

## Cómo se hizo

Se empaquetó el JAR **una sola vez** (`.\mvnw.cmd package`) y se arrancó ese mismo archivo
con dos formas de configuración externa:

```powershell
# 1) Variable de ambiente (relaxed binding: SERVER_PORT -> server.port)
$env:SERVER_PORT = "8081"; java -jar target\backend-academico-0.0.1-SNAPSHOT.jar

# 2) Argumento de línea de comandos
java -jar target\backend-academico-0.0.1-SNAPSHOT.jar --server.port=8081

# Con Maven, sin JAR:
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

Sin variable ni argumento, el mismo JAR vuelve a 8080.

## Evidencia (`evidencia/`)

| Archivo | Qué muestra |
|---|---|
| `01-versiones.txt` | `java -version` y `mvnw --version` |
| `02-pruebas-resumen.txt` | `Tests run: 1, Failures: 0, Errors: 0` · `BUILD SUCCESS` |
| `03-arranque-8080.txt` | Solución base: `Tomcat started on port 8080`, `/` responde 404 (aún no hay rutas) |
| `reto-arranque-8081-env.txt` | `SERVER_PORT=8081` → `Tomcat started on port 8081`; 8080 sin escuchar |
| `reto-arranque-8081-arg.txt` | `--server.port=8081` → `Tomcat started on port 8081` |
| `reto-arranque-base-8080.txt` | Mismo JAR sin configuración externa → 8080 |
| `reto-jar-sha256.txt` | SHA-256 del JAR: idéntico antes y después de los tres arranques |

`git diff master -- src pom.xml` no tiene cambios: no se tocó código Java ni la construcción.

## Por qué cambiar el puerto no exige recompilar

El puerto no forma parte de la lógica compilada. `BackendAcademicoApplication` sólo llama a
`SpringApplication.run`; el bytecode no contiene `8080`. Al **arrancar**, Spring Boot arma un
`Environment` con varias fuentes de propiedades ordenadas por prioridad: argumentos de línea
de comandos > variables de ambiente > `application.properties` dentro del JAR. La
autoconfiguración del servidor embebido lee `server.port` de ese `Environment` y configura
Tomcat con el valor ganador.

Como la resolución ocurre en tiempo de ejecución, basta con aportar una fuente de mayor
prioridad (`--server.port` o `SERVER_PORT`) para cambiar el puerto; el `.class` y el JAR son
los mismos, lo que se comprueba con el SHA-256. Recompilar sólo hace falta cuando cambia el
código fuente, no cuando cambia la configuración del ambiente.

Limitación: una prueba `@SpringBootTest` sin puerto real no demuestra que la red sea
accesible; por eso la evidencia incluye el log de Tomcat, `netstat` y la petición HTTP.

## Entorno

La práctica pide JDK 25. En este equipo se ejecutó con **JDK 21.0.10 (Temurin)**, que también
compila con `release 21`. El resultado es el mismo bytecode Java 21.
