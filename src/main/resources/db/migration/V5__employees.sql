-- =====================================================================
-- V5 — Empleados, contactos de emergencia y migración users → employees
-- ---------------------------------------------------------------------
-- Requiere V3 (organización) y V4 (catálogos).
-- =====================================================================

IF OBJECT_ID('dbo.emp_employee', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.emp_employee (
        id                   BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_emp_employee PRIMARY KEY,
        uuid                 UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_emp_employee_uuid DEFAULT NEWID(),
        enabled              BIT NOT NULL CONSTRAINT DF_emp_employee_enabled DEFAULT 1,
        created_at           DATETIME2(3) NOT NULL CONSTRAINT DF_emp_employee_created_at DEFAULT SYSUTCDATETIME(),
        updated_at           DATETIME2(3) NULL,
        version              INT NOT NULL CONSTRAINT DF_emp_employee_version DEFAULT 0,
        deleted_at           DATETIME2(3) NULL,

        user_id              BIGINT NULL,
        company_id           BIGINT NOT NULL,
        branch_id            BIGINT NOT NULL,
        department_id        BIGINT NOT NULL,
        position_id          BIGINT NOT NULL,
        manager_id           BIGINT NULL,

        employee_number      NVARCHAR(30)  NOT NULL,
        first_name           NVARCHAR(100) NOT NULL,
        middle_name          NVARCHAR(100) NULL,
        last_name            NVARCHAR(100) NOT NULL,
        second_last_name     NVARCHAR(100) NULL,

        birth_date           DATE NULL,
        gender_id            BIGINT NULL,
        marital_status_id    BIGINT NULL,

        curp                 NVARCHAR(18) NULL,
        rfc                  NVARCHAR(13) NULL,
        nss                  NVARCHAR(11) NULL,

        corporate_email      NVARCHAR(150) NULL,
        personal_email       NVARCHAR(150) NULL,
        phone                NVARCHAR(20)  NULL,

        address_line1        NVARCHAR(200) NULL,
        address_line2        NVARCHAR(200) NULL,
        city                 NVARCHAR(100) NULL,
        state                NVARCHAR(100) NULL,
        postal_code          NVARCHAR(10)  NULL,
        country              NVARCHAR(100) NULL CONSTRAINT DF_emp_employee_country DEFAULT N'México',

        hire_date            DATE NOT NULL,
        termination_date     DATE NULL,
        termination_reason   NVARCHAR(500) NULL,

        status_id            BIGINT NOT NULL,
        work_modality_id     BIGINT NULL,

        CONSTRAINT FK_emp_employee_user       FOREIGN KEY (user_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_emp_employee_company    FOREIGN KEY (company_id) REFERENCES dbo.org_company(id),
        CONSTRAINT FK_emp_employee_branch     FOREIGN KEY (branch_id) REFERENCES dbo.org_branch(id),
        CONSTRAINT FK_emp_employee_department FOREIGN KEY (department_id) REFERENCES dbo.org_department(id),
        CONSTRAINT FK_emp_employee_position   FOREIGN KEY (position_id) REFERENCES dbo.org_position(id),
        CONSTRAINT FK_emp_employee_manager    FOREIGN KEY (manager_id) REFERENCES dbo.emp_employee(id),
        CONSTRAINT FK_emp_employee_gender     FOREIGN KEY (gender_id) REFERENCES dbo.md_catalog_item(id),
        CONSTRAINT FK_emp_employee_marital    FOREIGN KEY (marital_status_id) REFERENCES dbo.md_catalog_item(id),
        CONSTRAINT FK_emp_employee_status     FOREIGN KEY (status_id) REFERENCES dbo.md_catalog_item(id),
        CONSTRAINT FK_emp_employee_modality   FOREIGN KEY (work_modality_id) REFERENCES dbo.md_catalog_item(id),
        CONSTRAINT CK_emp_employee_dates
            CHECK (termination_date IS NULL OR termination_date >= hire_date),
        CONSTRAINT CK_emp_employee_birth
            CHECK (birth_date IS NULL OR birth_date < hire_date)
    );

    CREATE UNIQUE INDEX UX_emp_employee_uuid ON dbo.emp_employee(uuid);
    CREATE UNIQUE INDEX UX_emp_employee_user ON dbo.emp_employee(user_id)
        WHERE user_id IS NOT NULL AND deleted_at IS NULL;
    CREATE UNIQUE INDEX UX_emp_employee_number ON dbo.emp_employee(company_id, employee_number)
        WHERE deleted_at IS NULL;
    CREATE UNIQUE INDEX UX_emp_employee_corporate_email ON dbo.emp_employee(corporate_email)
        WHERE corporate_email IS NOT NULL AND deleted_at IS NULL;
    CREATE UNIQUE INDEX UX_emp_employee_curp ON dbo.emp_employee(curp)
        WHERE curp IS NOT NULL AND deleted_at IS NULL;
    CREATE UNIQUE INDEX UX_emp_employee_rfc ON dbo.emp_employee(rfc)
        WHERE rfc IS NOT NULL AND deleted_at IS NULL;
    CREATE UNIQUE INDEX UX_emp_employee_nss ON dbo.emp_employee(nss)
        WHERE nss IS NOT NULL AND deleted_at IS NULL;

    CREATE INDEX IX_emp_employee_org ON dbo.emp_employee(company_id, branch_id, department_id, position_id);
    CREATE INDEX IX_emp_employee_manager ON dbo.emp_employee(manager_id);
    CREATE INDEX IX_emp_employee_status ON dbo.emp_employee(status_id, enabled);
    CREATE INDEX IX_emp_employee_name ON dbo.emp_employee(last_name, second_last_name, first_name);
END;

IF OBJECT_ID('dbo.emp_emergency_contact', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.emp_emergency_contact (
        id           BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_emp_emergency_contact PRIMARY KEY,
        uuid         UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_emp_emergency_contact_uuid DEFAULT NEWID(),
        enabled      BIT NOT NULL CONSTRAINT DF_emp_emergency_contact_enabled DEFAULT 1,
        created_at   DATETIME2(3) NOT NULL CONSTRAINT DF_emp_emergency_contact_created_at DEFAULT SYSUTCDATETIME(),
        updated_at   DATETIME2(3) NULL,
        version      INT NOT NULL CONSTRAINT DF_emp_emergency_contact_version DEFAULT 0,
        deleted_at   DATETIME2(3) NULL,

        employee_id  BIGINT NOT NULL,
        name         NVARCHAR(150) NOT NULL,
        relationship NVARCHAR(50)  NULL,
        phone        NVARCHAR(20)  NOT NULL,
        email        NVARCHAR(150) NULL,
        is_primary   BIT NOT NULL CONSTRAINT DF_emp_emergency_contact_primary DEFAULT 0,

        CONSTRAINT FK_emp_emergency_contact_employee
            FOREIGN KEY (employee_id) REFERENCES dbo.emp_employee(id)
    );

    CREATE UNIQUE INDEX UX_emp_emergency_contact_uuid ON dbo.emp_emergency_contact(uuid);
    CREATE UNIQUE INDEX UX_emp_emergency_contact_primary ON dbo.emp_emergency_contact(employee_id)
        WHERE is_primary = 1 AND deleted_at IS NULL;
    CREATE INDEX IX_emp_emergency_contact_employee
        ON dbo.emp_emergency_contact(employee_id, deleted_at);
END;

-- ---------------------------------------------------------------------
-- Permiso para datos personales sensibles (CURP/RFC/NSS)
-- ---------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM dbo.sec_permission WHERE code = 'EMPLOYEE_VIEW_SENSITIVE')
BEGIN
    INSERT INTO dbo.sec_permission (uuid, code, module, description, enabled, created_at, version)
    VALUES (NEWID(), 'EMPLOYEE_VIEW_SENSITIVE', 'employee',
            'Ver datos personales sensibles de empleados', 1, SYSUTCDATETIME(), 0);
END;

INSERT INTO dbo.sec_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM dbo.sec_role r
CROSS JOIN dbo.sec_permission p
WHERE p.code = 'EMPLOYEE_VIEW_SENSITIVE'
  AND r.code IN ('ADMIN', 'SUPER_ADMIN', 'HR', 'EMPLOYEE')
  AND NOT EXISTS (
      SELECT 1 FROM dbo.sec_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ---------------------------------------------------------------------
-- Organización mínima para poder migrar usuarios existentes.
-- El administrador puede renombrarla/reorganizarla después.
-- ---------------------------------------------------------------------
DECLARE @companyId BIGINT;
DECLARE @branchId BIGINT;
DECLARE @departmentId BIGINT;
DECLARE @positionId BIGINT;
DECLARE @statusActivo BIGINT;
DECLARE @modalityPresencial BIGINT;

IF NOT EXISTS (SELECT 1 FROM dbo.org_company WHERE code = 'INSSOFT' AND deleted_at IS NULL)
BEGIN
    INSERT INTO dbo.org_company (code, legal_name, trade_name, timezone)
    VALUES ('INSSOFT', 'INSSOFT', 'INSSOFT', 'America/Mexico_City');
END;

SELECT @companyId = id FROM dbo.org_company WHERE code = 'INSSOFT' AND deleted_at IS NULL;

IF NOT EXISTS (SELECT 1 FROM dbo.org_branch WHERE company_id = @companyId AND code = 'MATRIZ' AND deleted_at IS NULL)
BEGIN
    INSERT INTO dbo.org_branch (company_id, code, name)
    VALUES (@companyId, 'MATRIZ', 'Matriz');
END;

SELECT @branchId = id FROM dbo.org_branch WHERE company_id = @companyId AND code = 'MATRIZ' AND deleted_at IS NULL;

IF NOT EXISTS (SELECT 1 FROM dbo.org_department WHERE branch_id = @branchId AND code = 'GENERAL' AND deleted_at IS NULL)
BEGIN
    INSERT INTO dbo.org_department (branch_id, code, name)
    VALUES (@branchId, 'GENERAL', 'General');
END;

SELECT @departmentId = id FROM dbo.org_department WHERE branch_id = @branchId AND code = 'GENERAL' AND deleted_at IS NULL;

IF NOT EXISTS (SELECT 1 FROM dbo.org_position WHERE department_id = @departmentId AND code = 'GENERAL' AND deleted_at IS NULL)
BEGIN
    INSERT INTO dbo.org_position (department_id, code, name)
    VALUES (@departmentId, 'GENERAL', 'General');
END;

SELECT @positionId = id FROM dbo.org_position WHERE department_id = @departmentId AND code = 'GENERAL' AND deleted_at IS NULL;

SELECT @statusActivo = i.id
FROM dbo.md_catalog_item i
JOIN dbo.md_catalog c ON c.id = i.catalog_id
WHERE c.code = 'EMPLOYMENT_STATUS' AND i.code = 'ACTIVO' AND i.deleted_at IS NULL;

SELECT @modalityPresencial = i.id
FROM dbo.md_catalog_item i
JOIN dbo.md_catalog c ON c.id = i.catalog_id
WHERE c.code = 'WORK_MODALITY' AND i.code = 'PRESENCIAL' AND i.deleted_at IS NULL;

IF @statusActivo IS NULL
    THROW 50001, 'No existe EMPLOYMENT_STATUS/ACTIVO. Ejecute V4 antes de V5.', 1;

-- ---------------------------------------------------------------------
-- Migración de usuarios legados a empleados (idempotente)
-- ---------------------------------------------------------------------
;WITH legacy AS (
    SELECT
        u.*,
        LTRIM(RTRIM(COALESCE(NULLIF(LTRIM(RTRIM(u.name)), ''), u.username))) AS full_name
    FROM dbo.users u
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.emp_employee e
        WHERE e.user_id = u.id AND e.deleted_at IS NULL
    )
)
INSERT INTO dbo.emp_employee (
    uuid, enabled, created_at, version,
    user_id, company_id, branch_id, department_id, position_id,
    employee_number, first_name, last_name,
    corporate_email, hire_date, status_id, work_modality_id
)
SELECT
    NEWID(),
    COALESCE(u.enabled, 1),
    SYSUTCDATETIME(),
    0,
    u.id,
    @companyId,
    @branchId,
    @departmentId,
    @positionId,
    CONCAT('LEG-', u.id),
    LEFT(CASE
        WHEN CHARINDEX(' ', u.full_name) > 0
            THEN LEFT(u.full_name, CHARINDEX(' ', u.full_name) - 1)
        ELSE u.full_name
    END, 100),
    LEFT(CASE
        WHEN CHARINDEX(' ', u.full_name) > 0
            THEN SUBSTRING(u.full_name, CHARINDEX(' ', u.full_name) + 1, 100)
        ELSE 'SIN_APELLIDO'
    END, 100),
    u.email,
    COALESCE(u.hire_date, CAST(SYSUTCDATETIME() AS DATE)),
    @statusActivo,
    @modalityPresencial
FROM legacy u;

UPDATE u
SET employee_id = e.id
FROM dbo.users u
JOIN dbo.emp_employee e ON e.user_id = u.id AND e.deleted_at IS NULL
WHERE u.employee_id IS NULL;

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_users_employee')
BEGIN
    ALTER TABLE dbo.users
        ADD CONSTRAINT FK_users_employee FOREIGN KEY (employee_id) REFERENCES dbo.emp_employee(id);
END;
