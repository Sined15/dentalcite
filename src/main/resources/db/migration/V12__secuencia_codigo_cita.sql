-- V12__secuencia_codigo_cita.sql
-- RF-15: la reserva genera un codigo unico que se muestra al confirmar.
--
-- El correlativo sale de una secuencia de la base y no de un COUNT en codigo,
-- por el mismo motivo que `numeroHistoria` en V2: dos reservas simultaneas
-- calcularian el mismo numero y la restriccion UNIQUE de `citas.codigo` haria
-- fallar una de las dos con un 500 en vez de darle su codigo.
CREATE SEQUENCE sq_codigo_cita START 1;
