-- V6__agregar_control_bloqueo_login.sql
-- RNF-05: persistir el conteo de intentos fallidos y el bloqueo temporal en
-- PostgreSQL, para que el bloqueo de fuerza bruta siga vigente aunque Redis
-- (usado como caché rápida) no esté disponible.
ALTER TABLE usuarios
    ADD COLUMN intentos_fallidos INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN bloqueado_hasta TIMESTAMPTZ NULL;
