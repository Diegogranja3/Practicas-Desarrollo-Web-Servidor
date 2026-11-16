package mx.edu.backendacademico.repository.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudIdempotenteRepository extends JpaRepository<SolicitudIdempotenteEntity, Long> {
    Optional<SolicitudIdempotenteEntity> findByUsuarioAndClave(String usuario, String clave);
}
