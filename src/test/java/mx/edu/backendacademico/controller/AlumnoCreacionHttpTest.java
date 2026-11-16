package mx.edu.backendacademico.controller;

// Reto U09 (1/2): contrato HTTP de creación. Slice MVC con el servicio sustituido.

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlumnoController.class)
@AutoConfigureMockMvc(addFilters = false)
class AlumnoCreacionHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoService service;

    @Test void crearResponde201ConLocationDelRecursoCreado() throws Exception {
        // El id lo asigna el servicio; se usa 42 para que un Location fijo o inventado no pase por casualidad.
        when(service.crear("A042", "Ada Lovelace", "ada@u.mx"))
            .thenReturn(new Alumno(42L, "A042", "Ada Lovelace", "ada@u.mx", EstatusAlumno.ACTIVO));

        mvc.perform(post("/api/v1/alumnos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"matricula\":\"A042\",\"nombre\":\"Ada Lovelace\",\"correo\":\"ada@u.mx\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/alumnos/42"))
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.estatus").value("ACTIVO"));
        verify(service).crear("A042", "Ada Lovelace", "ada@u.mx");
    }
}
