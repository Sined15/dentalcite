-- V4__crear_esquema_catalogo.sql
-- Modificación de la tabla de Especialidades (creada en V1)
ALTER TABLE especialidades RENAME COLUMN activa TO activo;
ALTER TABLE especialidades ADD COLUMN descripcion TEXT;
ALTER TABLE especialidades ADD COLUMN creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE especialidades ADD COLUMN actualizado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Creación de la tabla de Tratamientos (Catálogo Clínico)
CREATE TABLE tratamientos (
    id UUID PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL,
    descripcion TEXT,
    duracion_minutos INT NOT NULL,
    especialidad_id UUID NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tratamiento_especialidad FOREIGN KEY (especialidad_id) REFERENCES especialidades(id) ON DELETE RESTRICT,
    CONSTRAINT chk_tratamiento_duracion CHECK (duracion_minutos >= 15 AND duracion_minutos <= 240)
);

-- Creación de la tabla de Odontólogos (Personal Médico)
CREATE TABLE odontologos (
    id UUID PRIMARY KEY,
    cop VARCHAR(20) NOT NULL UNIQUE,
    nombres VARCHAR(100) NOT NULL,
    apellidos VARCHAR(100) NOT NULL,
    ficha_id UUID NOT NULL UNIQUE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_odontologo_ficha FOREIGN KEY (ficha_id) REFERENCES fichas(id) ON DELETE RESTRICT
);

-- Tabla intermedia para la relación Muchos a Muchos entre Odontólogos y Especialidades
CREATE TABLE odontologo_especialidad (
    odontologo_id UUID NOT NULL,
    especialidad_id UUID NOT NULL,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (odontologo_id, especialidad_id),
    CONSTRAINT fk_oe_odontologo FOREIGN KEY (odontologo_id) REFERENCES odontologos(id) ON DELETE CASCADE,
    CONSTRAINT fk_oe_especialidad FOREIGN KEY (especialidad_id) REFERENCES especialidades(id) ON DELETE RESTRICT
);
