package mx.edu.backendacademico.service;

// Reto U11: clave de idempotencia para crear alumnos, contra PostgreSQL real.

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import mx.edu.backendacademico.exception.IdempotenciaConflictoException;
import mx.edu.backendacademico.repository.jpa.AlumnoSpringDataRepository;
import mx.edu.backendacademico.repository.jpa.SolicitudIdempotenteRepository;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class IdempotenciaTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final AtomicInteger N = new AtomicInteger();

    @Autowired IdempotenciaService servicio;          // bean real: atraviesa las transacciones reales
    @Autowired AlumnoSpringDataRepository alumnos;
    @Autowired SolicitudIdempotenteRepository solicitudes;
    @Autowired AlumnoService alumnoService;

    @RepeatedTest(5)
    void dosSolicitudesSimultaneasConLaMismaClaveCreanUnSoloAlumno() throws Exception {
        int n = N.incrementAndGet();
        String clave = "clave-" + n, matricula = "I" + n;
        var r = enParalelo(
            () -> servicio.crearAlumno("docente", clave, matricula, "Ada Lovelace", "ada@u.mx"),
            () -> servicio.crearAlumno("docente", clave, matricula, "Ada Lovelace", "ada@u.mx"));

        var a = (IdempotenciaService.Resultado) r[0];
        var b = (IdempotenciaService.Resultado) r[1];
        assertEquals(a.alumno().id(), b.alumno().id(), "Ambas reciben el mismo alumno");
        assertEquals(a.alumno(), b.alumno(), "La repetición devuelve la misma representación");
        assertTrue(a.repetida() ^ b.repetida(), "Exactamente una es repetición");
        assertEquals(1, contarAlumnos(matricula));
        assertEquals(1, solicitudes.findAll().stream().filter(s -> s.getClave().equals(clave)).count());
    }

    @Test
    void mismaClaveConDistintoContenidoSeRechaza() {
        int n = N.incrementAndGet();
        String clave = "clave-" + n, matricula = "I" + n;
        servicio.crearAlumno("docente", clave, matricula, "Ada Lovelace", "ada@u.mx");
        var ex = assertThrows(IdempotenciaConflictoException.class,
            () -> servicio.crearAlumno("docente", clave, matricula, "Otra Persona", "ada@u.mx"));
        assertTrue(ex.getMessage().contains(clave));
        assertEquals(1, contarAlumnos(matricula));
    }

    @RepeatedTest(5)
    void mismaClaveYDistintoContenidoSimultaneosDejanUnAlumnoYUnRechazo() throws Exception {
        int n = N.incrementAndGet();
        String clave = "clave-" + n, matricula = "I" + n;
        var r = enParalelo(
            () -> servicio.crearAlumno("docente", clave, matricula, "Ada Lovelace", "ada@u.mx"),
            () -> servicio.crearAlumno("docente", clave, matricula, "Otra Persona", "otra@u.mx"));
        long creados = java.util.Arrays.stream(r).filter(x -> x instanceof IdempotenciaService.Resultado).count();
        long rechazos = java.util.Arrays.stream(r).filter(x -> x instanceof IdempotenciaConflictoException).count();
        assertEquals(1, creados, java.util.Arrays.toString(r));
        assertEquals(1, rechazos, java.util.Arrays.toString(r));
        assertEquals(1, contarAlumnos(matricula));
    }

    @Test
    void laClaveEsPorUsuario() {
        int n = N.incrementAndGet();
        String clave = "compartida-" + n;
        var a = servicio.crearAlumno("docente", clave, "I" + n + "A", "Ada Lovelace", "ada@u.mx");
        var b = servicio.crearAlumno("admin", clave, "I" + n + "B", "Emmy Noether", "emmy@u.mx");
        assertFalse(b.repetida());
        assertNotEquals(a.alumno().id(), b.alumno().id());
    }

    @Test
    void laRepeticionDevuelveLaRespuestaOriginalAunqueElAlumnoCambie() {
        int n = N.incrementAndGet();
        String clave = "clave-" + n;
        var original = servicio.crearAlumno("docente", clave, " i" + n + " ", "Ada Lovelace", "ada@u.mx");
        alumnoService.actualizar(original.alumno().id(), "Ada Byron", "ada.byron@u.mx");
        // " iN " y "IN" son el mismo contenido canónico.
        var repetida = servicio.crearAlumno("docente", clave, "I" + n, "Ada Lovelace", "ada@u.mx");
        assertTrue(repetida.repetida());
        assertEquals(original.alumno(), repetida.alumno());
        assertEquals("Ada Lovelace", repetida.alumno().nombre());
    }

    private long contarAlumnos(String matricula) {
        return alumnos.findAll().stream().filter(a -> a.toDomain().matricula().equals(matricula)).count();
    }

    /** Ejecuta dos operaciones a la vez; devuelve su resultado o la excepción que lanzaron. */
    private Object[] enParalelo(Callable<Object> uno, Callable<Object> dos) throws Exception {
        var listas = new CountDownLatch(2);
        var salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> envolver1 = envolver(uno, listas, salida), envolver2 = envolver(dos, listas, salida);
            Future<Object> f1 = pool.submit(envolver1), f2 = pool.submit(envolver2);
            assertTrue(listas.await(10, TimeUnit.SECONDS));
            salida.countDown();
            return new Object[] { f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS) };
        } finally {
            pool.shutdownNow();
        }
    }

    private static Callable<Object> envolver(Callable<Object> op, CountDownLatch listas, CountDownLatch salida) {
        return () -> {
            listas.countDown();
            salida.await();
            try { return op.call(); } catch (RuntimeException e) { return e; }
        };
    }
}
