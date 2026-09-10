-- V18__autor_de_la_cita.sql
-- HU-14 / RF-17: «reservar una cita en nombre de cualquier paciente
-- registrado», con el criterio de que «quedara registrado quien la creo»
-- (RN-09).
--
-- Hasta aqui la cita no guardaba a su autor porque no hacia falta: la reservaba
-- el paciente para si mismo y el autor era deducible de `ficha_id`. En cuanto
-- recepcion puede reservar en nombre de otro, esas dos cosas dejan de coincidir
-- y la de quien es la cita ya no dice quien la pidio.
--
-- Por que una columna y no una fila en `citas_historial`, que es donde vive el
-- resto de la trazabilidad (RF-21): el alta NO se escribe en la bitacora a
-- proposito. La fila hija toma un bloqueo sobre la cita recien insertada y lo
-- mantiene hasta el commit, dentro de la misma transaccion que sostiene la
-- restriccion de exclusion de V13; con varias reservas disputando el pool de
-- consultorios eso cerraba un ciclo de espera y PostgreSQL abortaba una con
-- «deadlock detected», rompiendo el criterio 2 de HU-10 bajo carga. Una columna
-- de la propia fila no anade ningun bloqueo en ese camino: la clave ajena solo
-- toma FOR KEY SHARE sobre la fila de `usuarios`, que es compartido y no entra
-- en conflicto con las otras reservas.
--
-- RESTRICT por el mismo motivo que `citas_historial.usuario_id`: dar de baja una
-- cuenta no puede vaciar el «quien» de lo que esa persona ordeno. La baja de
-- usuarios es logica (`activo`), asi que no estorba.
--
-- Admite nulo porque las citas ya existentes no lo tienen y RN-12 impide
-- reescribirlas; una cita sin autor es una anterior a HU-14, no un dato perdido.

ALTER TABLE citas ADD COLUMN creado_por_usuario_id UUID;

ALTER TABLE citas ADD CONSTRAINT fk_citas_creado_por
    FOREIGN KEY (creado_por_usuario_id) REFERENCES usuarios(id) ON DELETE RESTRICT;

-- HU-15 lo consulta por cita, no por usuario, asi que el indice no es para
-- buscar: existe porque sin el, comprobar la clave ajena al dar de baja una
-- cuenta recorreria `citas` entera.
CREATE INDEX idx_citas_creado_por ON citas (creado_por_usuario_id);
