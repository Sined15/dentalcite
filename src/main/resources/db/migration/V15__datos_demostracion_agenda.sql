-- V15__datos_demostracion_agenda.sql
-- HU-08 a HU-11. Hasta aquí, Flyway sembraba especialidades, consultorios y
-- feriados, pero ni un solo tratamiento ni un solo horario de atención. Sobre una
-- base limpia eso deja el motor de disponibilidad sin nada que ofrecer: el
-- selector de tratamientos del portal sale vacío y `GET /api/v1/disponibilidad`
-- no puede siquiera invocarse, porque exige un tratamiento. Demostrar el Sprint 2
-- obligaba entonces a dar de alta catálogo y horario a mano, que es justo el paso
-- manual que RNF-11 prohíbe.
--
-- Se siembra lo mínimo para que la agenda exista: catálogo, un segundo odontólogo
-- con especialidades distintas —sin él, «cualquier odontólogo» y RN-08 no se
-- distinguen de «el único que hay»— y el horario de ambos.
--
-- **Citas: ninguna, a propósito.** Una cita sembrada lleva fecha absoluta, así que
-- caducaría al día siguiente de escribirla y quedaría fuera de la ventana de RN-05;
-- además tendría que esquivar las restricciones de exclusión de V13. La agenda se
-- puebla reservando en vivo, que es también mejor demostración.

-- 1. Catálogo de tratamientos (RF-09). Las duraciones son múltiplos de quince
--    (RN-04, restricción de V10) y se reparten a propósito entre 30, 45 y 60 para
--    que se vea que el motor propone bloques de longitud distinta sobre el mismo
--    horario.
INSERT INTO tratamientos (id, codigo, nombre, descripcion, duracion_minutos, especialidad_id, activo)
SELECT gen_random_uuid(), v.codigo, v.nombre, v.descripcion, v.duracion, e.id, TRUE
FROM (VALUES
    ('TRT-001', 'Consulta y diagnóstico',      'Evaluación inicial con plan de tratamiento sugerido.', 30, 'Odontología General'),
    ('TRT-002', 'Profilaxis dental',           'Limpieza dental completa con destartraje supragingival.', 45, 'Odontología General'),
    ('TRT-003', 'Control de ortodoncia',       'Ajuste periódico de aparatología fija.', 30, 'Ortodoncia'),
    ('TRT-004', 'Endodoncia unirradicular',    'Tratamiento de conducto en pieza de una raíz.', 60, 'Endodoncia'),
    ('TRT-005', 'Destartraje periodontal',     'Raspado y alisado radicular por cuadrante.', 45, 'Periodoncia')
) AS v(codigo, nombre, descripcion, duracion, especialidad)
JOIN especialidades e ON e.nombre = v.especialidad;

-- 2. Segunda odontóloga. No tiene cuenta de usuario: una ficha puede existir sin
--    `Usuario` (RN-11 solo exige lo contrario, que una cuenta se vincule a una
--    ficha cuyo documento coincida), y para el motor lo único que cuenta es el
--    registro de `odontologos` con sus especialidades.
INSERT INTO fichas (documento, telefono, numero_historia) VALUES
    ('40555333', '987000003', 'HC-' || LPAD(nextval('sq_historia_clinica')::text, 5, '0'));

INSERT INTO odontologos (id, cop, nombres, apellidos, ficha_id, activo) VALUES (
    gen_random_uuid(), 'COP-10002', 'Ana', 'Quispe',
    (SELECT id FROM fichas WHERE documento = '40555333'), TRUE);

-- Especialidades distintas a las de Pérez (Odontología General y Ortodoncia, en
-- V8): así RN-08 es visible: consultar la disponibilidad de una endodoncia con
-- «cualquier odontólogo» solo puede proponer a Quispe.
INSERT INTO odontologo_especialidad (odontologo_id, especialidad_id)
SELECT o.id, e.id
FROM odontologos o, especialidades e
WHERE o.cop = 'COP-10002'
  AND e.nombre IN ('Endodoncia', 'Periodoncia');

-- 3. Horario de atención de ambos, de lunes a viernes (RF-11). Se declara por día
--    de la semana y en hora local de la clínica, no por fecha, de modo que estos
--    datos no caducan: el motor los reconcilia con las citas —que viven en UTC—
--    usando `app.zona-horaria`.
--    Los tramos no se solapan entre sí (RN-03) y dejan la tarde de Pérez libre
--    para que el bloqueo de HU-07 tenga dónde demostrarse.
INSERT INTO horarios_atencion (odontologo_id, dia_semana, hora_inicio, hora_fin)
SELECT o.id, d.dia, h.inicio, h.fin
FROM odontologos o
JOIN (VALUES ('COP-10001', TIME '09:00', TIME '13:00'),
             ('COP-10002', TIME '15:00', TIME '19:00')) AS h(cop, inicio, fin) ON h.cop = o.cop
CROSS JOIN (VALUES (1), (2), (3), (4), (5)) AS d(dia);
