package mx.edu.backendacademico.controller;

// Reto U12: mejora de acceso por propietario, con la cadena real de filtros.

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlumnoController.class)
@Import(SecurityConfig.class)
class AccesoPorPropietarioTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoService service;

    @BeforeEach
    void datos() {
        when(service.buscar(1L)).thenReturn(new Alumno(1L, "A001", "Ada Lovelace", "ada@u.mx", EstatusAlumno.ACTIVO));
        when(service.buscar(2L)).thenReturn(new Alumno(2L, "A002", "Emmy Noether", "emmy@u.mx", EstatusAlumno.ACTIVO));
        when(service.buscar(999L)).thenThrow(new AlumnoNoEncontradoException(999L));
    }

    @Test void propietarioVeSuFicha() throws Exception {
        mvc.perform(get("/api/v1/alumnos/1").with(httpBasic("a001", "a001pass")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.matricula").value("A001"));
    }

    @Test void fichaAjenaRespondeComoInexistente() throws Exception {
        mvc.perform(get("/api/v1/alumnos/2").with(httpBasic("a001", "a001pass")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("ALUMNO_NO_ENCONTRADO"));
        mvc.perform(get("/api/v1/alumnos/999").with(httpBasic("a001", "a001pass")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("ALUMNO_NO_ENCONTRADO"));
    }

    @Test void alumnoNoPuedeListarATodos() throws Exception {
        mvc.perform(get("/api/v1/alumnos").with(httpBasic("a001", "a001pass"))).andExpect(status().isForbidden());
        verify(service, never()).listar();
    }

    @Test void docenteYAdminVenCualquierFicha() throws Exception {
        mvc.perform(get("/api/v1/alumnos/2").with(httpBasic("docente", "docente123"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/alumnos/2").with(httpBasic("admin", "admin123"))).andExpect(status().isOk());
    }
}
