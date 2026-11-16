package mx.edu.backendacademico.repository;

// Reto U09 (2/2): violación real de uk_alumnos_matricula en PostgreSQL, sin la comprobación previa del servicio.

import static org.junit.jupiter.api.Assertions.*;

import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.MatriculaDuplicadaException;
import mx.edu.backendacademico.repository.jpa.AlumnoSpringDataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class UnicidadPostgresTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired AlumnoRepository repository;          // con el perfil jpa es AlumnoJpaAdapter
    @Autowired AlumnoSpringDataRepository filas;

    @Test void laRestriccionUnicaDePostgresSeTraduceAMatriculaDuplicada() {
        assertInstanceOf(mx.edu.backendacademico.repository.jpa.AlumnoJpaAdapter.class,
            org.springframework.aop.support.AopUtils.isAopProxy(repository)
                ? org.springframework.test.util.AopTestUtils.getTargetObject(repository) : repository);

        repository.guardar(new Alumno(null, "U001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
        // Se llama al adaptador directamente: no hay existePorMatricula previo, así que el único que puede
        // rechazar el segundo INSERT es la restricción UNIQUE de V1 en PostgreSQL.
        var ex = assertThrows(MatriculaDuplicadaException.class, () ->
            repository.guardar(new Alumno(null, "U001", "Emmy", "emmy@u.mx", EstatusAlumno.ACTIVO)));
        assertTrue(ex.getMessage().contains("U001"), ex.getMessage());

        // La transacción del segundo intento se revirtió: sólo existe la primera fila.
        assertEquals(1, filas.findAll().stream().filter(a -> a.toDomain().matricula().equals("U001")).count());
    }
}
