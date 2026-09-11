-- V19__planes_de_tratamiento.sql
-- HU-17 / RF-23: «crear un plan de tratamiento indicando tratamiento y numero
-- previsto de sesiones, y suspenderlo con motivo». Abre el modulo M6.
--
-- Dos tablas y no una, porque RN-15 da estados a la sesion —pendiente, atendida
-- y cerrada— y RN-16 exige que una sesion admita como maximo una cita enlazada:
-- eso son filas, no un contador. En este Sprint solo se escribe PENDIENTE; las
-- otras dos las alcanzaran HU-18 y HU-19. El CHECK las declara ya porque los
-- estados son del modelo y no de la historia que los estrena.
--
-- Lo que NO esta aqui, a proposito: el avance. RN-14 dice que «se deriva siempre
-- de las citas atendidas enlazadas; nunca se persiste ni se edita a mano», asi
-- que no hay columna que lo guarde y no la habra.

CREATE TABLE planes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ficha_id UUID NOT NULL REFERENCES fichas(id) ON DELETE RESTRICT,
    tratamiento_id UUID NOT NULL REFERENCES tratamientos(id) ON DELETE RESTRICT,
    -- Quien lo planifico. RF-26 lo necesitara para decidir quien puede consultar
    -- cada plan, y sin el un plan no dice de quien es la indicacion clinica.
    odontologo_id UUID NOT NULL REFERENCES odontologos(id) ON DELETE RESTRICT,
    sesiones_previstas INTEGER NOT NULL CHECK (sesiones_previstas > 0),
    -- RN-12: «en pacientes y planes la baja logica es una marca de actividad».
    -- Ningun plan se borra; suspenderlo baja esta marca y conserva la fila.
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    motivo_suspension VARCHAR(255),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Un plan sin marca de actividad tiene que decir por que se suspendio, y uno
    -- activo no puede tener motivo: son estados excluyentes, no dos columnas
    -- sueltas que puedan contradecirse.
    CONSTRAINT plan_suspendido_con_motivo
        CHECK ((activo AND motivo_suspension IS NULL)
               OR (NOT activo AND motivo_suspension IS NOT NULL))
);

-- Las tres claves ajenas son RESTRICT y no CASCADE. RN-12 prohibe eliminar
-- pacientes y planes, y de las bajas de catalogo dice que «no puede darse de baja
-- un odontologo, un tratamiento ni un consultorio con citas activas»: la baja es
-- siempre logica. RESTRICT es lo que impide que un borrado fisico accidental se
-- lleve por delante la indicacion clinica de un paciente.

-- RN-13: «un paciente no puede tener dos planes activos del mismo tratamiento».
--
-- El criterio de aceptacion pide expresamente que el 409 este «garantizado por un
-- indice unico parcial», no por una comprobacion previa en el servicio: entre
-- mirar si existe y crear el segundo cabe otra creacion, exactamente igual que
-- entre consultar la disponibilidad y reservar (HU-10).
--
-- El indice se apoya solo en `activo`. RN-13 define activo como «conserva su marca
-- de actividad Y le restan sesiones», pero la segunda mitad se deriva del avance
-- de RF-25, que es HU-18 y pertenece al Sprint 4; la Definicion de Terminado
-- prohibe que un criterio dependa de una historia posterior. HU-18 revisara esta
-- condicion cuando el avance exista.
CREATE UNIQUE INDEX ux_plan_activo_por_tratamiento
    ON planes (ficha_id, tratamiento_id)
    WHERE activo;

-- La ficha consulta sus planes, y RF-26 los consultara por paciente.
CREATE INDEX idx_planes_ficha ON planes (ficha_id);
CREATE INDEX idx_planes_odontologo ON planes (odontologo_id);

CREATE TABLE plan_sesiones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- CASCADE, al contrario que las de arriba y por la misma razon que
    -- `citas_historial.cita_id`: la sesion es parte del plan y no tiene
    -- existencia propia. Que ningun plan se borre lo garantiza la aplicacion.
    plan_id UUID NOT NULL REFERENCES planes(id) ON DELETE CASCADE,
    numero INTEGER NOT NULL CHECK (numero > 0),
    -- RN-15: «una sesion recorre tres estados: pendiente sin cita enlazada,
    -- atendida cuando la cita enlazada lo alcanza y cerrada al registrarse su
    -- recomendacion».
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE', 'ATENDIDA', 'CERRADA')),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Las sesiones estan numeradas y su orden es el que HU-18 recorrera para
    -- ocupar «la primera pendiente en orden» (RN-15).
    CONSTRAINT ux_sesion_numero_por_plan UNIQUE (plan_id, numero)
);

CREATE INDEX idx_plan_sesiones_plan ON plan_sesiones (plan_id, numero);
