package mx.edu.backendacademico.service;

// Reto U05: filtro opcional de estatus sobre la lista de alumnos.

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.repository.AlumnoRepositoryEnMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlumnoServiceFiltroTest {
    private AlumnoRepositoryEnMemoria repository;
    private AlumnoService service;

    @BeforeEach
    void preparar() {
        repository = new AlumnoRepositoryEnMemoria();
        service = new AlumnoService(repository);
        service.crear("A001", "Ada", "ada@u.mx");
        service.crear("A002", "Emmy", "emmy@u.mx");
        // La API todavía no expone la baja; el estado se prepara con la operación de dominio.
        var baja = service.crear("A003", "Grace", "grace@u.mx").darDeBaja();
        repository.guardar(baja);
    }

    @Test void sinFiltroConservaElContratoOriginal() {
        assertEquals(service.listar(), service.listar(null));
        assertEquals(3, service.listar(null).size());
    }

    @Test void filtraActivos() {
        assertEquals(List.of("A001", "A002"), matriculas(service.listar(EstatusAlumno.ACTIVO)));
    }

    @Test void filtraBajas() {
        assertEquals(List.of("A003"), matriculas(service.listar(EstatusAlumno.BAJA)));
    }

    @Test void consultarNoModificaElRepositorio() {
        var antes = repository.buscarTodos();
        service.listar(EstatusAlumno.ACTIVO);
        service.listar(EstatusAlumno.BAJA);
        service.listar(null);
        // Mismos alumnos, mismo orden y mismos valores: filtrar sólo produjo listas nuevas.
        assertEquals(antes, repository.buscarTodos());
        assertEquals(EstatusAlumno.BAJA, repository.buscarPorId(3L).orElseThrow().estatus());
    }

    private static List<String> matriculas(List<Alumno> alumnos) {
        return alumnos.stream().map(Alumno::matricula).toList();
    }
}
