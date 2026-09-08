-- V1__init.sql
-- Creación de extensiones necesarias
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==========================================
-- 1. CREACIÓN DE TABLAS BASE
-- ==========================================

-- Tabla de Usuarios (Módulo M1 - Acceso y Usuarios)
CREATE TABLE usuarios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correo VARCHAR(150) UNIQUE NOT NULL,
    contrasena_hash VARCHAR(255) NOT NULL, -- BCrypt coste 12
    rol VARCHAR(50) NOT NULL CHECK (rol IN ('PACIENTE', 'RECEPCIONISTA', 'ODONTOLOGO', 'ADMINISTRADOR')),
    activo BOOLEAN DEFAULT TRUE NOT NULL,
    tokens_validos_desde TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    creado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Tabla de Especialidades (Módulo M3 - Catálogo clínico)
CREATE TABLE especialidades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(100) UNIQUE NOT NULL,
    activa BOOLEAN DEFAULT TRUE NOT NULL
);

-- Tabla de Consultorios (Módulo M4 - Agenda y disponibilidad)
CREATE TABLE consultorios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(50) UNIQUE NOT NULL,
    inoperativo BOOLEAN DEFAULT FALSE NOT NULL
);

-- Tabla de Feriados Nacionales (Módulo M4 - Agenda y disponibilidad)
CREATE TABLE feriados (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fecha DATE UNIQUE NOT NULL,
    descripcion VARCHAR(150) NOT NULL
);

-- Tabla de Catálogo de Recomendaciones (Módulo M6 - Plan de tratamiento)
CREATE TABLE recomendaciones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    descripcion VARCHAR(255) UNIQUE NOT NULL,
    activa BOOLEAN DEFAULT TRUE NOT NULL
);

-- ==========================================
-- 2. INSERCIÓN DE DATOS SEMILLA (HU-01)
-- ==========================================

-- Nota: Todas las contraseñas hash a continuación corresponden a la clave en texto plano: 'Password123' (BCrypt coste 12)

-- A. Credenciales de demostración para los 4 roles
INSERT INTO usuarios (id, correo, contrasena_hash, rol) VALUES 
(gen_random_uuid(), 'admin@dentalcite.com', '$2a$12$uE.91Q5.3H6q.o4N3p5j5.cQnJ5U.UvKz.hV6r6b/C9o4wK1xX.2C', 'ADMINISTRADOR'),
(gen_random_uuid(), 'recepcion@dentalcite.com', '$2a$12$uE.91Q5.3H6q.o4N3p5j5.cQnJ5U.UvKz.hV6r6b/C9o4wK1xX.2C', 'RECEPCIONISTA'),
(gen_random_uuid(), 'dr.perez@dentalcite.com', '$2a$12$uE.91Q5.3H6q.o4N3p5j5.cQnJ5U.UvKz.hV6r6b/C9o4wK1xX.2C', 'ODONTOLOGO'),
(gen_random_uuid(), 'paciente@demo.com', '$2a$12$b1zGdXHTyG82YEG6dpBxuewk.W7IhxeySzk2SnMSnTu10kaJnINRq', 'PACIENTE');

-- B. Especialidades Odontológicas
INSERT INTO especialidades (nombre) VALUES 
('Odontología General'),
('Ortodoncia'),
('Endodoncia'),
('Periodoncia'),
('Odontopediatría');

-- C. Consultorios (El caso simulado tiene 3 consultorios)
INSERT INTO consultorios (nombre) VALUES 
('Consultorio 1'),
('Consultorio 2'),
('Consultorio 3');

-- D. Feriados (Datos referenciales para Lima, Perú en el segundo semestre de 2026)
INSERT INTO feriados (fecha, descripcion) VALUES 
('2026-08-30', 'Día de Santa Rosa de Lima'),
('2026-10-08', 'Combate de Angamos'),
('2026-11-01', 'Día de Todos los Santos'),
('2026-12-08', 'Día de la Inmaculada Concepción'),
('2026-12-25', 'Navidad');

-- E. Catálogo de Recomendaciones (Catálogo cerrado)
INSERT INTO recomendaciones (descripcion) VALUES 
('Evitar ingerir alimentos sólidos o masticar por 2 horas.'),
('No realizar enjuagues bruscos durante las primeras 24 horas.'),
('Tomar los analgésicos recetados en caso de dolor persistente.'),
('Aplicar hielo en la zona externa de la mejilla por 15 minutos en caso de inflamación.'),
('Mantener dieta blanda y evitar alimentos irritantes o muy calientes.'),
('Uso estricto de seda dental y cepillado suave en la zona tratada.');