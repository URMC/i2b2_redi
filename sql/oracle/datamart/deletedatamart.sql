DROP USER i2b2mart"projectcode" CASCADE;
DROP USER i2b2data"projectcode" CASCADE;
DROP USER i2b2meta"projectcode" CASCADE;
DROP TABLESPACE "projectcode" INCLUDING CONTENTS AND DATAFILES;
delete from i2b2hive.CRC_DB_LOOKUP where C_DB_NICENAME = '"projectcode"';
delete from i2b2hive.ONT_DB_LOOKUP where C_DB_NICENAME = 'Mart"projectcode"';
delete from i2b2hive.ONT_DB_LOOKUP where C_DB_NICENAME = 'Data"projectcode"';
delete from i2b2hive.ONT_DB_LOOKUP where C_DB_NICENAME = 'Meta"projectcode"';
delete from i2b2hive.WORK_DB_LOOKUP where C_DB_NICENAME = 'Work"projectcode"';
delete from I2B2PM.pm_project_data where project_id = '"projectcode"';
delete from I2B2PM.pm_project_user_roles where project_id = '"projectcode"' and user_id = 'AGG_SERVICE_ACCOUNT';