package mx.edu.backendacademico.service;

// Reto U10: expediente académico con regla de acceso por objeto.

import java.util.List;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.dto.AlumnoMapper;
import mx.edu.backendacademico.dto.AlumnoResponse;
import mx.edu.backendacademico.dto.InscripcionResponse;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExpedienteService {
    private final AlumnoService alumnos;
    private final InscripcionService inscripciones;

    public ExpedienteService(AlumnoService alumnos, InscripcionService inscripciones) {
        this.alumnos = alumnos;
        this.inscripciones = inscripciones;
    }

    public record Expediente(AlumnoResponse alumno, List<InscripcionResponse> inscripciones) { }

    /** "Mi expediente": la identidad es el principal autenticado; no hay parámetro que la sustituya. */
    public Expediente delPrincipal(Authentication principal) {
        var alumno = alumnos.buscarPorMatricula(principal.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La cuenta no tiene un expediente asociado"));
        return armar(alumno);
    }

    /** Expediente por id: ADMIN ve cualquiera; cualquier otra cuenta, sólo el propio. */
    public Expediente porId(Long id, Authentication principal) {
        var alumno = alumnos.buscar(id);   // inexistente -> 404
        if (!esAdmin(principal) && !esPropietario(alumno, principal)) {
            // Mismo 404 que si no existiera: no se revela qué ids pertenecen a otros alumnos.
            throw new AlumnoNoEncontradoException(id);
        }
        return armar(alumno);
    }

    private static boolean esAdmin(Authentication principal) {
        return principal.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private static boolean esPropietario(Alumno alumno, Authentication principal) {
        // La cuenta de un alumno se llama como su matrícula (a001 -> A001); el vínculo lo fija el servidor.
        return alumno.matricula().equalsIgnoreCase(principal.getName());
    }

    private Expediente armar(Alumno alumno) {
        var historial = inscripciones.historial(alumno.id(), PageRequest.of(0, 50, Sort.by("fecha").and(Sort.by("id"))));
        return new Expediente(AlumnoMapper.toResponse(alumno), historial.contenido());
    }
}
