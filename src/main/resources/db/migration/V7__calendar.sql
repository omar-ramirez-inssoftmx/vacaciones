-- =====================================================================
-- V7 — Calendar: calendario laboral, festivos y días especiales
-- ---------------------------------------------------------------------
-- Requiere V3 (organización) y V4 (catálogos).
-- =====================================================================

IF OBJECT_ID('dbo.cal_calendar', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.cal_calendar (
        id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_cal_calendar PRIMARY KEY,
        uuid        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_cal_calendar_uuid DEFAULT NEWID(),
        enabled     BIT NOT NULL CONSTRAINT DF_cal_calendar_enabled DEFAULT 1,
        created_at  DATETIME2(3) NOT NULL CONSTRAINT DF_cal_calendar_created_at DEFAULT SYSUTCDATETIME(),
        updated_at  DATETIME2(3) NULL,
        version     INT NOT NULL CONSTRAINT DF_cal_calendar_version DEFAULT 0,
        deleted_at  DATETIME2(3) NULL,
        company_id  BIGINT NOT NULL,
        name        NVARCHAR(150) NOT NULL,
        timezone    NVARCHAR(64)  NOT NULL CONSTRAINT DF_cal_calendar_timezone DEFAULT 'America/Mexico_City',
        workweek    NVARCHAR(50)  NOT NULL CONSTRAINT DF_cal_calendar_workweek DEFAULT 'MON,TUE,WED,THU,FRI',
        CONSTRAINT FK_cal_calendar_company FOREIGN KEY (company_id) REFERENCES dbo.org_company(id)
    );

    CREATE UNIQUE INDEX UX_cal_calendar_uuid ON dbo.cal_calendar(uuid);
    CREATE UNIQUE INDEX UX_cal_calendar_company ON dbo.cal_calendar(company_id)
        WHERE deleted_at IS NULL;
END;

IF OBJECT_ID('dbo.cal_holiday', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.cal_holiday (
        id           BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_cal_holiday PRIMARY KEY,
        uuid         UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_cal_holiday_uuid DEFAULT NEWID(),
        enabled      BIT NOT NULL CONSTRAINT DF_cal_holiday_enabled DEFAULT 1,
        created_at   DATETIME2(3) NOT NULL CONSTRAINT DF_cal_holiday_created_at DEFAULT SYSUTCDATETIME(),
        updated_at   DATETIME2(3) NULL,
        version      INT NOT NULL CONSTRAINT DF_cal_holiday_version DEFAULT 0,
        deleted_at   DATETIME2(3) NULL,
        company_id   BIGINT NULL,
        name         NVARCHAR(150) NOT NULL,
        holiday_date DATE NOT NULL,
        type         NVARCHAR(20) NOT NULL,
        year         INT NOT NULL,
        description  NVARCHAR(500) NULL,
        CONSTRAINT FK_cal_holiday_company FOREIGN KEY (company_id) REFERENCES dbo.org_company(id),
        CONSTRAINT CK_cal_holiday_year CHECK (year = YEAR(holiday_date)),
        CONSTRAINT CK_cal_holiday_type CHECK (type IN ('OFFICIAL', 'COMPANY'))
    );

    CREATE UNIQUE INDEX UX_cal_holiday_uuid ON dbo.cal_holiday(uuid);
    CREATE UNIQUE INDEX UX_cal_holiday_company_date ON dbo.cal_holiday(company_id, holiday_date, type)
        WHERE company_id IS NOT NULL AND deleted_at IS NULL;
    CREATE UNIQUE INDEX UX_cal_holiday_official_date ON dbo.cal_holiday(holiday_date, type)
        WHERE company_id IS NULL AND deleted_at IS NULL;
    CREATE INDEX IX_cal_holiday_company_date ON dbo.cal_holiday(company_id, holiday_date, enabled);
    CREATE INDEX IX_cal_holiday_year ON dbo.cal_holiday(year, type);
END;

IF OBJECT_ID('dbo.cal_special_day', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.cal_special_day (
        id               BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_cal_special_day PRIMARY KEY,
        uuid             UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_cal_special_day_uuid DEFAULT NEWID(),
        enabled          BIT NOT NULL CONSTRAINT DF_cal_special_day_enabled DEFAULT 1,
        created_at       DATETIME2(3) NOT NULL CONSTRAINT DF_cal_special_day_created_at DEFAULT SYSUTCDATETIME(),
        updated_at       DATETIME2(3) NULL,
        version          INT NOT NULL CONSTRAINT DF_cal_special_day_version DEFAULT 0,
        deleted_at       DATETIME2(3) NULL,
        company_id       BIGINT NOT NULL,
        name             NVARCHAR(150) NOT NULL,
        start_date       DATE NOT NULL,
        end_date         DATE NOT NULL,
        type             NVARCHAR(20) NOT NULL,
        affects_workdays BIT NOT NULL CONSTRAINT DF_cal_special_day_affects DEFAULT 0,
        description      NVARCHAR(500) NULL,
        CONSTRAINT FK_cal_special_day_company FOREIGN KEY (company_id) REFERENCES dbo.org_company(id),
        CONSTRAINT CK_cal_special_day_dates CHECK (end_date >= start_date),
        CONSTRAINT CK_cal_special_day_type CHECK (type IN ('EVENT', 'CLOSURE', 'SPECIAL'))
    );

    CREATE UNIQUE INDEX UX_cal_special_day_uuid ON dbo.cal_special_day(uuid);
    CREATE INDEX IX_cal_special_day_company_dates
        ON dbo.cal_special_day(company_id, start_date, end_date, enabled);
END;

-- ---------------------------------------------------------------------
-- Calendario default por empresa
-- ---------------------------------------------------------------------
INSERT INTO dbo.cal_calendar (company_id, name, timezone, workweek)
SELECT c.id, LEFT(CONCAT('Calendario ', c.legal_name), 150), c.timezone, 'MON,TUE,WED,THU,FRI'
FROM dbo.org_company c
WHERE c.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM dbo.cal_calendar cal
      WHERE cal.company_id = c.id AND cal.deleted_at IS NULL
  );

-- ---------------------------------------------------------------------
-- Festivos oficiales México 2024–2030 (globales, company_id = NULL)
-- ---------------------------------------------------------------------
INSERT INTO dbo.cal_holiday (company_id, name, holiday_date, type, year, description)
SELECT NULL, v.name, TRY_CONVERT(DATE, v.holiday_date), 'OFFICIAL', v.year, 'Feriado oficial México'
FROM (VALUES
    ('2024-01-01', 'Año Nuevo', 2024),
    ('2024-02-05', 'Día de la Constitución', 2024),
    ('2024-03-18', 'Natalicio de Benito Juárez', 2024),
    ('2024-05-01', 'Día del Trabajo', 2024),
    ('2024-09-16', 'Día de la Independencia', 2024),
    ('2024-11-18', 'Revolución Mexicana', 2024),
    ('2024-12-25', 'Navidad', 2024),
    ('2025-01-01', 'Año Nuevo', 2025),
    ('2025-02-03', 'Día de la Constitución', 2025),
    ('2025-03-17', 'Natalicio de Benito Juárez', 2025),
    ('2025-05-01', 'Día del Trabajo', 2025),
    ('2025-09-16', 'Día de la Independencia', 2025),
    ('2025-11-17', 'Revolución Mexicana', 2025),
    ('2025-12-25', 'Navidad', 2025),
    ('2026-01-01', 'Año Nuevo', 2026),
    ('2026-02-02', 'Día de la Constitución', 2026),
    ('2026-03-16', 'Natalicio de Benito Juárez', 2026),
    ('2026-05-01', 'Día del Trabajo', 2026),
    ('2026-09-16', 'Día de la Independencia', 2026),
    ('2026-11-16', 'Revolución Mexicana', 2026),
    ('2026-12-25', 'Navidad', 2026),
    ('2027-01-01', 'Año Nuevo', 2027),
    ('2027-02-01', 'Día de la Constitución', 2027),
    ('2027-03-15', 'Natalicio de Benito Juárez', 2027),
    ('2027-05-01', 'Día del Trabajo', 2027),
    ('2027-09-16', 'Día de la Independencia', 2027),
    ('2027-11-15', 'Revolución Mexicana', 2027),
    ('2027-12-25', 'Navidad', 2027),
    ('2028-01-01', 'Año Nuevo', 2028),
    ('2028-02-07', 'Día de la Constitución', 2028),
    ('2028-03-20', 'Natalicio de Benito Juárez', 2028),
    ('2028-05-01', 'Día del Trabajo', 2028),
    ('2028-09-16', 'Día de la Independencia', 2028),
    ('2028-11-20', 'Revolución Mexicana', 2028),
    ('2028-12-25', 'Navidad', 2028),
    ('2029-01-01', 'Año Nuevo', 2029),
    ('2029-02-05', 'Día de la Constitución', 2029),
    ('2029-03-19', 'Natalicio de Benito Juárez', 2029),
    ('2029-05-01', 'Día del Trabajo', 2029),
    ('2029-09-16', 'Día de la Independencia', 2029),
    ('2029-11-19', 'Revolución Mexicana', 2029),
    ('2029-12-25', 'Navidad', 2029),
    ('2030-01-01', 'Año Nuevo', 2030),
    ('2030-02-04', 'Día de la Constitución', 2030),
    ('2030-03-18', 'Natalicio de Benito Juárez', 2030),
    ('2030-05-01', 'Día del Trabajo', 2030),
    ('2030-09-16', 'Día de la Independencia', 2030),
    ('2030-11-18', 'Revolución Mexicana', 2030),
    ('2030-12-25', 'Navidad', 2030)
) AS v(holiday_date, name, year)
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.cal_holiday h
    WHERE h.company_id IS NULL
      AND h.type = 'OFFICIAL'
      AND h.holiday_date = TRY_CONVERT(DATE, v.holiday_date)
      AND h.deleted_at IS NULL
);
