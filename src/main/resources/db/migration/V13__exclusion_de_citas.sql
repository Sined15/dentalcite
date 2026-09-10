-- V13__exclusion_de_citas.sql
-- HU-10 / RNF-12: «el solapamiento y la unicidad se garantizaran con
-- restricciones de integridad en la base de datos; la aplicacion y Redis no
-- seran la barrera unica».
--
-- Hasta aqui la reserva era comprobar-y-actuar: HU-09 miraba la disponibilidad y
-- despues insertaba, y entre las dos cosas cabia otra reserva. El bloqueo
-- distribuido de Redis evita la carrera en el caso normal, pero es una capa de
-- rendimiento, no una garantia: si Redis no responde, esta restriccion es la que
-- sigue impidiendo la doble reserva.
--
-- Si esta migracion falla, la base ya contiene citas CONFIRMADA solapadas. Para
-- verlas:
--
--   SELECT a.codigo, b.codigo, a.inicio, a.fin
--   FROM citas a JOIN citas b
--     ON a.id < b.id
--    AND a.estado = 'CONFIRMADA' AND b.estado = 'CONFIRMADA'
--    AND tstzrange(a.inicio, a.fin) && tstzrange(b.inicio, b.fin)
--    AND (a.odontologo_id = b.odontologo_id OR a.consultorio_id = b.consultorio_id);
--
-- RN-12 obliga a resolverlas cancelandolas con motivo, no borrando filas.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- `tstzrange` es semiabierto [) por defecto, que es exactamente el criterio de
-- solapamiento que usa el resto del sistema: una cita que acaba a las 10:00 y
-- otra que empieza a las 10:00 no chocan.
--
-- La restriccion es parcial sobre CONFIRMADA: cancelar libera la franja en el
-- acto, que es lo que HU-11 y HU-15 necesitan. RN-09 define «activa» como la
-- confirmada cuya hora de fin no ha pasado, pero una restriccion no puede llamar
-- a now(); cubrir toda CONFIRMADA es mas estricto y no estorba, porque RN-05
-- impide reservar en el pasado.

-- RN-01: un odontologo no puede tener dos citas activas solapadas.
ALTER TABLE citas ADD CONSTRAINT citas_sin_solape_odontologo
    EXCLUDE USING gist (odontologo_id WITH =, tstzrange(inicio, fin) WITH &&)
    WHERE (estado = 'CONFIRMADA');

-- RN-02: un consultorio no puede alojar dos citas activas solapadas.
ALTER TABLE citas ADD CONSTRAINT citas_sin_solape_consultorio
    EXCLUDE USING gist (consultorio_id WITH =, tstzrange(inicio, fin) WITH &&)
    WHERE (estado = 'CONFIRMADA');
