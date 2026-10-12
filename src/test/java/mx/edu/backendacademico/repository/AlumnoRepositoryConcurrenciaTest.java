package mx.edu.backendacademico.repository;

// Reto U04: dos tareas intentan registrar la misma matrícula al mismo tiempo.

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import mx.edu.backendacademico.exception.MatriculaDuplicadaException;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.RepeatedTest;

class AlumnoRepositoryConcurrenciaTest {
    // Se repite para observar varios entrelazados; aun así no prueba todos los posibles.
    @RepeatedTest(50)
    void mismaMatriculaEnParaleloDejaUnRegistroYUnConflicto() throws Exception {
        var repository = new AlumnoRepositoryEnMemoria();
        var service = new AlumnoService(repository);
        var listas = new CountDownLatch(2);
        var salida = new CountDownLatch(1);

        Callable<Boolean> registrar = () -> {
            listas.countDown();
            // Ambas tareas esperan la misma señal para competir por el mismo monitor.
            salida.await();
            try {
                service.crear("A001", "Ada", "ada@u.mx");
                return true;
            } catch (MatriculaDuplicadaException e) {
                return false;
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> primera = pool.submit(registrar);
            Future<Boolean> segunda = pool.submit(registrar);
            assertTrue(listas.await(5, TimeUnit.SECONDS), "Las tareas no llegaron al punto de partida");
            salida.countDown();

            // get() espera la terminación de cada tarea: sin pausas arbitrarias.
            List<Boolean> resultados = List.of(resultado(primera), resultado(segunda));
            assertEquals(1, resultados.stream().filter(exito -> exito).count(), "Exactamente un registro");
            assertEquals(1, resultados.stream().filter(exito -> !exito).count(), "Exactamente un conflicto");
            assertEquals(1, repository.buscarTodos().size());
            assertEquals("A001", repository.buscarTodos().get(0).matricula());
        } finally {
            pool.shutdownNow();
        }
    }

    private static Boolean resultado(Future<Boolean> tarea) throws InterruptedException, ExecutionException {
        try {
            return tarea.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("La tarea no terminó", e);
        }
    }
}
