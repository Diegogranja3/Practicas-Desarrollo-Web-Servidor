package mx.edu.backendacademico.service;

// Reto U08: dos admisiones simultáneas a una materia con un solo lugar, contra PostgreSQL real.

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.OperacionAcademicaException;
import mx.edu.backendacademico.repository.jpa.*;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class InscripcionConcurrenteTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Base temporal: Flyway aplica V1 y V2 sobre un PostgreSQL efímero, no sobre la del laboratorio.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired AlumnoSpringDataRepository alumnos;
    @Autowired MateriaRepository materias;
    @Autowired InscripcionRepository inscripciones;
    @Autowired InscripcionService servicio;   // bean real: cada llamada atraviesa el proxy transaccional
    @Autowired PlatformTransactionManager transacciones;
    @Autowired JdbcTemplate jdbc;

    @RepeatedTest(10)
    void dosAdmisionesSimultaneasDejanUnaInscripcionYUnRechazoPorCupo() throws Exception {
        var escenario = prepararMateriaConUnLugar();
        var listas = new CountDownLatch(2);
        var salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = pool.submit(admitir(escenario.alumnoA(), escenario.materia(), listas, salida));
            Future<String> b = pool.submit(admitir(escenario.alumnoB(), escenario.materia(), listas, salida));
            assertTrue(listas.await(10, TimeUnit.SECONDS), "Las tareas no llegaron al punto de partida");
            salida.countDown();

            // get() espera la terminación de cada tarea, sin pausas arbitrarias.
            var resultados = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertEquals(1, resultados.stream().filter("INSCRITO"::equals).count(), "Una admisión persiste: " + resultados);
            assertEquals(1, resultados.stream().filter("SIN_CUPO"::equals).count(), "La otra se rechaza por cupo: " + resultados);
            assertEquals(1, inscripciones.countByMateriaId(escenario.materia()), "Una sola fila en inscripciones");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void laSegundaAdmisionEsperaElBloqueoDentroDePostgreSQL() throws Exception {
        var escenario = prepararMateriaConUnLugar();
        var bloqueada = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var trabajador = new AtomicReference<Thread>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // 1. Otra transacción toma el mismo bloqueo que inscribir() y lo retiene.
            Future<?> retenedor = pool.submit(() -> new TransactionTemplate(transacciones).execute(estado -> {
                materias.bloquearPorId(escenario.materia()).orElseThrow();
                bloqueada.countDown();
                try { liberar.await(30, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return null;
            }));
            assertTrue(bloqueada.await(10, TimeUnit.SECONDS));

            // 2. La admisión real intenta bloquear la misma fila y debe quedarse esperando.
            Future<Long> admision = pool.submit(() -> {
                trabajador.set(Thread.currentThread());
                return servicio.inscribir(escenario.alumnoA(), escenario.materia());
            });

            // 3. Evidencia en PostgreSQL: una sesión espera un Lock en la consulta de materias.
            Map<String, Object> espera = esperarSesionBloqueada();
            System.out.println("[RETO-U08] pg_stat_activity de la admisión en espera: " + espera);

            // 4. Evidencia en Java: el hilo NO está BLOCKED en un monitor; está dentro del driver JDBC esperando la red.
            Thread hilo = trabajador.get();
            var marcos = hilo.getStackTrace();
            boolean enDriver = java.util.Arrays.stream(marcos).anyMatch(m -> m.getClassName().startsWith("org.postgresql"));
            System.out.println("[RETO-U08] Estado del hilo de la admisión: " + hilo.getState()
                + " | en el driver org.postgresql: " + enDriver + " | marco superior: " + marcos[0]);
            assertNotEquals(Thread.State.BLOCKED, hilo.getState(), "No espera un monitor de Java");
            assertTrue(enDriver, "La espera ocurre dentro de la llamada JDBC");
            assertFalse(admision.isDone(), "La admisión sigue esperando mientras el bloqueo exista");

            // 5. Al confirmar la otra transacción se libera la fila y la admisión continúa.
            liberar.countDown();
            retenedor.get(30, TimeUnit.SECONDS);
            assertNotNull(admision.get(30, TimeUnit.SECONDS));
            assertEquals(1, inscripciones.countByMateriaId(escenario.materia()));
        } finally {
            liberar.countDown();
            pool.shutdownNow();
        }
    }

    private Callable<String> admitir(Long alumnoId, Long materiaId, CountDownLatch listas, CountDownLatch salida) {
        return () -> {
            listas.countDown();
            salida.await();
            try {
                servicio.inscribir(alumnoId, materiaId);
                return "INSCRITO";
            } catch (OperacionAcademicaException e) {
                return e.getCodigo();
            }
        };
    }

    private Map<String, Object> esperarSesionBloqueada() throws InterruptedException {
        String sql = """
            SELECT pid, wait_event_type, wait_event, state, left(query, 70) AS query
            FROM pg_stat_activity
            WHERE wait_event_type = 'Lock' AND query ILIKE '%from materias%'
            """;
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            var filas = jdbc.queryForList(sql);
            if (!filas.isEmpty()) return filas.get(0);
            Thread.sleep(50);   // sondeo con límite de tiempo, no una pausa para "dar tiempo"
        }
        return fail("Ninguna sesión de PostgreSQL quedó esperando el bloqueo de la materia");
    }

    private record Escenario(Long alumnoA, Long alumnoB, Long materia) { }

    private Escenario prepararMateriaConUnLugar() {
        int n = SECUENCIA.incrementAndGet();
        var a = alumnos.saveAndFlush(new AlumnoEntity(new Alumno(null, "C" + n + "A", "Ada", "ada" + n + "@u.mx", EstatusAlumno.ACTIVO)));
        var b = alumnos.saveAndFlush(new AlumnoEntity(new Alumno(null, "C" + n + "B", "Emmy", "emmy" + n + "@u.mx", EstatusAlumno.ACTIVO)));
        var m = materias.saveAndFlush(new MateriaEntity("CUPO" + n, "Materia de un lugar", 4, 1));
        return new Escenario(a.getId(), b.getId(), m.getId());
    }
}
