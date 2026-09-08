-- V8__corregir_credenciales_demo.sql
-- HU-01: «existirán […] una credencial de demostración por cada uno de los cuatro
-- roles». El hash sembrado en V1 para ADMINISTRADOR, RECEPCIONISTA y ODONTOLOGO no
-- correspondía a 'Password123' (verificado con BCrypt), así que tres de las cuatro
-- credenciales de demostración no podían iniciar sesión. Se reemplazan por hashes
-- BCrypt de coste 12 generados y verificados contra esa contraseña (RNF-03).
--
-- Además, la cuenta ODONTOLOGO carecía de ficha y de registro en `odontologos`, de
-- modo que no resolvía a ningún identificador de odontólogo (RN-11) y no podía
-- declarar su horario ni sus bloqueos (HU-07). Se siembran ambos.

-- 1. Credenciales de demostración: las cuatro cuentas usan 'Password123'.
UPDATE usuarios SET contrasena_hash = '$2a$12$.qTTkgKjMW1kWIZ3tEWFnOe7I.3lvKUoDInH0Ig02FOkZG/bpFiP.'
    WHERE correo = 'admin@dentalcite.com';
UPDATE usuarios SET contrasena_hash = '$2a$12$uigH6JvzO5b9M1ssOuQriOVs/APJxCCKUxS0crfjqrQUWYY/e1OnC'
    WHERE correo = 'recepcion@dentalcite.com';
UPDATE usuarios SET contrasena_hash = '$2a$12$a2Y6l0kp1lyXZD2JB86sieckbZwSfHke5EBgo7gNzobC6GXTb6I9m'
    WHERE correo = 'dr.perez@dentalcite.com';

-- 2. Fichas de demostración. El número de historia sale de la secuencia (RN-10),
--    no de un literal, para que un registro posterior no colisione con él.
INSERT INTO fichas (documento, telefono, numero_historia) VALUES
    ('40123456', '987000001', 'HC-' || LPAD(nextval('sq_historia_clinica')::text, 5, '0')),
    ('40987654', '987000002', 'HC-' || LPAD(nextval('sq_historia_clinica')::text, 5, '0'));

UPDATE usuarios SET ficha_id = (SELECT id FROM fichas WHERE documento = '40123456')
    WHERE correo = 'dr.perez@dentalcite.com';
UPDATE usuarios SET ficha_id = (SELECT id FROM fichas WHERE documento = '40987654')
    WHERE correo = 'paciente@demo.com';

-- 3. Registro de odontólogo de la cuenta ODONTOLOGO, con sus especialidades (RF-10).
INSERT INTO odontologos (id, cop, nombres, apellidos, ficha_id, activo) VALUES (
    gen_random_uuid(), 'COP-10001', 'Luis', 'Pérez',
    (SELECT id FROM fichas WHERE documento = '40123456'), TRUE);

INSERT INTO odontologo_especialidad (odontologo_id, especialidad_id)
SELECT o.id, e.id
FROM odontologos o, especialidades e
WHERE o.cop = 'COP-10001'
  AND e.nombre IN ('Odontología General', 'Ortodoncia');
