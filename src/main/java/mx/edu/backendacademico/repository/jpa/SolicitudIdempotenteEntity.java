package mx.edu.backendacademico.repository.jpa;

// Reto U11: reserva de una clave de idempotencia y la respuesta que produjo.

import jakarta.persistence.*;
import java.time.LocalDateTime;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.dto.AlumnoResponse;

@Entity
@Table(name = "solicitudes_idempotentes")
public class SolicitudIdempotenteEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String usuario;
    private String clave;
    private String huella;
    private LocalDateTime creada;
    @Column(name = "alumno_id")
    private Long alumnoId;
    private String matricula;
    private String nombre;
    private String correo;
    @Enumerated(EnumType.STRING)
    private EstatusAlumno estatus;

    protected SolicitudIdempotenteEntity() { }

    public SolicitudIdempotenteEntity(String usuario, String clave, String huella, LocalDateTime creada) {
        this.usuario = usuario;
        this.clave = clave;
        this.huella = huella;
        this.creada = creada;
    }

    /** Guarda la respuesta original; la entidad está administrada, así que se escribe al confirmar. */
    public void registrarRespuesta(Alumno creado) {
        this.alumnoId = creado.id();
        this.matricula = creado.matricula();
        this.nombre = creado.nombre();
        this.correo = creado.correo();
        this.estatus = creado.estatus();
    }

    public AlumnoResponse respuesta() {
        return new AlumnoResponse(alumnoId, matricula, nombre, correo, estatus);
    }

    public String getClave() { return clave; }
    public String getHuella() { return huella; }
}
