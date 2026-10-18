-- =====================================================================
-- V4 — Catálogos maestros (master data) + seeds iniciales
-- =====================================================================

IF OBJECT_ID('dbo.md_catalog', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.md_catalog (
        id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_md_catalog PRIMARY KEY,
        uuid        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_md_catalog_uuid DEFAULT NEWID(),
        enabled     BIT NOT NULL CONSTRAINT DF_md_catalog_enabled DEFAULT 1,
        created_at  DATETIME2(3) NOT NULL CONSTRAINT DF_md_catalog_created_at DEFAULT SYSUTCDATETIME(),
        updated_at  DATETIME2(3) NULL,
        version     INT NOT NULL CONSTRAINT DF_md_catalog_version DEFAULT 0,
        deleted_at  DATETIME2(3) NULL,
        code        NVARCHAR(50)  NOT NULL,
        name        NVARCHAR(150) NOT NULL,
        description NVARCHAR(500) NULL
    );

    CREATE UNIQUE INDEX UX_md_catalog_uuid ON dbo.md_catalog(uuid);
    CREATE UNIQUE INDEX UX_md_catalog_code ON dbo.md_catalog(code)
        WHERE deleted_at IS NULL;
END;

IF OBJECT_ID('dbo.md_catalog_item', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.md_catalog_item (
        id            BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_md_catalog_item PRIMARY KEY,
        uuid          UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_md_catalog_item_uuid DEFAULT NEWID(),
        enabled       BIT NOT NULL CONSTRAINT DF_md_catalog_item_enabled DEFAULT 1,
        created_at    DATETIME2(3) NOT NULL CONSTRAINT DF_md_catalog_item_created_at DEFAULT SYSUTCDATETIME(),
        updated_at    DATETIME2(3) NULL,
        version       INT NOT NULL CONSTRAINT DF_md_catalog_item_version DEFAULT 0,
        deleted_at    DATETIME2(3) NULL,
        catalog_id    BIGINT NOT NULL,
        code          NVARCHAR(50)  NOT NULL,
        name          NVARCHAR(150) NOT NULL,
        description   NVARCHAR(500) NULL,
        sort_order    INT NOT NULL CONSTRAINT DF_md_catalog_item_sort_order DEFAULT 0,
        metadata_json NVARCHAR(MAX) NULL,
        CONSTRAINT FK_md_catalog_item_catalog FOREIGN KEY (catalog_id) REFERENCES dbo.md_catalog(id)
    );

    CREATE UNIQUE INDEX UX_md_catalog_item_uuid ON dbo.md_catalog_item(uuid);
    CREATE UNIQUE INDEX UX_md_catalog_item_catalog_code ON dbo.md_catalog_item(catalog_id, code)
        WHERE deleted_at IS NULL;
    CREATE INDEX IX_md_catalog_item_catalog ON dbo.md_catalog_item(catalog_id, enabled, sort_order);
END;

-- ---------------------------------------------------------------------
-- Catálogos
-- ---------------------------------------------------------------------
INSERT INTO dbo.md_catalog (uuid, code, name, description, enabled, created_at, version)
SELECT NEWID(), v.code, v.name, v.description, 1, SYSUTCDATETIME(), 0
FROM (VALUES
    ('GENDER',            N'Género',                 N'Identidad de género'),
    ('MARITAL_STATUS',    N'Estado civil',           N'Estado civil del empleado'),
    ('EMPLOYMENT_STATUS', N'Estatus de empleo',      N'Estatus laboral'),
    ('WORK_MODALITY',     N'Modalidad de trabajo',   N'Presencial, híbrido o teletrabajo'),
    ('CONTRACT_TYPE',     N'Tipo de contrato',       N'Tipo de relación laboral'),
    ('EDUCATION_LEVEL',   N'Nivel educativo',        N'Último nivel de estudios'),
    ('DOCUMENT_TYPE',     N'Tipo de documento',      N'Clasificación de documentos'),
    ('INCIDENT_TYPE',     N'Tipo de incidencia',     N'Retardos, faltas, permisos, etc.'),
    ('LEAVE_TYPE',        N'Tipo de permiso',        N'Motivos de ausencia'),
    ('HOLIDAY_TYPE',      N'Tipo de día festivo',    N'Oficial o de empresa')
) AS v(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM dbo.md_catalog c WHERE c.code = v.code);

-- ---------------------------------------------------------------------
-- Items por catálogo
-- ---------------------------------------------------------------------
INSERT INTO dbo.md_catalog_item (uuid, catalog_id, code, name, sort_order, enabled, created_at, version)
SELECT NEWID(), c.id, v.code, v.name, v.sort_order, 1, SYSUTCDATETIME(), 0
FROM dbo.md_catalog c
CROSS APPLY (VALUES
    ('MASCULINO', N'Masculino', 1),
    ('FEMENINO', N'Femenino', 2),
    ('OTRO', N'Otro', 3),
    ('PREFIERO_NO_DECIR', N'Prefiero no decir', 4)
) AS v(code, name, sort_order)
WHERE c.code = 'GENDER'
  AND NOT EXISTS (SELECT 1 FROM dbo.md_catalog_item i WHERE i.catalog_id = c.id AND i.code = v.code);

INSERT INTO dbo.md_catalog_item (uuid, catalog_id, code, name, sort_order, enabled, created_at, version)
SELECT NEWID(), c.id, v.code, v.name, v.sort_order, 1, SYSUTCDATETIME(), 0
FROM dbo.md_catalog c
CROSS APPLY (VALUES
    ('SOLTERO', N'Soltero(a)', 1),
    ('CASADO', N'Casado(a)', 2),
    ('DIVORCIADO', N'Divorciado(a)', 3),
    ('VIUDO', N'Viudo(a)', 4),
    ('UNION_LIBRE', N'Unión libre', 5)
) AS v(code, name, sort_order)
WHERE c.code = 'MARITAL_STATUS'
  AND NOT EXISTS (SELECT 1 FROM dbo.md_catalog_item i WHERE i.catalog_id = c.id AND i.code = v.code);

INSERT INTO dbo.md_catalog_item (uuid, catalog_id, code, name, sort_order, enabled, created_at, version)
SELECT NEWID(), c.id, v.code, v.name, v.sort_order, 1, SYSUTCDATETIME(), 0
FROM dbo.md_catalog c
CROSS APPLY (VALUES
    ('ACTIVO', N'Activo', 1),
    ('BAJA', N'Baja', 2),
    ('SUSPENDIDO', N'Suspendido', 3),
    ('PERMISO', N'Permiso', 4)
) AS v(code, name, sort_order)
WHERE c.code = 'EMPLOYMENT_STATUS'
  AND NOT EXISTS (SELECT 1 FROM dbo.md_catalog_item i WHERE i.catalog_id = c.id AND i.code = v.code);

INSERT INTO dbo.md_catalog_item (uuid, catalog_id, code, name, sort_order, enabled, created_at, version)
SELECT NEWID(), c.id, v.code, v.name, v.sort_order, 1, SYSUTCDATETIME(), 0
FROM dbo.md_catalog c
CROSS APPLY (VALUES
    ('PRESENCIAL', N'Presencial', 1),
    ('HIBRIDO', N'Híbrido', 2),
    ('TELETRABAJO', N'Teletrabajo', 3)
) AS v(code, name, sort_order)
WHERE c.code = 'WORK_MODALITY'
  AND NOT EXISTS (SELECT 1 FROM dbo.md_catalog_item i WHERE i.catalog_id = c.id AND i.code = v.code);

INSERT INTO dbo.md_catalog_item (uuid, catalog_id, code, name, sort_order, enabled, created_at, version)
SELECT NEWID(), c.id, v.code, v.name, v.sort_order, 1, SYSUTCDATETIME(), 0
FROM dbo.md_catalog c
CROSS APPLY (VALUES
    ('INDEFINIDO', N'Indefinido', 1),
    ('TEMPORAL', N'Temporal', 2),
    ('POR_OBRA', N'Por obra', 3),
    ('PRACTICAS', N'Prácticas', 4)
) AS v(code, name, sort_order)
WHERE c.code = 'CONTRACT_TYPE'
  AND NOT EXISTS (SELECT 1 FROM dbo.md_catalog_item i WHERE i.catalog_id = c.id AND i.code = v.code);

INSERT INTO dbo.md_catalog_item (uuid, catalog_id, code, name, sort_order, enabled, created_at, version)
SELECT NEWID(), c.id, v.code, v.name, v.sort_order, 1, SYSUTCDATETIME(), 0
FROM dbo.md_catalog c
CROSS APPLY (VALUES
    ('PRIMARIA', N'Primaria', 1),
    ('SECUNDARIA', N'Secundaria', 2),
    ('BACHILLERATO', N'Bachillerato', 3),
    ('TECNICO', N'Técnico', 4),
    ('LICENCIATURA', N'Licenciatura', 5),
    ('MAESTRIA', N'Maestría', 6),
    ('DOCTORADO', N'Doctorado', 7)
) AS v(code, name, sort_order)
WHERE c.code = 'EDUCATION_LEVEL'
  AND NOT EXISTS (SELECT 1 FROM dbo.md_catalog_item i WHERE i.catalog_id = c.id AND i.code = v.code);

INSERT INTO dbo.md_catalog_item (uuid, catalog_id, code, name, sort_order, enabled, created_at, version)
SELECT NEWID(), c.id, v.code, v.name, v.sort_order, 1, SYSUTCDATETIME(), 0
FROM dbo.md_catalog c
CROSS APPLY (VALUES
    ('CONTRATO', N'Contrato', 1),
    ('IDENTIFICACION', N'Identificación', 2),
    ('CURP', N'CURP', 3),
    ('RFC', N'RFC', 4),
    ('COMPROBANTE_DOMICILIO', N'Comprobante de domicilio', 5),
    ('CONSTANCIA', N'Constancia', 6),
    ('RECIBO_NOMINA', N'Recibo de nómina', 7),
    ('POLITICA', N'Política interna', 8),
    ('TELETRABAJO', N'Documento de teletrabajo', 9),
    ('OTRO', N'Otro', 10)
) AS v(code, name, sort_order)
WHERE c.code = 'DOCUMENT_TYPE'
  AND NOT EXISTS (SELECT 1 FROM dbo.md_catalog_item i WHERE i.catalog_id = c.id AND i.code = v.code);

INSERT INTO dbo.md_catalog_item (uuid, catalog_id, code, name, sort_order, enabled, created_at, version)
SELECT NEWID(), c.id, v.code, v.name, v.sort_order, 1, SYSUTCDATETIME(), 0
FROM dbo.md_catalog c
CROSS APPLY (VALUES
    ('RETARDO', N'Retardo', 1),
    ('FALTA', N'Falta', 2),
    ('PERMISO', N'Permiso', 3),
    ('INCAPACIDAD', N'Incapacidad', 4),
    ('AUSENCIA', N'Ausencia', 5),
    ('HORA_EXTRA', N'Hora extra', 6),
    ('ADMINISTRATIVA', N'Incidencia administrativa', 7)
) AS v(code, name, sort_order)
WHERE c.code = 'INCIDENT_TYPE'
  AND NOT EXISTS (SELECT 1 FROM dbo.md_catalog_item i WHERE i.catalog_id = c.id AND i.code = v.code);

INSERT INTO dbo.md_catalog_item (uuid, catalog_id, code, name, sort_order, enabled, created_at, version)
SELECT NEWID(), c.id, v.code, v.name, v.sort_order, 1, SYSUTCDATETIME(), 0
FROM dbo.md_catalog c
CROSS APPLY (VALUES
    ('VACACIONES', N'Vacaciones', 1),
    ('PERSONAL', N'Asunto personal', 2),
    ('LUTO', N'Luto', 3),
    ('MATERNIDAD', N'Maternidad', 4),
    ('PATERNIDAD', N'Paternidad', 5),
    ('ESTUDIO', N'Estudio', 6)
) AS v(code, name, sort_order)
WHERE c.code = 'LEAVE_TYPE'
  AND NOT EXISTS (SELECT 1 FROM dbo.md_catalog_item i WHERE i.catalog_id = c.id AND i.code = v.code);

INSERT INTO dbo.md_catalog_item (uuid, catalog_id, code, name, sort_order, enabled, created_at, version)
SELECT NEWID(), c.id, v.code, v.name, v.sort_order, 1, SYSUTCDATETIME(), 0
FROM dbo.md_catalog c
CROSS APPLY (VALUES
    ('OFICIAL', N'Oficial', 1),
    ('EMPRESA', N'Empresa', 2)
) AS v(code, name, sort_order)
WHERE c.code = 'HOLIDAY_TYPE'
  AND NOT EXISTS (SELECT 1 FROM dbo.md_catalog_item i WHERE i.catalog_id = c.id AND i.code = v.code);
