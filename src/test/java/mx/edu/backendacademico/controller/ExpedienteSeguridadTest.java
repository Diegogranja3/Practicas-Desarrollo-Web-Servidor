package mx.edu.backendacademico.controller;

// Reto U10: acceso por objeto al expediente con la cadena real de filtros y la regla real de ExpedienteService.

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;
import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.dto.InscripcionResponse;
import mx.edu.backendacademico.dto.PaginaResponse;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import mx.edu.backendacademico.service.AlumnoService;
import mx.edu.backendacademico.service.ExpedienteService;
import mx.edu.backendacademico.service.InscripcionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExpedienteController.class)
@Import({SecurityConfig.class, ExpedienteService.class})
class ExpedienteSeguridadTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoService alumnos;
    @MockitoBean InscripcionService inscripciones;

    private static final Alumno A001 = new Alumno(1L, "A001", "Ada Lovelace", "ada@u.mx", EstatusAlumno.ACTIVO);
    private static final Alumno A010 = new Alumno(4L, "A010", "Grace Hopper", "grace@u.mx", EstatusAlumno.ACTIVO);

    @BeforeEach
    void datos() {
        when(alumnos.buscarPorMatricula("a001")).thenReturn(Optional.of(A001));
        when(alumnos.buscar(1L)).thenReturn(A001);
        when(alumnos.buscar(4L)).thenReturn(A010);
        when(alumnos.buscar(999L)).thenThrow(new AlumnoNoEncontradoException(999L));
        when(inscripciones.historial(anyLong(), any())).thenAnswer(i -> new PaginaResponse<InscripcionResponse>(
            List.of(new InscripcionResponse(9L, 1L, "Análisis matemático", null)), 0, 50, 1, 1));
    }

    @Test void propietarioVeSuExpediente() throws Exception {
        mvc.perform(get("/api/v1/expediente").with(httpBasic("a001", "a001pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alumno.matricula").value("A001"))
            .andExpect(jsonPath("$.inscripciones[0].materia").value("Análisis matemático"));
    }

    @Test void unParametroDeUsuarioNoCambiaLaIdentidad() throws Exception {
        mvc.perform(get("/api/v1/expediente").param("usuario", "a010").with(httpBasic("a001", "a001pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alumno.matricula").value("A001"));
        verify(alumnos, never()).buscarPorMatricula("a010");
    }

    @Test void propietarioVeSuExpedientePorId() throws Exception {
        mvc.perform(get("/api/v1/expediente/1").with(httpBasic("a001", "a001pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alumno.id").value(1));
    }

    @Test void usuarioAjenoRecibe404SinConsultarInscripciones() throws Exception {
        mvc.perform(get("/api/v1/expediente/4").with(httpBasic("a001", "a001pass")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("ALUMNO_NO_ENCONTRADO"))
            .andExpect(jsonPath("$.detail").value("No existe el alumno con id 4"));
        verify(inscripciones, never()).historial(anyLong(), any());
    }

    @Test void ajenoEInexistenteSonIndistinguibles() throws Exception {
        mvc.perform(get("/api/v1/expediente/999").with(httpBasic("a001", "a001pass")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("ALUMNO_NO_ENCONTRADO"));
    }

    @Test void administradorVeCualquierExpediente() throws Exception {
        mvc.perform(get("/api/v1/expediente/4").with(httpBasic("admin", "admin123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alumno.matricula").value("A010"));
    }

    @Test void docenteNoTieneAccesoAlExpediente() throws Exception {
        mvc.perform(get("/api/v1/expediente/4").with(httpBasic("docente", "docente123")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(alumnos, inscripciones);
    }

    @Test void sinCredencialesRecibe401() throws Exception {
        mvc.perform(get("/api/v1/expediente")).andExpect(status().isUnauthorized());
    }

    @Test void administradorNoTieneMiExpediente() throws Exception {
        mvc.perform(get("/api/v1/expediente").with(httpBasic("admin", "admin123")))
            .andExpect(status().isForbidden());
    }
}
