-- V21__imagen_del_catalogo.sql
-- HU-06. La revisión v4 amplía RF-09 y RF-10: el tratamiento, la especialidad y
-- el odontólogo llevan imagen, porque el catálogo deja de ser una tabla de
-- mantenimiento y pasa a ser lo que el visitante recorre —la portada con los
-- tratamientos, la galería de especialidades y las fichas del equipo—.
--
-- Se guarda la **ruta**, no el archivo. No hay almacenamiento de ficheros en el
-- proyecto, y montarlo para esto pediría volumen, subida multipart, validación de
-- tipo y tamaño y una ruta que los sirviera. La ruta apunta a los SVG que viajan
-- con el cliente Angular (`public/img/catalogo/`), así que la demostración no
-- depende de Internet.
--
-- La columna es nullable a propósito: que el administrador dé de alta un
-- tratamiento sin imagen es legítimo, y el cliente pinta un marcador.

ALTER TABLE especialidades ADD COLUMN imagen_url VARCHAR(500);
ALTER TABLE tratamientos   ADD COLUMN imagen_url VARCHAR(500);
ALTER TABLE odontologos    ADD COLUMN imagen_url VARCHAR(500);

-- Se rellenan **todas** las filas sembradas, no unas cuantas. Una semilla a
-- medias deja la portada de la demostración con huecos y obliga a completarla a
-- mano, que es el paso manual que RNF-11 prohíbe y el mismo argumento de V15 y
-- V20.

-- 1. Las ocho especialidades del caso (V1 y V20).
UPDATE especialidades e SET imagen_url = v.ruta
FROM (VALUES
    ('Odontología General',          '/img/catalogo/especialidad-general.svg'),
    ('Ortodoncia',                   '/img/catalogo/especialidad-ortodoncia.svg'),
    ('Endodoncia',                   '/img/catalogo/especialidad-endodoncia.svg'),
    ('Periodoncia',                  '/img/catalogo/especialidad-periodoncia.svg'),
    ('Odontopediatría',              '/img/catalogo/especialidad-odontopediatria.svg'),
    ('Cirugía Bucal y Maxilofacial', '/img/catalogo/especialidad-cirugia.svg'),
    ('Rehabilitación Oral',          '/img/catalogo/especialidad-rehabilitacion.svg'),
    ('Odontología Estética',         '/img/catalogo/especialidad-estetica.svg')
) AS v(nombre, ruta)
WHERE e.nombre = v.nombre;

-- 2. Los ocho tratamientos (V15 y V20).
UPDATE tratamientos t SET imagen_url = v.ruta
FROM (VALUES
    ('TRT-001', '/img/catalogo/tratamiento-consulta.svg'),
    ('TRT-002', '/img/catalogo/tratamiento-profilaxis.svg'),
    ('TRT-003', '/img/catalogo/tratamiento-ortodoncia.svg'),
    ('TRT-004', '/img/catalogo/tratamiento-endodoncia.svg'),
    ('TRT-005', '/img/catalogo/tratamiento-destartraje.svg'),
    ('TRT-006', '/img/catalogo/tratamiento-exodoncia.svg'),
    ('TRT-007', '/img/catalogo/tratamiento-corona.svg'),
    ('TRT-008', '/img/catalogo/tratamiento-blanqueamiento.svg')
) AS v(codigo, ruta)
WHERE t.codigo = v.codigo;

-- 3. Los siete odontólogos (V8, V15 y V20). Son avatares con iniciales, no
--    retratos: inventar la foto de una persona sería peor que no tenerla.
UPDATE odontologos o SET imagen_url = v.ruta
FROM (VALUES
    ('COP-10001', '/img/catalogo/odontologo-cop-10001.svg'),
    ('COP-10002', '/img/catalogo/odontologo-cop-10002.svg'),
    ('COP-10003', '/img/catalogo/odontologo-cop-10003.svg'),
    ('COP-10004', '/img/catalogo/odontologo-cop-10004.svg'),
    ('COP-10005', '/img/catalogo/odontologo-cop-10005.svg'),
    ('COP-10006', '/img/catalogo/odontologo-cop-10006.svg'),
    ('COP-10007', '/img/catalogo/odontologo-cop-10007.svg')
) AS v(cop, ruta)
WHERE o.cop = v.cop;
