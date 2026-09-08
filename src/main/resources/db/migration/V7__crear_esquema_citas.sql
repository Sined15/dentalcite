-- V7__crear_esquema_citas.sql
-- Esquema mínimo de la cita. Solo la estructura: el motor de disponibilidad
-- (HU-08), la reserva (HU-09) y la exclusión bajo concurrencia (HU-10) son del
-- Sprint 2. Se adelanta aquí porque las reglas RN-12 (no dar de baja un
-- tratamiento u odontólogo con citas activas) y RN-03 (no aplicar un bloqueo que
-- alcance citas activas) pertenecen al Sprint 1 y no son verificables sin ella:
-- TratamientoRepository.hasCitasActivas y OdontologoRepository.hasCitasActivas
-- ya consultaban esta tabla, de modo que la baja fallaba con un 500.

CREATE TABLE citas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo VARCHAR(20) NOT NULL UNIQUE,
    ficha_id UUID NOT NULL,
    odontologo_id UUID NOT NULL,
    tratamiento_id UUID NOT NULL,
    consultorio_id UUID NOT NULL,
    inicio TIMESTAMPTZ NOT NULL,
    fin TIMESTAMPTZ NOT NULL,
    -- RN-09: la cita nace confirmada y transita a uno de tres estados finales.
    estado VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA'
        CHECK (estado IN ('CONFIRMADA', 'ATENDIDA', 'NO_ASISTIO', 'CANCELADA')),
    motivo_cancelacion VARCHAR(255),
    creado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_cita_ficha FOREIGN KEY (ficha_id) REFERENCES fichas(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cita_odontologo FOREIGN KEY (odontologo_id) REFERENCES odontologos(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cita_tratamiento FOREIGN KEY (tratamiento_id) REFERENCES tratamientos(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cita_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorios(id) ON DELETE RESTRICT,
    CONSTRAINT cita_inicio_antes_de_fin CHECK (inicio < fin)
);

-- Índices para las consultas de cita activa que ejercen RN-12 y RN-03.
CREATE INDEX idx_citas_odontologo_estado ON citas (odontologo_id, estado);
CREATE INDEX idx_citas_consultorio_estado ON citas (consultorio_id, estado);
CREATE INDEX idx_citas_tratamiento_estado ON citas (tratamiento_id, estado);
CREATE INDEX idx_citas_rango ON citas (inicio, fin);
