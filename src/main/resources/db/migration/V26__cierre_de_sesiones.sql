-- V26__cierre_de_sesiones.sql
-- Al terminar una sesion el odontologo deja por escrito que cuidados debe seguir
-- el paciente y cuando volver. Eso es lo que lleva la sesion a CERRADA, el tercer
-- estado que V19 declaro y que hasta ahora no alcanzaba ningun camino.

ALTER TABLE plan_sesiones
    ADD COLUMN proximo_control DATE,
    ADD COLUMN observacion VARCHAR(300);

-- El estado y sus datos no pueden contradecirse, igual que sesion_pendiente_sin_cita
-- ata el estado con la cita: una sesion cerrada dice cuando es el proximo control, y
-- una que todavia no lo esta no tiene ni fecha ni observacion que ensenar.
ALTER TABLE plan_sesiones
    ADD CONSTRAINT sesion_cerrada_con_proximo_control
    CHECK ((estado = 'CERRADA' AND proximo_control IS NOT NULL)
        OR (estado <> 'CERRADA' AND proximo_control IS NULL AND observacion IS NULL));

-- Las recomendaciones son filas y no un texto libre: salen del catalogo cerrado que
-- siembra V1, y una sesion puede llevar varias.
CREATE TABLE plan_sesion_recomendaciones (
    sesion_id UUID NOT NULL REFERENCES plan_sesiones(id) ON DELETE CASCADE,
    -- RESTRICT, al contrario que el de arriba: retirar una recomendacion del catalogo
    -- no puede borrar la que ya se le indico a un paciente. Del catalogo se sale
    -- marcandola inactiva, que es para lo que esta `recomendaciones.activa`.
    recomendacion_id UUID NOT NULL REFERENCES recomendaciones(id) ON DELETE RESTRICT,
    PRIMARY KEY (sesion_id, recomendacion_id)
);

-- La clave primaria ya resuelve «las recomendaciones de esta sesion»; este indice es
-- el del sentido contrario, que es el que necesita el RESTRICT al dar de baja una.
CREATE INDEX idx_plan_sesion_recomendaciones_recomendacion
    ON plan_sesion_recomendaciones (recomendacion_id);

-- Lo que esta tabla NO puede exigir, a proposito: que una sesion cerrada tenga al
-- menos una recomendacion. Una tabla padre no puede obligar a que existan filas
-- hijas, asi que ese rechazo lo sostiene el servicio y no hay que buscarlo aqui.
