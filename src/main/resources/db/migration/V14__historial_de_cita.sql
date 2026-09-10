-- V14__historial_de_cita.sql
-- HU-11 / RF-21: «mantener la maquina de estados de la cita y registrar cada
-- transicion con fecha, usuario y motivo».
--
-- La bitacora vive en su propia tabla y no en columnas de `citas` porque una
-- cita tiene tantas transiciones como estados atraviese: confirmada -> cancelada
-- hoy, y confirmada -> atendida o no asisitio cuando llegue HU-16. Guardar solo
-- la ultima —que es lo que hace `citas.motivo_cancelacion`— basta para mostrar
-- el motivo, pero no para responder «quien y cuando», que es justo lo que
-- RF-21 pide.
--
-- RN-12 dice que ningun registro de citas se elimina fisicamente, y quien lo
-- garantiza es la aplicacion: no hay ninguna operacion que borre una cita, y
-- cancelar cambia el estado de la fila conservandola intacta.
--
-- Por eso las dos claves foraneas no se comportan igual:
--
--   * `cita_id` es CASCADE. La bitacora es parte de la cita y no tiene
--     existencia propia: una transicion de una cita que no existe no significa
--     nada. Poner RESTRICT aqui no defenderia RN-12 —que la aplicacion ya
--     respeta— y solo dejaria filas huerfanas imposibles de retirar.
--   * `usuario_id` es RESTRICT. Esta si es la garantia que importa: sin ella,
--     dar de baja una cuenta vaciaria de golpe el «quien» de todas las
--     transiciones que esa persona ordeno, que es justo lo que una bitacora de
--     auditoria no puede permitir.

CREATE TABLE citas_historial (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cita_id UUID NOT NULL,
    -- Admite nulo para una transicion sin estado previo. Hoy no la escribe
    -- nadie: el alta de la cita NO se registra aqui, porque la fila hija bloquea
    -- la cita recien insertada hasta el commit y eso provocaba interbloqueos en
    -- el camino disputado de HU-10. Cuando nacio la cita ya lo dice
    -- `citas.creado_en`; lo que no se puede deducir es quien ejercio una
    -- transicion sobre ella, y para eso existe esta tabla.
    estado_anterior VARCHAR(20)
        CHECK (estado_anterior IS NULL
               OR estado_anterior IN ('CONFIRMADA', 'ATENDIDA', 'NO_ASISTIO', 'CANCELADA')),
    estado_nuevo VARCHAR(20) NOT NULL
        CHECK (estado_nuevo IN ('CONFIRMADA', 'ATENDIDA', 'NO_ASISTIO', 'CANCELADA')),
    -- Obligatorio al cancelar (RF-20), pero la columna admite nulo porque no toda
    -- transicion futura lo tendra. Quien exige el motivo es el servicio de
    -- cancelacion, que es donde la regla se puede enunciar por transicion.
    motivo VARCHAR(255),
    -- Nulo cuando la transicion no la origina una persona identificada.
    usuario_id UUID,
    ocurrido_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_historial_cita FOREIGN KEY (cita_id)
        REFERENCES citas(id) ON DELETE CASCADE,
    CONSTRAINT fk_historial_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios(id) ON DELETE RESTRICT
);

-- La bitacora se lee siempre por cita y en orden cronologico.
CREATE INDEX idx_historial_cita ON citas_historial (cita_id, ocurrido_en);

-- RF-18: la agenda del dia se consulta por rango de fechas y se filtra por
-- odontologo y estado. `idx_citas_estado_inicio` (V11) ya sirve al filtro por
-- estado; este cubre el caso de recepcion filtrando un odontologo concreto.
CREATE INDEX idx_citas_odontologo_inicio ON citas (odontologo_id, inicio);
