-- V5__crear_esquema_horarios.sql

-- Tabla de Horarios de Atención (Módulo M4 - Agenda y disponibilidad)
CREATE TABLE horarios_atencion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    odontologo_id UUID NOT NULL REFERENCES odontologos(id) ON DELETE CASCADE,
    dia_semana INTEGER NOT NULL CHECK (dia_semana BETWEEN 1 AND 7), -- 1 = Lunes, 7 = Domingo
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    creado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT horario_inicio_antes_de_fin CHECK (hora_inicio < hora_fin)
);

-- Tabla de Bloqueos (Módulo M4 - Agenda y disponibilidad)
-- Puede ser un bloqueo para un odontólogo, un consultorio o ambos (aunque usualmente es uno a la vez).
CREATE TABLE bloqueos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    odontologo_id UUID REFERENCES odontologos(id) ON DELETE CASCADE,
    consultorio_id UUID REFERENCES consultorios(id) ON DELETE CASCADE,
    motivo VARCHAR(255) NOT NULL,
    fecha_inicio TIMESTAMPTZ NOT NULL,
    fecha_fin TIMESTAMPTZ NOT NULL,
    creado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT bloqueo_al_menos_uno CHECK (odontologo_id IS NOT NULL OR consultorio_id IS NOT NULL),
    CONSTRAINT bloqueo_inicio_antes_de_fin CHECK (fecha_inicio < fecha_fin)
);
