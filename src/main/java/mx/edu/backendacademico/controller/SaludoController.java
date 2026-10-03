package mx.edu.backendacademico.controller;

// Asocia una ruta HTTP con un método y entrega datos para serialización JSON.

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SaludoController {
    // Reto U02: Spring MVC resuelve "nombre" desde la query string antes de invocar el método.
    @GetMapping("/saludo")
    public Map<String, String> saludar(@RequestParam(defaultValue = "Mundo") String nombre) {
        // Jackson serializa este valor al salir del método; no se construye JSON por concatenación.
        return Map.of("mensaje", "Hola " + nombre);
    }
}
