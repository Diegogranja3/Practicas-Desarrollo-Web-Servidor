package mx.edu.backendacademico.dto;

// Define exclusivamente los campos que el cliente puede enviar al crear.

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearAlumnoRequest(
    // Reto U06: sin espacios internos. Los externos se permiten porque el dominio los recorta;
    // la cadena vacía también pasa el patrón para que sólo @NotBlank la reporte.
    @NotBlank @Size(max = 30)
    @Pattern(regexp = "\\s*\\S*\\s*", message = "no debe contener espacios internos") String matricula,
@NotBlank @Size(min = 3, max = 120) String nombre,
@NotBlank @Email @Size(max = 160) String correo
) {}
