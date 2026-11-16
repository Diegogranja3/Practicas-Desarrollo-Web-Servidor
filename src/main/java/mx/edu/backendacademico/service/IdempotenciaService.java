package mx.edu.backendacademico.service;

// Reto U11: creación de alumnos con clave de idempotencia. La exclusión la da PostgreSQL, no la memoria de la JVM.

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import mx.edu.backendacademico.dto.AlumnoMapper;
import mx.edu.backendacademico.dto.AlumnoResponse;
import mx.edu.backendacademico.exception.IdempotenciaConflictoException;
import mx.edu.backendacademico.repository.jpa.SolicitudIdempotenteEntity;
import mx.edu.backendacademico.repository.jpa.SolicitudIdempotenteRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IdempotenciaService {
    private final AlumnoService alumnos;
    private final SolicitudIdempotenteRepository solicitudes;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public IdempotenciaService(AlumnoService alumnos, SolicitudIdempotenteRepository solicitudes,
                               PlatformTransactionManager transacciones, Clock reloj) {
        this.alumnos = alumnos;
        this.solicitudes = solicitudes;
        this.transaccion = new TransactionTemplate(transacciones);
        this.reloj = reloj;
    }

    public record Resultado(AlumnoResponse alumno, boolean repetida) { }

    public Resultado crearAlumno(String usuario, String clave, String matricula, String nombre, String correo) {
        String huella = huella(matricula, nombre, correo);
        try {
            // Reserva, efecto y respuesta en UNA transacción: se confirman o se revierten juntos.
            return transaccion.execute(estado -> {
                // 1. Reserva. Si otra transacción reservó la misma (usuario, clave) y no ha terminado,
                //    PostgreSQL detiene este INSERT hasta su commit o rollback.
                var solicitud = solicitudes.saveAndFlush(
                    new SolicitudIdempotenteEntity(usuario, clave, huella, LocalDateTime.now(reloj)));
                // 2. Efecto: AlumnoService.crear se une a esta transacción (propagación REQUIRED).
                var creado = alumnos.crear(matricula, nombre, correo);
                // 3. Respuesta original, guardada junto con la reserva.
                solicitud.registrarRespuesta(creado);
                return new Resultado(AlumnoMapper.toResponse(creado), false);
            });
        } catch (DataIntegrityViolationException ex) {
            if (!violaRestriccion(ex, "uk_solicitud_idempotente")) throw ex;
            // La clave ya estaba confirmada: esta consulta corre en otra transacción y ve la fila completa.
            var previa = solicitudes.findByUsuarioAndClave(usuario, clave).orElseThrow(() -> ex);
            if (!previa.getHuella().equals(huella)) throw new IdempotenciaConflictoException(clave);
            return new Resultado(previa.respuesta(), true);
        }
    }

    /** SHA-256 del contenido en la forma canónica que guarda el dominio. */
    static String huella(String matricula, String nombre, String correo) {
        String canonico = String.join("\n",
            matricula.strip().toUpperCase(Locale.ROOT), nombre.strip(), correo.strip());
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static boolean violaRestriccion(Throwable ex, String nombre) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException r && nombre.equalsIgnoreCase(r.getConstraintName())) return true;
        }
        return false;
    }
}
