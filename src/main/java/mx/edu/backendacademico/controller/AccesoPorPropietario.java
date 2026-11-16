package mx.edu.backendacademico.controller;

// Reto U12: mejora de acceso por propietario sobre la ficha de un alumno.

import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import org.springframework.security.core.Authentication;

final class AccesoPorPropietario {
    private AccesoPorPropietario() { }

    /**
     * DOCENTE y ADMIN ven cualquier ficha. Una cuenta que sólo tiene ALUMNO ve únicamente la suya:
     * la cuenta se llama como su matrícula (a001 -> A001), un vínculo que fija el servidor.
     * Una ficha ajena responde igual que una inexistente (404) para no revelar qué ids existen.
     *
     * Se aplica DESPUÉS de obtener el alumno (posiblemente desde la caché). La caché guarda datos con clave
     * por id, iguales para todos; la decisión de acceso depende del usuario, así que nunca se guarda en caché.
     */
    static Alumno exigir(Alumno alumno, Authentication principal) {
        boolean personal = principal.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_DOCENTE") || a.getAuthority().equals("ROLE_ADMIN"));
        if (personal || alumno.matricula().equalsIgnoreCase(principal.getName())) return alumno;
        throw new AlumnoNoEncontradoException(alumno.id());
    }
}
