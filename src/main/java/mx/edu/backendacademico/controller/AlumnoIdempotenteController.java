package mx.edu.backendacademico.controller;

// Reto U11: POST /api/v1/alumnos con la cabecera Idempotency-Key.
// Sin la cabecera, MVC elige el AlumnoController original: el contrato existente no cambia.

import jakarta.validation.Valid;
import java.net.URI;
import mx.edu.backendacademico.dto.AlumnoResponse;
import mx.edu.backendacademico.dto.CrearAlumnoRequest;
import mx.edu.backendacademico.service.IdempotenciaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/alumnos")
public class AlumnoIdempotenteController {
    static final String CABECERA = "Idempotency-Key";
    private final IdempotenciaService service;

    public AlumnoIdempotenteController(IdempotenciaService service) { this.service = service; }

    @PostMapping(headers = CABECERA)
    public ResponseEntity<AlumnoResponse> crear(@RequestHeader(CABECERA) String clave,
                                                @RequestBody @Valid CrearAlumnoRequest request,
                                                Authentication principal) {
        if (clave.isBlank() || clave.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key debe tener entre 1 y 100 caracteres");
        }
        // Las claves son por usuario autenticado: la misma clave de dos clientes no choca.
        var r = service.crearAlumno(principal.getName(), clave.strip(), request.matricula(), request.nombre(), request.correo());
        var respuesta = ResponseEntity.created(URI.create("/api/v1/alumnos/" + r.alumno().id()));
        if (r.repetida()) respuesta.header("Idempotency-Replayed", "true");
        return respuesta.body(r.alumno());
    }
}
