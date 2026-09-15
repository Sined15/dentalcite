-- V22__pacientes_de_demostracion.sql
-- El entorno tiene que arrancar listo para demostrarse, sin ningún alta a mano.
-- La semilla lo venía cumpliendo para el catálogo y la agenda, pero no para las
-- personas: tras la anterior, la clínica tiene ocho fichas y siete son de
-- odontólogos, porque cada uno necesita una para vincular su cuenta con su
-- registro. La única ficha de paciente es la de Rosa Delgado.
--
-- Con ese padrón no se puede enseñar nada del módulo de pacientes: la búsqueda
-- devuelve odontólogos, no hay a quién citar desde recepción y la ficha clínica
-- no tiene historial. Todo eso obligaba a dar pacientes de alta antes de cada
-- demostración, que es justo lo que la semilla existe para evitar; por eso esto
-- es una migración y no un guion aparte.
--
-- Tampoco se siembran citas, por lo mismo que en las anteriores: llevarían fecha
-- absoluta, caducarían y tendrían que esquivar las restricciones de solape.

-- 1. Las cuentas de demostración se llamaban como el trozo de su correo. Se
--    sembraron sin nombre y, cuando la columna pasó a ser obligatoria, se las
--    bautizó con `split_part(correo, '@', 1)`; nadie volvió sobre ello. El
--    resultado es que la cuenta «paciente» y la ficha «Rosa Delgado» son la misma
--    persona y no hay forma de saberlo mirando, que es justo lo que hace falta al
--    cruzar la pantalla de cuentas con la de pacientes.
--
--    Las dos que tienen ficha toman el nombre de esa ficha. Administración y
--    recepción no la tienen —solo el odontólogo la necesita— y reciben un nombre
--    legible cualquiera.
UPDATE usuarios SET nombre = 'Sofía Ramírez'  WHERE correo = 'admin@dentalcite.com';
UPDATE usuarios SET nombre = 'Carmen Aguilar' WHERE correo = 'recepcion@dentalcite.com';
UPDATE usuarios SET nombre = 'Luis Pérez'     WHERE correo = 'dr.perez@dentalcite.com';
UPDATE usuarios SET nombre = 'Rosa Delgado'   WHERE correo = 'paciente@demo.com';

-- 2. La ficha de la odontóloga con colegiatura COP-10002 se sembró solo con
--    documento y teléfono, así que el listado la identifica por su número de
--    historia —«HC-00003»— mientras las demás salen con su nombre. Es el mismo
--    arreglo que ya se hizo con la ficha de Rosa Delgado.
UPDATE fichas SET nombres = 'Ana', apellidos = 'Quispe' WHERE documento = '40555333';

-- 3. Seis pacientes. **Ninguno lleva cuenta ni registro de odontólogo**: una
--    ficha sin credenciales es exactamente lo que crea el alta presencial en el
--    mostrador, y quien quiera cuenta se registra por el portal, que vincula la
--    ficha existente por tipo y número de documento en vez de duplicarla.
--
--    Camacho aparece dos veces a propósito: la búsqueda compara el apellido por
--    prefijo, y con un solo portador no se distingue «encontró a esta persona» de
--    «encontró a todas las que empiezan así». Dos llevan alergias, que es lo que
--    la ficha clínica muestra y que el resto deja en blanco.
--    El número de historia sale de la secuencia, nunca de un literal.
INSERT INTO fichas (documento, telefono, nombres, apellidos, alergias, numero_historia)
SELECT v.documento, v.telefono, v.nombres, v.apellidos, v.alergias,
       'HC-' || LPAD(nextval('sq_historia_clinica')::text, 5, '0')
FROM (VALUES
    ('45110001', '987100001', 'Lucía',    'Camacho',  NULL),
    ('45110002', '987100002', 'Héctor',   'Camacho',  NULL),
    ('45110003', '987100003', 'Miriam',   'Tapia',    'Penicilina'),
    ('45110004', '987100004', 'Óscar',    'Benites',  NULL),
    ('45110005', '987100005', 'Patricia', 'Nakamura', 'Látex; anestesia con epinefrina'),
    ('45110006', '987100006', 'Raúl',     'Espinoza', NULL)
) AS v(documento, telefono, nombres, apellidos, alergias);
