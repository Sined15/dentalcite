-- V3__agregar_requiere_cambio_password.sql
ALTER TABLE usuarios ADD COLUMN requiere_cambio_password BOOLEAN DEFAULT FALSE NOT NULL;
