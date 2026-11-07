-- Reto U07: el nombre no puede quedar en blanco.
-- Es una migración nueva: V1 ya está aplicada y su checksum forma parte del historial.
-- ALTER TABLE modifica la tabla existente; no la elimina ni la vuelve a crear, así que las filas se conservan.
-- PostgreSQL comprueba la restricción contra todas las filas actuales al agregarla.
ALTER TABLE alumnos
    ADD CONSTRAINT ck_nombre_no_vacio CHECK (LENGTH(TRIM(nombre)) > 0);
