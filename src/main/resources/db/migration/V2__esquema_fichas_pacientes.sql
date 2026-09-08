CREATE SEQUENCE sq_historia_clinica START 1;

CREATE TABLE fichas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    documento VARCHAR(20) UNIQUE NOT NULL,
    telefono VARCHAR(20),
    numero_historia VARCHAR(20) UNIQUE NOT NULL
);

-- Vincular usuario con ficha
ALTER TABLE usuarios ADD COLUMN ficha_id UUID;
ALTER TABLE usuarios ADD CONSTRAINT fk_usuarios_fichas FOREIGN KEY (ficha_id) REFERENCES fichas(id) ON DELETE SET NULL;
ALTER TABLE usuarios ADD CONSTRAINT uq_usuarios_fichas UNIQUE (ficha_id);

CREATE TABLE consentimientos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    fecha TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version_texto VARCHAR(50) NOT NULL
);
