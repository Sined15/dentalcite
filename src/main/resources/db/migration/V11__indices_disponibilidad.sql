-- V11__indices_disponibilidad.sql
-- Solo indices: el motor de disponibilidad (HU-08) no cambia el esquema, pero si
-- las consultas que se emiten en caliente.
--
-- `bloqueos` no tenia ningun indice por rango y el motor la recorre en cada
-- calculo para descontar los tramos bloqueados (RN-02, RN-03).
CREATE INDEX idx_bloqueos_rango ON bloqueos (fecha_inicio, fecha_fin);

-- `citas` ya tiene idx_citas_rango (inicio, fin), pero la consulta del motor
-- filtra primero por estado: solo la cita activa ocupa agenda (RN-09).
CREATE INDEX idx_citas_estado_inicio ON citas (estado, inicio);
