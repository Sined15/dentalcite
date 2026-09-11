-- V20__semilla_del_caso_simulado.sql
-- HU-01. La revisión v4 del informe cambió el tamaño del caso: la clínica pasa a
-- siete odontólogos repartidos en ocho especialidades, con cinco consultorios
-- (§3.5 Supuestos, y la métrica de RNF-01). La semilla se había quedado en cinco
-- especialidades y tres consultorios (`V1`), con dos odontólogos: Pérez (`V8`) y
-- Quispe (`V15`).
--
-- RNF-11 exige que el entorno arranque «sin ningún paso manual», así que demostrar
-- el caso del informe obligaba hoy a dar de alta a mano tres especialidades, dos
-- consultorios y cinco odontólogos con su horario. Es el mismo argumento que
-- obligó a escribir `V15`, y por eso esto es una migración y no un guion aparte.
--
-- Flyway ya selló `V1`, `V8` y `V15`, de modo que el caso se completa añadiendo.

-- 1. Las tres especialidades que faltaban de las ocho del informe.
INSERT INTO especialidades (nombre) VALUES
    ('Cirugía Bucal y Maxilofacial'),
    ('Rehabilitación Oral'),
    ('Odontología Estética');

-- 2. Los dos consultorios que faltaban de los cinco.
INSERT INTO consultorios (nombre) VALUES
    ('Consultorio 4'),
    ('Consultorio 5');

-- 3. Un tratamiento por especialidad nueva (RF-09). Sin él la especialidad no es
--    alcanzable desde el portal —`GET /api/v1/disponibilidad` exige un
--    tratamiento—, así que sembrar la especialidad sola dejaría invisible a su
--    odontólogo: justo el paso manual que RNF-11 prohíbe. Las duraciones son
--    múltiplos de quince (RN-04, restricción de `V10`).
INSERT INTO tratamientos (id, codigo, nombre, descripcion, duracion_minutos, especialidad_id, activo)
SELECT gen_random_uuid(), v.codigo, v.nombre, v.descripcion, v.duracion, e.id, TRUE
FROM (VALUES
    ('TRT-006', 'Exodoncia simple',        'Extracción de pieza dentaria sin osteotomía.',            60, 'Cirugía Bucal y Maxilofacial'),
    ('TRT-007', 'Corona sobre pieza',      'Rehabilitación protésica de una pieza con corona.',       90, 'Rehabilitación Oral'),
    ('TRT-008', 'Blanqueamiento dental',   'Aclaramiento dental en consultorio, sesión única.',       45, 'Odontología Estética')
) AS v(codigo, nombre, descripcion, duracion, especialidad)
JOIN especialidades e ON e.nombre = v.especialidad;

-- 4. Los cinco odontólogos que faltaban. Ninguno lleva cuenta de `usuarios`, igual
--    que Quispe en `V15`: una ficha puede existir sin `Usuario`, y para el motor lo
--    único que cuenta es el registro de `odontologos` con sus especialidades.
--    El número de historia sale de la secuencia (RN-10), nunca de un literal.
INSERT INTO fichas (documento, telefono, nombres, apellidos, numero_historia)
SELECT v.documento, v.telefono, v.nombres, v.apellidos,
       'HC-' || LPAD(nextval('sq_historia_clinica')::text, 5, '0')
FROM (VALUES
    ('40555334', '987000004', 'Carla', 'Mendoza'),
    ('40555335', '987000005', 'Jorge', 'Ríos'),
    ('40555336', '987000006', 'Elena', 'Vargas'),
    ('40555337', '987000007', 'Marco', 'Salas'),
    ('40555338', '987000008', 'Diana', 'Flores')
) AS v(documento, telefono, nombres, apellidos);

INSERT INTO odontologos (id, cop, nombres, apellidos, ficha_id, activo)
SELECT gen_random_uuid(), v.cop, f.nombres, f.apellidos, f.id, TRUE
FROM (VALUES
    ('COP-10003', '40555334'),
    ('COP-10004', '40555335'),
    ('COP-10005', '40555336'),
    ('COP-10006', '40555337'),
    ('COP-10007', '40555338')
) AS v(cop, documento)
JOIN fichas f ON f.documento = v.documento;

-- 5. Reparto de especialidades. Las ocho quedan cubiertas y varias con más de un
--    odontólogo, para que «cualquier odontólogo» (RN-08) sea una elección real y
--    no un sinónimo del único que hay.
--
--    Endodoncia sigue siendo exclusiva de Quispe a propósito: el guion de la
--    Sprint Review de E-2 dice «elegir una endodoncia con cualquier odontólogo:
--    solo se propone Quispe», y es el acta de una Review ya celebrada.
INSERT INTO odontologo_especialidad (odontologo_id, especialidad_id)
SELECT o.id, e.id
FROM (VALUES
    ('COP-10003', 'Odontopediatría'),
    ('COP-10003', 'Odontología General'),
    ('COP-10004', 'Cirugía Bucal y Maxilofacial'),
    ('COP-10005', 'Rehabilitación Oral'),
    ('COP-10005', 'Odontología Estética'),
    ('COP-10006', 'Ortodoncia'),
    ('COP-10006', 'Odontología Estética'),
    ('COP-10007', 'Odontología General'),
    ('COP-10007', 'Periodoncia')
) AS v(cop, especialidad)
JOIN odontologos o ON o.cop = v.cop
JOIN especialidades e ON e.nombre = v.especialidad;

-- 6. Horario de atención, de lunes a viernes y en hora local (RF-11). Como en
--    `V15`, se declara por día de la semana y no por fecha, de modo que no caduca.
--
--    Dos rasgos del reparto que no son casuales:
--
--    a) Por la mañana atienden seis odontólogos y solo hay cinco consultorios. Eso
--       es lo que hace demostrable a mano RN-02 y RF-14: existe de verdad la franja
--       que el motor no ofrece porque no queda consultorio libre. Con cinco y cinco
--       nunca llegaría a verse.
--    b) Mendoza y Flores tienen jornada partida, dos tramos que no se solapan
--       (RN-03). Hasta aquí ningún dato semilla tenía más de un tramo por día, así
--       que ese camino del motor no se recorría en la demostración.
--
--    La tarde de Pérez sigue libre, que es donde el bloqueo de HU-07 se demuestra.
INSERT INTO horarios_atencion (odontologo_id, dia_semana, hora_inicio, hora_fin)
SELECT o.id, d.dia, h.inicio, h.fin
FROM odontologos o
JOIN (VALUES ('COP-10003', TIME '09:00', TIME '13:00'),
             ('COP-10003', TIME '15:00', TIME '19:00'),
             ('COP-10004', TIME '09:00', TIME '13:00'),
             ('COP-10005', TIME '09:00', TIME '13:00'),
             ('COP-10006', TIME '09:00', TIME '13:00'),
             ('COP-10007', TIME '09:00', TIME '13:00'),
             ('COP-10007', TIME '15:00', TIME '19:00')) AS h(cop, inicio, fin) ON h.cop = o.cop
CROSS JOIN (VALUES (1), (2), (3), (4), (5)) AS d(dia);

-- 7. La ficha del paciente de demostración se llamaba «Ana Quispe» desde `V9`, el
--    mismo nombre que la odontóloga COP-10002 de `V15`. Con dos odontólogos podía
--    pasar inadvertido; con siete se lee como un error de la demostración.
UPDATE fichas SET nombres = 'Rosa', apellidos = 'Delgado' WHERE documento = '40987654';
