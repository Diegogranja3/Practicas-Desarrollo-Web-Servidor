-- Reto U11: claves de idempotencia para la creación de alumnos.
-- La restricción única (usuario, clave) es el punto de exclusión entre solicitudes simultáneas:
-- un segundo INSERT con la misma pareja espera a que la primera transacción termine y, si confirmó, falla.
CREATE TABLE solicitudes_idempotentes (
    id BIGSERIAL PRIMARY KEY,
    usuario VARCHAR(100) NOT NULL,
    clave VARCHAR(100) NOT NULL,
    huella VARCHAR(64) NOT NULL,            -- SHA-256 en hexadecimal del contenido canónico
    creada TIMESTAMP NOT NULL,
    -- Respuesta original. Se escribe en la misma transacción que la reserva, así que una fila confirmada siempre la tiene.
    alumno_id BIGINT,
    matricula VARCHAR(30),
    nombre VARCHAR(120),
    correo VARCHAR(160),
    estatus VARCHAR(20),
    CONSTRAINT uk_solicitud_idempotente UNIQUE (usuario, clave),
    CONSTRAINT ck_clave_no_vacia CHECK (LENGTH(TRIM(clave)) > 0)
);
