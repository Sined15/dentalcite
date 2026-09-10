-- V17__ficha_alergias_y_busqueda.sql
-- HU-13 · RF-07, RF-08 · RNF-02.
--
-- La ficha no guardaba las alergias, que es el dato que RF-08 nombra
-- explicitamente y la razon por la que un odontologo abre la ficha de alguien a
-- quien va a atender. HU-12 lo dejo fuera a proposito: el criterio que lo pide
-- es de esta historia.

ALTER TABLE fichas ADD COLUMN alergias TEXT;

-- RNF-02: «la consulta de ficha respondera con p95 <= 1 s». La unica que hay es
-- uq_ficha_documento, que es (tipo_documento, documento): buscar por el numero
-- suelto no puede usarla, porque no es su columna principal.
CREATE INDEX ix_fichas_documento ON fichas (documento);

-- `numero_historia` ya esta indexado por su restriccion UNIQUE.

-- Busqueda por apellido. `varchar_pattern_ops` es lo que permite que un LIKE de
-- prefijo use el indice; sin el, el planificador recorre la tabla entera aunque
-- el indice exista.
CREATE INDEX ix_fichas_apellidos ON fichas (lower(apellidos) varchar_pattern_ops);

-- RNF-11: sin una alergia sembrada, la ficha de la demostracion ensena el campo
-- vacio y no se distingue de uno que no funciona. La de la paciente demo, que es
-- la unica ficha sembrada que no pertenece a un odontologo.
UPDATE fichas SET alergias = 'Alergia a la penicilina. Intolerancia al latex.'
    WHERE documento = '40987654';
