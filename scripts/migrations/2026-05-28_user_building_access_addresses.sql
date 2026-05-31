-- Расширяем хранение прав на нагрузку по корпусам.
-- Теперь значение может быть кодом всего СП (например, СП3) или конкретным адресом СП (например, СП3|УЛ.ЕЛАГИНА,Д.1).

BEGIN;

ALTER TABLE app_user
    ALTER COLUMN "managedBuildingCode" TYPE VARCHAR(80);

ALTER TABLE app_user_load_building
    ALTER COLUMN building_code TYPE VARCHAR(255);

COMMIT;
