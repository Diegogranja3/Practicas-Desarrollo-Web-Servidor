package mx.edu.backendacademico.repository;

// Expresa el contrato de almacenamiento sin imponer una tecnología a los servicios.

import java.util.List;
import java.util.Optional;
import mx.edu.backendacademico.domain.Alumno;

public interface AlumnoRepository {
    Alumno guardar(Alumno alumno);
    List<Alumno> buscarTodos();
    Optional<Alumno> buscarPorId(Long id);
    boolean existePorMatricula(String matricula);
    // Reto U10: localiza el expediente a partir de la matrícula vinculada a la cuenta autenticada.
    Optional<Alumno> buscarPorMatricula(String matriculaCanonica);
    void eliminarPorId(Long id);
}
