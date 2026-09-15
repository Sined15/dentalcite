-- V23__cuentas_de_los_odontologos.sql
-- De los siete odontólogos del caso simulado solo uno tenía cuenta: a Pérez se
-- la dieron al corregir las credenciales de demostración, y los otros seis se
-- sembraron **sin** credenciales, porque para calcular la disponibilidad basta el
-- registro del odontólogo con su horario y sus especialidades.
--
-- Bastaba mientras la agenda fuera solo del mostrador. Ya no: el odontólogo
-- declara su propio horario, registra sus bloqueos, consulta su agenda y cierra
-- sus citas, y nada de eso puede hacerlo quien no inicia sesión. Con una sola
-- cuenta, seis de los siete dependían de que recepción se lo llevara todo, y
-- enseñar esas operaciones se apoyaba en una única persona.
--
-- La cuenta se vincula **por ficha**, no por registro: de la cuenta sale la ficha
-- y de la ficha el odontólogo, y la restricción única sobre `usuarios.ficha_id`
-- lo garantiza. De ahí el NOT EXISTS: Pérez ya tiene la suya y no debe duplicarse.
--
-- El hash es el mismo que ya usan las cuentas de demostración para 'Password123'.
-- Es literalmente el mismo texto porque BCrypt lleva su sal dentro y dos hashes
-- distintos de la misma contraseña no aportan nada a una semilla; una credencial
-- real no nace así, nace del alta con contraseña provisional y cambio obligatorio.
INSERT INTO usuarios (correo, contrasena_hash, rol, activo, nombre, ficha_id)
SELECT v.correo,
       '$2a$12$a2Y6l0kp1lyXZD2JB86sieckbZwSfHke5EBgo7gNzobC6GXTb6I9m',
       'ODONTOLOGO',
       TRUE,
       o.nombres || ' ' || o.apellidos,
       o.ficha_id
FROM odontologos o
JOIN (VALUES
    ('COP-10002', 'dra.quispe@dentalcite.com'),
    ('COP-10003', 'dra.mendoza@dentalcite.com'),
    ('COP-10004', 'dr.rios@dentalcite.com'),
    ('COP-10005', 'dra.vargas@dentalcite.com'),
    ('COP-10006', 'dr.salas@dentalcite.com'),
    ('COP-10007', 'dra.flores@dentalcite.com')
) AS v(cop, correo) ON v.cop = o.cop
WHERE NOT EXISTS (SELECT 1 FROM usuarios u WHERE u.ficha_id = o.ficha_id);
