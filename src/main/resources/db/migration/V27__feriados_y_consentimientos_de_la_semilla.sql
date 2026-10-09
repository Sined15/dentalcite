-- V27__feriados_y_consentimientos_de_la_semilla.sql
-- Dos huecos de la semilla de demostración, los dos de datos y no de esquema.

-- 1. Feriados.
--
--    V1 cargó los del segundo semestre de 2026 y se le escapó el 9 de diciembre,
--    Batalla de Ayacucho, que cae en miércoles: el motor de disponibilidad lo
--    ofrecía como un día más y el calendario del portal dejaba marcarlo. Además,
--    la reserva admite hasta noventa días vista, así que desde octubre el
--    horizonte entra en 2027, del que no había ninguno. Se añaden los que caen
--    dentro de ese horizonte contado desde el final del ciclo: el 1 de enero y el
--    Jueves y el Viernes Santo.
--
--    Los feriados no se mantienen por interfaz: los del año siguiente llegan con
--    otra migración como esta. Ninguno de estos cae en lunes, que es el día que
--    eligen las pruebas de reserva para tener agenda lejos de la antelación
--    mínima.
INSERT INTO feriados (fecha, descripcion) VALUES
    ('2026-12-09', 'Batalla de Ayacucho'),
    ('2027-01-01', 'Año Nuevo'),
    ('2027-03-25', 'Jueves Santo'),
    ('2027-03-26', 'Viernes Santo')
ON CONFLICT (fecha) DO NOTHING;

-- 2. Consentimientos.
--
--    Toda alta de paciente registra el consentimiento informado con su fecha y la
--    versión del texto, y las fichas sembradas representan justo eso: personas
--    dadas de alta en el mostrador, y una que además se registró en el portal.
--    Ninguna lo tenía, de modo que inspeccionar los consentimientos de la
--    demostración daba siete pacientes que nunca lo habían dado.
--
--    Se registra la versión que el cliente muestra hoy y se enlaza la cuenta
--    cuando la persona la tiene, igual que hace el registro del portal. El NOT
--    EXISTS deja en paz a quien ya lo tuviera, por ejemplo porque en una base de
--    trabajo alguien la registró de nuevo a mano.
INSERT INTO consentimientos (ficha_id, usuario_id, version_texto)
SELECT f.id, u.id, '1.0'
FROM fichas f
LEFT JOIN usuarios u ON u.ficha_id = f.id
WHERE f.tipo_documento = 'DNI'
  AND f.documento IN ('40987654',
                      '45110001', '45110002', '45110003', '45110004', '45110005', '45110006')
  AND NOT EXISTS (SELECT 1 FROM consentimientos c WHERE c.ficha_id = f.id);
