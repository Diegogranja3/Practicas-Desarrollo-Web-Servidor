package mx.edu.backendacademico.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AlumnoTest {
    @Test void bajaConservaIdentidadSinModificarElOriginal() {
        var alumno = new Alumno(1L, " a001 ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var baja = alumno.darDeBaja();
        // Comprueba tanto el resultado como la ausencia de efectos laterales.
        assertEquals("A001", baja.matricula());
        assertEquals(alumno.id(), baja.id());
        assertEquals(EstatusAlumno.BAJA, baja.estatus());
        assertEquals(EstatusAlumno.ACTIVO, alumno.estatus());
    }

    @Test void rechazaMatriculaVacia() {
        assertThrows(IllegalArgumentException.class, () ->
            new Alumno(null, " ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
    }

    @Test void materiaRechazaCreditosNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> new Materia(null, "M1", "Análisis", 0));
    }

    @Test void reactivarConservaIdentidadSinModificarElOriginal() {
        var baja = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.BAJA);
        var activo = baja.reactivar();
        // Cambia sólo el estatus; el valor original sigue en BAJA.
        assertEquals(EstatusAlumno.ACTIVO, activo.estatus());
        assertEquals(EstatusAlumno.BAJA, baja.estatus());
        assertEquals(baja.id(), activo.id());
        assertEquals(baja.matricula(), activo.matricula());
        assertEquals(baja.nombre(), activo.nombre());
        assertEquals(baja.correo(), activo.correo());
    }

    @Test void mismoIdConDistintoCorreoNoSonIgualesComoValor() {
        var antes = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var despues = new Alumno(1L, "A001", "Ada", "ada.lovelace@u.mx", EstatusAlumno.ACTIVO);
        // equals del record compara todos los componentes, no sólo el id.
        assertNotEquals(antes, despues);
        // Misma identidad académica: mismo id y misma matrícula normalizada.
        assertEquals(antes.id(), despues.id());
        assertEquals(antes.matricula(), despues.matricula());
    }
}
