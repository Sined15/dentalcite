-- V10__check_duracion_multiplo_de_quince.sql
-- RN-04: «La duración de la cita es la del tratamiento, múltiplo de quince
-- minutos», implementada en «el servicio de disponibilidad y validación en la
-- base de datos». El CHECK de V4 solo cubría el rango 15–240, de modo que la
-- mitad de la regla que corresponde a la base quedaba sin aplicar: una escritura
-- que no pasara por TratamientoService podía dejar una duración de 20 minutos,
-- que el motor de disponibilidad (HU-08) no sabría recorrer en incrementos de
-- quince.
--
-- Si esta migración falla, hay filas previas con una duración no múltiplo de 15:
-- corríjanse antes de reintentar, en lugar de relajar la restricción.

ALTER TABLE tratamientos DROP CONSTRAINT chk_tratamiento_duracion;

ALTER TABLE tratamientos ADD CONSTRAINT chk_tratamiento_duracion
    CHECK (duracion_minutos BETWEEN 15 AND 240 AND duracion_minutos % 15 = 0);
