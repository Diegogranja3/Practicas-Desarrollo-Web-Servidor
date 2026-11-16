package mx.edu.backendacademico.controller;

// Reto U10: el controlador sólo entrega el principal; la regla de acceso vive en ExpedienteService.

import mx.edu.backendacademico.service.ExpedienteService;
import mx.edu.backendacademico.service.ExpedienteService.Expediente;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expediente")
public class ExpedienteController {
    private final ExpedienteService service;

    public ExpedienteController(ExpedienteService service) { this.service = service; }

    // Sin parámetros de identidad: quién es el alumno lo decide la autenticación.
    @GetMapping
    public Expediente mio(Authentication principal) { return service.delPrincipal(principal); }

    @GetMapping("/{id}")
    public Expediente porId(@PathVariable Long id, Authentication principal) { return service.porId(id, principal); }
}
