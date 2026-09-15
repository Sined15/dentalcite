-- V24__agenda_de_demostracion.sql
-- Las semillas anteriores dejaron la clínica montada pero vacía de actividad:
-- catálogo, consultorios, siete odontólogos con horario y seis pacientes, y
-- ninguna cita. Las tres declinaron sembrarlas por la misma razón —«llevarían
-- fecha absoluta, caducarían y tendrían que esquivar las restricciones de
-- solape»—, dejando la agenda para poblarla reservando en vivo.
--
-- Eso basta para demostrar la reserva, pero no lo que cuelga de ella. Al entrar
-- con la cuenta del odontólogo, «Mi agenda» sale vacía, «Por cerrar» sale vacía
-- y su lista de pacientes también, porque quién es paciente suyo se deduce de las
-- citas: sin ninguna, no ha atendido a nadie. Enseñar esas tres pantallas
-- obligaba a reservar a mano antes de cada demostración, que es el paso manual
-- que la semilla existe para evitar.
--
-- Los dos obstáculos se resuelven en vez de esquivarse:
--
--   * **La caducidad.** Ninguna fecha se escribe: se calculan a partir de
--     CURRENT_DATE, tomando días hábiles y saltando fines de semana y feriados,
--     de modo que la agenda queda repartida alrededor del día en que se migra.
--     Envejece igualmente —una migración corre una sola vez—, pero lo hace
--     desplazándose entera hacia el pasado, y un entorno recreado vuelve a
--     nacer con la agenda colocada.
--   * **El solape.** Las horas se eligen dentro del horario de Pérez (09:00 a
--     13:00 de lunes a viernes) y sin pisarse entre sí, así que las exclusiones
--     de `citas_sin_solape_odontologo` y `citas_sin_solape_consultorio` se
--     cumplen sin trucos. Son parciales sobre CONFIRMADA, de modo que lo
--     atendido, lo no asistido y lo cancelado ni siquiera entran en ellas.
--
-- Todo es de Pérez porque es la única cuenta de odontólogo con la que se
-- demuestra, y porque repartir citas entre los siete pondría a competir por los
-- cinco consultorios sin que eso enseñe nada.
--
-- La hora local se convierte con la zona de la clínica escrita aquí, que duplica
-- `app.zona-horaria`. Es la única forma: los horarios se declaran en hora local y
-- las citas se guardan en UTC, y una migración no lee la configuración de la
-- aplicación. Si esa propiedad cambia, esta semilla hay que mirarla.

WITH habiles_pasados AS (
    -- Los días hábiles anteriores a hoy, el más reciente primero: `n = 1` es el
    -- último día en que la clínica abrió. Se descartan fines de semana y
    -- feriados porque una cita en un día sin atención contradiría al motor de
    -- disponibilidad, que jamás la habría ofrecido.
    SELECT d::date AS fecha, row_number() OVER (ORDER BY d DESC) AS n
    FROM generate_series(CURRENT_DATE - 30, CURRENT_DATE - 1, INTERVAL '1 day') AS d
    WHERE EXTRACT(ISODOW FROM d) <= 5
      AND NOT EXISTS (SELECT 1 FROM feriados f WHERE f.fecha = d::date)
),
habiles_futuros AS (
    -- Y los siguientes, el más próximo primero. Empieza en mañana y no en hoy:
    -- la antelación mínima descarta las franjas inmediatas, así que una cita de
    -- hoy podría caer en una hora que el portal ya no ofrece.
    SELECT d::date AS fecha, row_number() OVER (ORDER BY d) AS n
    FROM generate_series(CURRENT_DATE + 1, CURRENT_DATE + 30, INTERVAL '1 day') AS d
    WHERE EXTRACT(ISODOW FROM d) <= 5
      AND NOT EXISTS (SELECT 1 FROM feriados f WHERE f.fecha = d::date)
),
agenda AS (
    -- Cada fila es una cita: cuándo cae, a qué hora local, de quién, de qué y en
    -- qué estado. Los tratamientos son los tres que Pérez puede atender con sus
    -- especialidades —general y ortodoncia—: darle una endodoncia dejaría en la
    -- base una cita que el motor nunca habría propuesto.
    --
    -- El reparto de estados no es decorativo, cada uno enseña una pantalla:
    --
    --   ATENDIDA y NO_ASISTIO  el historial de la ficha y el paciente atendido
    --   CONFIRMADA ya vencida  la cola de «Por cerrar»
    --   CONFIRMADA futura      «Mi agenda» y la cancelación desde recepción
    --   CANCELADA              que cancelar libera la franja
    --
    -- Óscar Benites solo aparece con la cancelada, y eso es a propósito: una
    -- cita cancelada no acredita haber tratado a nadie, así que él **no** sale
    -- entre los pacientes de Pérez aunque tenga una cita a su nombre. Es la
    -- distinción que hace el acceso a la ficha, y conviene poder enseñarla.
    SELECT * FROM (VALUES
        ('pasado', 5, TIME '09:00', '45110001', 'TRT-001', 'Consultorio 1', 'ATENDIDA',   NULL,                                   'recepcion@dentalcite.com'),
        ('pasado', 5, TIME '10:00', '45110003', 'TRT-002', 'Consultorio 1', 'ATENDIDA',   NULL,                                   'recepcion@dentalcite.com'),
        ('pasado', 3, TIME '09:30', '45110002', 'TRT-003', 'Consultorio 2', 'NO_ASISTIO', NULL,                                   'recepcion@dentalcite.com'),
        ('pasado', 2, TIME '09:00', '40987654', 'TRT-002', 'Consultorio 1', 'ATENDIDA',   NULL,                                   'paciente@demo.com'),
        ('pasado', 1, TIME '09:00', '45110005', 'TRT-001', 'Consultorio 1', 'CONFIRMADA', NULL,                                   'recepcion@dentalcite.com'),
        ('futuro', 1, TIME '09:00', '45110001', 'TRT-003', 'Consultorio 1', 'CONFIRMADA', NULL,                                   'recepcion@dentalcite.com'),
        ('futuro', 1, TIME '10:00', '45110003', 'TRT-001', 'Consultorio 1', 'CONFIRMADA', NULL,                                   'recepcion@dentalcite.com'),
        ('futuro', 2, TIME '09:00', '45110006', 'TRT-002', 'Consultorio 1', 'CONFIRMADA', NULL,                                   'recepcion@dentalcite.com'),
        ('futuro', 3, TIME '09:30', '40987654', 'TRT-003', 'Consultorio 2', 'CONFIRMADA', NULL,                                   'paciente@demo.com'),
        ('futuro', 4, TIME '11:00', '45110004', 'TRT-001', 'Consultorio 1', 'CANCELADA',  'El paciente pidió reprogramar',        'recepcion@dentalcite.com')
    ) AS v(cuando, n, hora, documento, tratamiento, consultorio, estado, motivo, autor)
),
nuevas AS (
    INSERT INTO citas (codigo, ficha_id, odontologo_id, tratamiento_id, consultorio_id,
                       inicio, fin, estado, motivo_cancelacion, creado_por_usuario_id,
                       creado_en, actualizado_en)
    SELECT
        -- El correlativo sale de la secuencia, igual que en la reserva: nunca de
        -- un literal ni de un COUNT.
        'CIT-' || LPAD(nextval('sq_codigo_cita')::text, 6, '0'),
        f.id, o.id, t.id, c.id,
        (COALESCE(hp.fecha, hf.fecha) + a.hora) AT TIME ZONE 'America/Lima',
        (COALESCE(hp.fecha, hf.fecha) + a.hora) AT TIME ZONE 'America/Lima'
            + (t.duracion_minutos * INTERVAL '1 minute'),
        a.estado,
        a.motivo,
        u.id,
        -- Se reservaron antes de ocurrir, que es lo que dice cualquier agenda
        -- real; dejar el valor por omisión pondría la reserva después de la cita.
        (COALESCE(hp.fecha, hf.fecha) + a.hora) AT TIME ZONE 'America/Lima' - INTERVAL '10 days',
        (COALESCE(hp.fecha, hf.fecha) + a.hora) AT TIME ZONE 'America/Lima' - INTERVAL '10 days'
    FROM agenda a
    JOIN fichas f        ON f.documento = a.documento
    JOIN odontologos o   ON o.cop = 'COP-10001'
    JOIN tratamientos t  ON t.codigo = a.tratamiento
    JOIN consultorios c  ON c.nombre = a.consultorio
    JOIN usuarios u      ON u.correo = a.autor
    LEFT JOIN habiles_pasados hp ON a.cuando = 'pasado' AND hp.n = a.n
    LEFT JOIN habiles_futuros hf ON a.cuando = 'futuro' AND hf.n = a.n
    WHERE COALESCE(hp.fecha, hf.fecha) IS NOT NULL
    RETURNING id, estado, motivo_cancelacion, inicio, fin
)
-- La bitácora, en la misma sentencia: así solo se escribe sobre las filas que
-- esta migración acaba de crear, y no sobre citas que alguien haya dejado en la
-- base probando. El alta no se registra —ninguna transición tiene estado
-- anterior nulo—, igual que en la aplicación: cuándo nació la cita ya lo dice
-- `creado_en`, y la bitácora existe para quién ejerció una transición sobre ella.
INSERT INTO citas_historial (cita_id, estado_anterior, estado_nuevo, motivo, usuario_id, ocurrido_en)
SELECT
    n.id,
    'CONFIRMADA',
    n.estado,
    n.motivo_cancelacion,
    -- Quién la ejerció: el resultado lo registra el odontólogo al terminar; la
    -- cancelación, el mostrador.
    CASE WHEN n.estado = 'CANCELADA'
         THEN (SELECT id FROM usuarios WHERE correo = 'recepcion@dentalcite.com')
         ELSE (SELECT id FROM usuarios WHERE correo = 'dr.perez@dentalcite.com')
    END,
    CASE WHEN n.estado = 'CANCELADA'
         THEN n.inicio - INTERVAL '2 days'
         ELSE n.fin + INTERVAL '10 minutes'
    END
FROM nuevas n
WHERE n.estado <> 'CONFIRMADA';
