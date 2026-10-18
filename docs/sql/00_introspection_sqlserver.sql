/* =====================================================================
   INSSOFT PEOPLE — Introspección de base de datos (SQL Server)
   ---------------------------------------------------------------------
   PROPÓSITO : Capturar el esquema real ANTES de crear la migración V1.
   SEGURIDAD : Script de SOLO LECTURA. No modifica datos ni estructura.
   EJECUTAR  : Sobre una COPIA de la base (idealmente restaurada en un
               servidor de pruebas), con un usuario de solo lectura.
   MOTIVO    : El esquema del repositorio se infiere de JPA; la base real
               puede diferir (columnas, índices, constraints, datos).
   ===================================================================== */

SET NOCOUNT ON;
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;

/* ---------------------------------------------------------------------
   0. Contexto
   --------------------------------------------------------------------- */
SELECT
    DB_NAME()                        AS database_name,
    SUSER_SNAME()                    AS login_name,
    @@VERSION                        AS sql_server_version,
    SYSDATETIME()                    AS captured_at,
    SERVERPROPERTY('Collation')      AS server_collation;

/* ---------------------------------------------------------------------
   1. Tablas y conteo aproximado de filas
   --------------------------------------------------------------------- */
SELECT
    s.name  AS schema_name,
    t.name  AS table_name,
    SUM(p.rows) AS row_count
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
JOIN sys.partitions p
     ON p.object_id = t.object_id
    AND p.index_id IN (0, 1)          -- heap o clustered index
WHERE t.is_ms_shipped = 0
GROUP BY s.name, t.name
ORDER BY s.name, t.name;

/* ---------------------------------------------------------------------
   2. Columnas: tipo, longitud, precisión, nulabilidad, default
   --------------------------------------------------------------------- */
SELECT
    c.TABLE_SCHEMA,
    c.TABLE_NAME,
    c.ORDINAL_POSITION,
    c.COLUMN_NAME,
    c.DATA_TYPE,
    c.CHARACTER_MAXIMUM_LENGTH,
    c.NUMERIC_PRECISION,
    c.NUMERIC_SCALE,
    c.IS_NULLABLE,
    c.COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS c
ORDER BY c.TABLE_SCHEMA, c.TABLE_NAME, c.ORDINAL_POSITION;

/* ---------------------------------------------------------------------
   3. Columnas identity
   --------------------------------------------------------------------- */
SELECT
    OBJECT_SCHEMA_NAME(ic.object_id) AS schema_name,
    OBJECT_NAME(ic.object_id)        AS table_name,
    ic.name                          AS column_name,
    ic.seed_value,
    ic.increment_value,
    ic.last_value
FROM sys.identity_columns ic
ORDER BY schema_name, table_name;

/* ---------------------------------------------------------------------
   4. Primary keys y unique constraints
   --------------------------------------------------------------------- */
SELECT
    OBJECT_SCHEMA_NAME(kc.parent_object_id) AS schema_name,
    OBJECT_NAME(kc.parent_object_id)        AS table_name,
    kc.name                                 AS constraint_name,
    kc.type_desc                            AS constraint_type,
    c.name                                  AS column_name,
    ic.key_ordinal
FROM sys.key_constraints kc
JOIN sys.index_columns ic
     ON ic.object_id = kc.parent_object_id
    AND ic.index_id  = kc.unique_index_id
JOIN sys.columns c
     ON c.object_id = ic.object_id
    AND c.column_id = ic.column_id
ORDER BY schema_name, table_name, constraint_name, ic.key_ordinal;

/* ---------------------------------------------------------------------
   5. Foreign keys
   --------------------------------------------------------------------- */
SELECT
    OBJECT_SCHEMA_NAME(fk.parent_object_id)      AS schema_name,
    OBJECT_NAME(fk.parent_object_id)             AS table_name,
    fk.name                                      AS fk_name,
    COL_NAME(fkc.parent_object_id, fkc.parent_column_id) AS column_name,
    OBJECT_SCHEMA_NAME(fk.referenced_object_id)  AS ref_schema,
    OBJECT_NAME(fk.referenced_object_id)         AS ref_table,
    COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) AS ref_column,
    fk.delete_referential_action_desc            AS on_delete,
    fk.update_referential_action_desc            AS on_update,
    fk.is_disabled
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc
     ON fkc.constraint_object_id = fk.object_id
ORDER BY schema_name, table_name, fk_name, fkc.constraint_column_id;

/* ---------------------------------------------------------------------
   6. Índices (incluye unique, filtrados, include)
   --------------------------------------------------------------------- */
SELECT
    OBJECT_SCHEMA_NAME(i.object_id) AS schema_name,
    OBJECT_NAME(i.object_id)        AS table_name,
    i.name                          AS index_name,
    i.type_desc,
    i.is_unique,
    i.is_unique_constraint,
    i.has_filter,
    i.filter_definition,
    c.name                          AS column_name,
    ic.key_ordinal,
    ic.is_included_column
FROM sys.indexes i
JOIN sys.index_columns ic
     ON ic.object_id = i.object_id
    AND ic.index_id  = i.index_id
JOIN sys.columns c
     ON c.object_id = ic.object_id
    AND c.column_id = ic.column_id
WHERE i.object_id IN (SELECT object_id FROM sys.tables WHERE is_ms_shipped = 0)
ORDER BY schema_name, table_name, i.name, ic.key_ordinal, ic.index_column_id;

/* ---------------------------------------------------------------------
   7. Check constraints y defaults
   --------------------------------------------------------------------- */
SELECT
    OBJECT_SCHEMA_NAME(cc.parent_object_id) AS schema_name,
    OBJECT_NAME(cc.parent_object_id)        AS table_name,
    cc.name                                 AS check_name,
    cc.definition,
    cc.is_disabled
FROM sys.check_constraints cc
ORDER BY schema_name, table_name, check_name;

SELECT
    OBJECT_SCHEMA_NAME(dc.parent_object_id) AS schema_name,
    OBJECT_NAME(dc.parent_object_id)        AS table_name,
    dc.name                                 AS default_name,
    c.name                                  AS column_name,
    dc.definition
FROM sys.default_constraints dc
JOIN sys.columns c
     ON c.object_id = dc.parent_object_id
    AND c.column_id = dc.parent_column_id
ORDER BY schema_name, table_name, column_name;

/* ---------------------------------------------------------------------
   8. Procedimientos, vistas, funciones y triggers
   --------------------------------------------------------------------- */
SELECT
    s.name AS schema_name,
    o.name,
    o.type_desc,
    o.create_date,
    o.modify_date
FROM sys.objects o
JOIN sys.schemas s ON s.schema_id = o.schema_id
WHERE o.type IN ('P', 'V', 'FN', 'IF', 'TF', 'TR')
  AND o.is_ms_shipped = 0
ORDER BY o.type_desc, s.name, o.name;

/* ---------------------------------------------------------------------
   9. Jobs de SQL Server (puede requerir permisos en msdb)
   --------------------------------------------------------------------- */
BEGIN TRY
    SELECT
        j.name        AS job_name,
        j.enabled,
        s.step_id,
        s.step_name,
        s.subsystem,
        s.command
    FROM msdb.dbo.sysjobs j
    JOIN msdb.dbo.sysjobsteps s ON s.job_id = j.job_id
    ORDER BY j.name, s.step_id;
END TRY
BEGIN CATCH
    SELECT 'Sin permiso para leer msdb.dbo.sysjobs' AS warning, ERROR_MESSAGE() AS detail;
END CATCH;

/* =====================================================================
   10. CALIDAD DE DATOS
   ---------------------------------------------------------------------
   Las siguientes consultas asumen los nombres inferidos por JPA.
   Si alguna columna no existe, ejecutar primero la sección 2 y ajustar.
   ===================================================================== */

/* 10.1 Distribución de estatus de solicitudes */
SELECT status, COUNT(*) AS total
FROM dbo.vacation_requests
GROUP BY status
ORDER BY total DESC;

/* 10.2 Solicitudes con fechas inválidas o días no positivos */
SELECT id, username, start_date, end_date, days, status
FROM dbo.vacation_requests
WHERE end_date < start_date
   OR days <= 0;

/* 10.3 Solicitudes traslapadas por usuario (estados bloqueantes) */
SELECT
    a.id            AS request_a,
    b.id            AS request_b,
    a.user_id,
    a.status        AS status_a,
    b.status        AS status_b,
    a.start_date    AS start_a,
    a.end_date      AS end_a,
    b.start_date    AS start_b,
    b.end_date      AS end_b
FROM dbo.vacation_requests a
JOIN dbo.vacation_requests b
     ON b.user_id = a.user_id
    AND b.id > a.id
    AND a.status IN ('PENDING', 'PENDING_LEADER', 'PENDING_ADMIN', 'APPROVED')
    AND b.status IN ('PENDING', 'PENDING_LEADER', 'PENDING_ADMIN', 'APPROVED')
    AND a.start_date <= b.end_date
    AND a.end_date   >= b.start_date
ORDER BY a.user_id, a.start_date;

/* 10.4 Saldos negativos o inconsistentes */
SELECT id, username, vacation_days_available, vacation_days_entitled, role, enabled
FROM dbo.users
WHERE vacation_days_available < 0
   OR vacation_days_available > vacation_days_entitled;

/* 10.5 Usuarios sin datos mínimos */
SELECT id, username, email, hire_date, role, enabled
FROM dbo.users
WHERE email IS NULL OR LTRIM(RTRIM(email)) = ''
   OR hire_date IS NULL
   OR role IS NULL;

/* 10.6 Emails duplicados (case-insensitive) */
SELECT LOWER(LTRIM(RTRIM(email))) AS email_normalized, COUNT(*) AS total
FROM dbo.users
WHERE email IS NOT NULL
GROUP BY LOWER(LTRIM(RTRIM(email)))
HAVING COUNT(*) > 1;

/* 10.7 Solicitudes huérfanas (si no hay FK, aquí aparecerán) */
SELECT vr.id, vr.user_id, vr.username
FROM dbo.vacation_requests vr
LEFT JOIN dbo.users u ON u.id = vr.user_id
WHERE u.id IS NULL;

SELECT vr.id, vr.leader_id
FROM dbo.vacation_requests vr
LEFT JOIN dbo.users u ON u.id = vr.leader_id
WHERE vr.leader_id IS NOT NULL AND u.id IS NULL;

/* 10.8 `username` denormalizado distinto al username real */
SELECT vr.id, vr.username AS request_username, u.username AS real_username
FROM dbo.vacation_requests vr
JOIN dbo.users u ON u.id = vr.user_id
WHERE LOWER(LTRIM(RTRIM(vr.username))) <> LOWER(LTRIM(RTRIM(u.username)));

/* 10.9 Días declarados vs. días hábiles (aprox. lunes-viernes; sin festivos) */
;WITH rango AS (
    SELECT
        vr.id,
        vr.start_date,
        vr.end_date,
        vr.days,
        DATEDIFF(DAY, vr.start_date, vr.end_date) + 1 AS calendar_days,
        (DATEDIFF(DAY, vr.start_date, vr.end_date) + 1)
        - (DATEDIFF(WEEK, vr.start_date, vr.end_date) * 2)
        - CASE WHEN DATENAME(WEEKDAY, vr.start_date) = 'Sunday' THEN 1 ELSE 0 END
        - CASE WHEN DATENAME(WEEKDAY, vr.end_date)   = 'Saturday' THEN 1 ELSE 0 END
          AS business_days_approx
    FROM dbo.vacation_requests vr
)
SELECT *
FROM rango
WHERE days <> business_days_approx
ORDER BY id;

/* 10.10 Reconciliación burda del saldo:
      disponible actual vs. (días correspondientes - días aprobados + rechazados)
      Nota: el sistema actual descuenta al crear y no repone al rechazar,
      por lo que diferencias son esperables. Documentar, no corregir aún. */
SELECT
    u.id,
    u.username,
    u.vacation_days_entitled,
    u.vacation_days_available,
    COALESCE(SUM(CASE WHEN vr.status = 'APPROVED' THEN vr.days ELSE 0 END), 0) AS approved_days,
    COALESCE(SUM(CASE WHEN vr.status IN ('PENDING','PENDING_LEADER','PENDING_ADMIN') THEN vr.days ELSE 0 END), 0) AS pending_days,
    u.vacation_days_entitled
      - COALESCE(SUM(CASE WHEN vr.status = 'APPROVED' THEN vr.days ELSE 0 END), 0)
      - COALESCE(SUM(CASE WHEN vr.status IN ('PENDING','PENDING_LEADER','PENDING_ADMIN') THEN vr.days ELSE 0 END), 0)
      AS expected_available
FROM dbo.users u
LEFT JOIN dbo.vacation_requests vr ON vr.user_id = u.id
GROUP BY u.id, u.username, u.vacation_days_entitled, u.vacation_days_available
ORDER BY u.id;

/* 10.11 Contenido de tablas legadas (verificar antes de decidir) */
SELECT COUNT(*) AS vacaciones_legacy_rows FROM dbo.vacaciones;
SELECT COUNT(*) AS vacation_history_rows   FROM dbo.vacation_history;

/* 10.12 Políticas sin PDF o con PDF vacío */
SELECT idpolitica, nombre_politica,
       CASE WHEN documento_pdf IS NULL THEN 'NULL' ELSE CAST(DATALENGTH(documento_pdf) AS VARCHAR(20)) END AS pdf_bytes
FROM dbo.tb_politicas_empresa;

/* 10.13 Tamaño de tablas e índices (para planear migración) */
SELECT
    OBJECT_SCHEMA_NAME(p.object_id) AS schema_name,
    OBJECT_NAME(p.object_id)        AS table_name,
    SUM(p.rows)                     AS rows,
    CAST(SUM(a.total_pages) * 8.0 / 1024 AS DECIMAL(10,2)) AS total_mb,
    CAST(SUM(a.used_pages)  * 8.0 / 1024 AS DECIMAL(10,2)) AS used_mb
FROM sys.partitions p
JOIN sys.allocation_units a ON a.container_id = p.partition_id
WHERE p.index_id IN (0, 1)
GROUP BY p.object_id
ORDER BY total_mb DESC;

/* =====================================================================
   11. Generación del DDL real
   ---------------------------------------------------------------------
   SQL Server no expone un "SHOW CREATE TABLE". Opciones:
   a) SSMS: clic derecho en la base → Tareas → Generar scripts
      (incluir tablas, índices, FKs, constraints, datos opcionalmente).
   b) mssql-scripter (CLI):
      mssql-scripter -S <server> -d vacaciones -U <user> -P <pwd> \
        --schema-and-data --file-per-object --output-folder ./schema
   c) Guardar el resultado como V1__baseline_legacy.sql (revisado).
   ===================================================================== */
