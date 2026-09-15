-- V25__enlace_de_citas_con_sesiones.sql
-- Una cita atendida ocupa una sesion del plan activo de su paciente y su
-- tratamiento: al registrar su resultado, o al crear el plan si la cita ya
-- estaba atendida. La sesion guarda que cita la ocupa.
--
-- RESTRICT y no SET NULL: una sesion atendida que perdiera su cita dejaria de
-- explicar por que cuenta como atendida, y la restriccion de abajo tampoco lo
-- admitiria.
ALTER TABLE plan_sesiones
    ADD COLUMN cita_id UUID REFERENCES citas(id) ON DELETE RESTRICT;

-- Una cita ocupa como mucho una sesion; que una sesion tenga como mucho una cita
-- ya lo da la propia columna. Se garantiza aqui y no solo en el servicio para
-- que ningun camino de escritura pueda contar dos veces la misma consulta.
ALTER TABLE plan_sesiones
    ADD CONSTRAINT ux_sesion_cita UNIQUE (cita_id);

-- El estado y el enlace no pueden contradecirse: pendiente es exactamente «sin
-- cita», y una sesion atendida o cerrada tiene que decir cual la ocupo.
ALTER TABLE plan_sesiones
    ADD CONSTRAINT sesion_pendiente_sin_cita
    CHECK ((estado = 'PENDIENTE' AND cita_id IS NULL)
        OR (estado <> 'PENDIENTE' AND cita_id IS NOT NULL));

-- No hay columna de avance, a proposito: cuantas sesiones van atendidas se
-- cuenta sobre estas filas cada vez que se pregunta, asi que no existe un dato
-- que pueda quedarse desfasado respecto de las citas.
