-- V9__agregar_datos_personales.sql
-- HU-04: «cuando cree una cuenta con correo, nombre y uno de los cuatro roles».
-- HU-02: «cuando envíe mis datos, documento, correo, teléfono y contraseña».
-- RN-10: «Un paciente se identifica por su tipo y número de documento».
--
-- Ni `usuarios` ni `fichas` guardaban el nombre de la persona, y la ficha
-- identificaba al paciente solo por el número de documento, sin su tipo.

-- 1. Nombre de la cuenta.
ALTER TABLE usuarios ADD COLUMN nombre VARCHAR(150);
UPDATE usuarios SET nombre = split_part(correo, '@', 1) WHERE nombre IS NULL;
ALTER TABLE usuarios ALTER COLUMN nombre SET NOT NULL;

-- 2. Datos personales de la ficha.
ALTER TABLE fichas ADD COLUMN nombres VARCHAR(100);
ALTER TABLE fichas ADD COLUMN apellidos VARCHAR(100);
ALTER TABLE fichas ADD COLUMN tipo_documento VARCHAR(20) NOT NULL DEFAULT 'DNI';

ALTER TABLE fichas ADD CONSTRAINT chk_ficha_tipo_documento
    CHECK (tipo_documento IN ('DNI', 'CE', 'PASAPORTE'));

-- 3. RN-10: la identidad del paciente es el par (tipo, número), no el número solo.
ALTER TABLE fichas DROP CONSTRAINT IF EXISTS fichas_documento_key;
ALTER TABLE fichas ADD CONSTRAINT uq_ficha_documento UNIQUE (tipo_documento, documento);

-- 4. Datos de las fichas de demostración sembradas en V8.
UPDATE fichas SET nombres = 'Luis',  apellidos = 'Pérez'   WHERE documento = '40123456';
UPDATE fichas SET nombres = 'Ana',   apellidos = 'Quispe'  WHERE documento = '40987654';
