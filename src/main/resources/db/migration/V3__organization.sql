-- =====================================================================
-- V3 — Organización: Company → Branch → Department → Position
-- =====================================================================

IF OBJECT_ID('dbo.org_company', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.org_company (
        id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_org_company PRIMARY KEY,
        uuid        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_org_company_uuid DEFAULT NEWID(),
        enabled     BIT NOT NULL CONSTRAINT DF_org_company_enabled DEFAULT 1,
        created_at  DATETIME2(3) NOT NULL CONSTRAINT DF_org_company_created_at DEFAULT SYSUTCDATETIME(),
        updated_at  DATETIME2(3) NULL,
        version     INT NOT NULL CONSTRAINT DF_org_company_version DEFAULT 0,
        deleted_at  DATETIME2(3) NULL,
        code        NVARCHAR(30)  NOT NULL,
        legal_name  NVARCHAR(200) NOT NULL,
        trade_name  NVARCHAR(200) NULL,
        rfc         NVARCHAR(13)  NULL,
        timezone    NVARCHAR(64)  NOT NULL CONSTRAINT DF_org_company_timezone DEFAULT 'America/Mexico_City'
    );

    CREATE UNIQUE INDEX UX_org_company_uuid ON dbo.org_company(uuid);
    CREATE UNIQUE INDEX UX_org_company_code ON dbo.org_company(code)
        WHERE deleted_at IS NULL;
    CREATE INDEX IX_org_company_enabled ON dbo.org_company(enabled, legal_name);
END;

IF OBJECT_ID('dbo.org_branch', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.org_branch (
        id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_org_branch PRIMARY KEY,
        uuid        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_org_branch_uuid DEFAULT NEWID(),
        enabled     BIT NOT NULL CONSTRAINT DF_org_branch_enabled DEFAULT 1,
        created_at  DATETIME2(3) NOT NULL CONSTRAINT DF_org_branch_created_at DEFAULT SYSUTCDATETIME(),
        updated_at  DATETIME2(3) NULL,
        version     INT NOT NULL CONSTRAINT DF_org_branch_version DEFAULT 0,
        deleted_at  DATETIME2(3) NULL,
        company_id  BIGINT NOT NULL,
        code        NVARCHAR(30)  NOT NULL,
        name        NVARCHAR(150) NOT NULL,
        address     NVARCHAR(300) NULL,
        phone       NVARCHAR(30)  NULL,
        CONSTRAINT FK_org_branch_company FOREIGN KEY (company_id) REFERENCES dbo.org_company(id)
    );

    CREATE UNIQUE INDEX UX_org_branch_uuid ON dbo.org_branch(uuid);
    CREATE UNIQUE INDEX UX_org_branch_company_code ON dbo.org_branch(company_id, code)
        WHERE deleted_at IS NULL;
    CREATE INDEX IX_org_branch_company ON dbo.org_branch(company_id, enabled);
END;

IF OBJECT_ID('dbo.org_department', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.org_department (
        id                   BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_org_department PRIMARY KEY,
        uuid                 UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_org_department_uuid DEFAULT NEWID(),
        enabled              BIT NOT NULL CONSTRAINT DF_org_department_enabled DEFAULT 1,
        created_at           DATETIME2(3) NOT NULL CONSTRAINT DF_org_department_created_at DEFAULT SYSUTCDATETIME(),
        updated_at           DATETIME2(3) NULL,
        version              INT NOT NULL CONSTRAINT DF_org_department_version DEFAULT 0,
        deleted_at           DATETIME2(3) NULL,
        branch_id            BIGINT NOT NULL,
        parent_department_id BIGINT NULL,
        code                 NVARCHAR(30)  NOT NULL,
        name                 NVARCHAR(150) NOT NULL,
        description          NVARCHAR(500) NULL,
        CONSTRAINT FK_org_department_branch FOREIGN KEY (branch_id) REFERENCES dbo.org_branch(id),
        CONSTRAINT FK_org_department_parent FOREIGN KEY (parent_department_id) REFERENCES dbo.org_department(id)
    );

    CREATE UNIQUE INDEX UX_org_department_uuid ON dbo.org_department(uuid);
    CREATE UNIQUE INDEX UX_org_department_branch_code ON dbo.org_department(branch_id, code)
        WHERE deleted_at IS NULL;
    CREATE INDEX IX_org_department_branch ON dbo.org_department(branch_id, enabled);
    CREATE INDEX IX_org_department_parent ON dbo.org_department(parent_department_id);
END;

IF OBJECT_ID('dbo.org_position', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.org_position (
        id            BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_org_position PRIMARY KEY,
        uuid          UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_org_position_uuid DEFAULT NEWID(),
        enabled       BIT NOT NULL CONSTRAINT DF_org_position_enabled DEFAULT 1,
        created_at    DATETIME2(3) NOT NULL CONSTRAINT DF_org_position_created_at DEFAULT SYSUTCDATETIME(),
        updated_at    DATETIME2(3) NULL,
        version       INT NOT NULL CONSTRAINT DF_org_position_version DEFAULT 0,
        deleted_at    DATETIME2(3) NULL,
        department_id BIGINT NOT NULL,
        code          NVARCHAR(30)  NOT NULL,
        name          NVARCHAR(150) NOT NULL,
        description   NVARCHAR(500) NULL,
        job_level     NVARCHAR(50)  NULL,
        CONSTRAINT FK_org_position_department FOREIGN KEY (department_id) REFERENCES dbo.org_department(id)
    );

    CREATE UNIQUE INDEX UX_org_position_uuid ON dbo.org_position(uuid);
    CREATE UNIQUE INDEX UX_org_position_department_code ON dbo.org_position(department_id, code)
        WHERE deleted_at IS NULL;
    CREATE INDEX IX_org_position_department ON dbo.org_position(department_id, enabled);
END;
