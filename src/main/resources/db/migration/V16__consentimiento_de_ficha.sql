-- V16__consentimiento_de_ficha.sql
-- HU-12 · RF-06 · RNF-06: «registrar un paciente presencial sin cuenta, con su
-- consentimiento informado».
--
-- `consentimientos.usuario_id` nacio NOT NULL en V1, cuando la unica alta era la
-- del portal y toda alta tenia cuenta. El paciente que llega al mostrador no
-- tiene ninguna, asi que su consentimiento no cabia en la tabla: RNF-06 —«cada
-- alta registrara el consentimiento informado con su fecha y la version del
-- texto»— se habria quedado en una casilla del formulario sin rastro persistido.

ALTER TABLE consentimientos ADD COLUMN ficha_id UUID
    REFERENCES fichas(id) ON DELETE CASCADE;

ALTER TABLE consentimientos ALTER COLUMN usuario_id DROP NOT NULL;

-- Los consentimientos ya emitidos por el portal pertenecen tambien a la ficha de
-- su cuenta. Sin este retrollenado, preguntar «¿consta el consentimiento de esta
-- ficha?» daria respuestas distintas segun por donde entro la persona.
UPDATE consentimientos c SET ficha_id = u.ficha_id
    FROM usuarios u
    WHERE u.id = c.usuario_id AND u.ficha_id IS NOT NULL;

-- Relajar el NOT NULL sin esto abriria la puerta a un consentimiento sin
-- titular, que no es un consentimiento.
ALTER TABLE consentimientos ADD CONSTRAINT chk_consentimiento_titular
    CHECK (usuario_id IS NOT NULL OR ficha_id IS NOT NULL);

CREATE INDEX ix_consentimientos_ficha ON consentimientos (ficha_id);
