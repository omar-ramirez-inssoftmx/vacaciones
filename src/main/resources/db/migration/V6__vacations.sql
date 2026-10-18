-- =====================================================================
-- V6 — Vacaciones: políticas, periodos, ledger, eventos y workflow
-- ---------------------------------------------------------------------
-- Requiere V2 (users/RBAC), V3/V4 (organización/catálogos) y V5 (empleados).
-- Preserva datos históricos: las solicitudes existentes se marcan
-- legacy_balance = 1 y NO generan movimientos en el ledger.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Políticas de vacaciones
-- ---------------------------------------------------------------------
IF OBJECT_ID('dbo.vac_policy', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vac_policy (
        id              BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_vac_policy PRIMARY KEY,
        uuid            UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_vac_policy_uuid DEFAULT NEWID(),
        enabled         BIT NOT NULL CONSTRAINT DF_vac_policy_enabled DEFAULT 1,
        created_at      DATETIME2(3) NOT NULL CONSTRAINT DF_vac_policy_created_at DEFAULT SYSUTCDATETIME(),
        updated_at      DATETIME2(3) NULL,
        version         INT NOT NULL CONSTRAINT DF_vac_policy_version DEFAULT 0,
        deleted_at      DATETIME2(3) NULL,
        company_id      BIGINT NOT NULL,
        code            NVARCHAR(50)  NOT NULL,
        name            NVARCHAR(150) NOT NULL,
        description     NVARCHAR(500) NULL,
        effective_from  DATE NOT NULL,
        effective_to    DATE NULL,
        default_policy  BIT NOT NULL CONSTRAINT DF_vac_policy_default DEFAULT 0,
        accrual_type    NVARCHAR(30) NOT NULL CONSTRAINT DF_vac_policy_accrual DEFAULT 'ANNIVERSARY',
        expiry_months   INT NULL,
        allow_negative  BIT NOT NULL CONSTRAINT DF_vac_policy_allow_negative DEFAULT 0,
        CONSTRAINT FK_vac_policy_company FOREIGN KEY (company_id) REFERENCES dbo.org_company(id),
        CONSTRAINT CK_vac_policy_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
    );

    CREATE UNIQUE INDEX UX_vac_policy_uuid ON dbo.vac_policy(uuid);
    CREATE UNIQUE INDEX UX_vac_policy_company_code ON dbo.vac_policy(company_id, code)
        WHERE deleted_at IS NULL;
    CREATE INDEX IX_vac_policy_company_default ON dbo.vac_policy(company_id, default_policy, enabled);
END;

IF OBJECT_ID('dbo.vac_policy_rule', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vac_policy_rule (
        id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_vac_policy_rule PRIMARY KEY,
        uuid        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_vac_policy_rule_uuid DEFAULT NEWID(),
        enabled     BIT NOT NULL CONSTRAINT DF_vac_policy_rule_enabled DEFAULT 1,
        created_at  DATETIME2(3) NOT NULL CONSTRAINT DF_vac_policy_rule_created_at DEFAULT SYSUTCDATETIME(),
        updated_at  DATETIME2(3) NULL,
        version     INT NOT NULL CONSTRAINT DF_vac_policy_rule_version DEFAULT 0,
        policy_id   BIGINT NOT NULL,
        min_years   INT NOT NULL,
        max_years   INT NOT NULL,
        days        DECIMAL(5,2) NOT NULL,
        CONSTRAINT FK_vac_policy_rule_policy FOREIGN KEY (policy_id) REFERENCES dbo.vac_policy(id),
        CONSTRAINT CK_vac_policy_rule_range CHECK (max_years >= min_years AND days >= 0),
        CONSTRAINT UX_vac_policy_rule_range UNIQUE (policy_id, min_years, max_years)
    );
END;

-- ---------------------------------------------------------------------
-- 2. Periodos por aniversario
-- ---------------------------------------------------------------------
IF OBJECT_ID('dbo.vac_period', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vac_period (
        id               BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_vac_period PRIMARY KEY,
        uuid             UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_vac_period_uuid DEFAULT NEWID(),
        enabled          BIT NOT NULL CONSTRAINT DF_vac_period_enabled DEFAULT 1,
        created_at       DATETIME2(3) NOT NULL CONSTRAINT DF_vac_period_created_at DEFAULT SYSUTCDATETIME(),
        updated_at       DATETIME2(3) NULL,
        version          INT NOT NULL CONSTRAINT DF_vac_period_version DEFAULT 0,
        employee_id      BIGINT NOT NULL,
        policy_id        BIGINT NOT NULL,
        period_start     DATE NOT NULL,
        period_end       DATE NOT NULL,
        anniversary_date DATE NOT NULL,
        entitled_days    DECIMAL(5,2) NOT NULL,
        opening_balance  DECIMAL(5,2) NOT NULL CONSTRAINT DF_vac_period_opening DEFAULT 0,
        status           NVARCHAR(20) NOT NULL CONSTRAINT DF_vac_period_status DEFAULT 'OPEN',
        CONSTRAINT FK_vac_period_employee FOREIGN KEY (employee_id) REFERENCES dbo.emp_employee(id),
        CONSTRAINT FK_vac_period_policy FOREIGN KEY (policy_id) REFERENCES dbo.vac_policy(id),
        CONSTRAINT CK_vac_period_dates CHECK (period_end >= period_start),
        CONSTRAINT CK_vac_period_days CHECK (entitled_days >= 0 AND opening_balance >= 0),
        CONSTRAINT UX_vac_period_employee_start UNIQUE (employee_id, period_start)
    );

    CREATE UNIQUE INDEX UX_vac_period_uuid ON dbo.vac_period(uuid);
    CREATE INDEX IX_vac_period_employee_end ON dbo.vac_period(employee_id, period_end);
END;

-- ---------------------------------------------------------------------
-- 3. Ledger de movimientos
-- ---------------------------------------------------------------------
IF OBJECT_ID('dbo.vac_ledger', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vac_ledger (
        id             BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_vac_ledger PRIMARY KEY,
        uuid           UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_vac_ledger_uuid DEFAULT NEWID(),
        employee_id    BIGINT NOT NULL,
        period_id      BIGINT NOT NULL,
        movement_type  NVARCHAR(30) NOT NULL,
        days           DECIMAL(5,2) NOT NULL,
        signed_days    DECIMAL(5,2) NOT NULL,
        reference_type NVARCHAR(30) NULL,
        reference_id   BIGINT NULL,
        notes          NVARCHAR(500) NULL,
        created_by     BIGINT NULL,
        created_at     DATETIME2(3) NOT NULL CONSTRAINT DF_vac_ledger_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_vac_ledger_employee FOREIGN KEY (employee_id) REFERENCES dbo.emp_employee(id),
        CONSTRAINT FK_vac_ledger_period FOREIGN KEY (period_id) REFERENCES dbo.vac_period(id),
        CONSTRAINT FK_vac_ledger_created_by FOREIGN KEY (created_by) REFERENCES dbo.users(id),
        CONSTRAINT CK_vac_ledger_days CHECK (days >= 0)
    );

    CREATE UNIQUE INDEX UX_vac_ledger_uuid ON dbo.vac_ledger(uuid);
    CREATE INDEX IX_vac_ledger_employee_period ON dbo.vac_ledger(employee_id, period_id, created_at);
    CREATE INDEX IX_vac_ledger_reference ON dbo.vac_ledger(reference_type, reference_id, movement_type);
END;

-- ---------------------------------------------------------------------
-- 4. Eventos de solicitud (historial de workflow)
-- ---------------------------------------------------------------------
IF OBJECT_ID('dbo.vac_request_event', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vac_request_event (
        id            BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_vac_request_event PRIMARY KEY,
        uuid          UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_vac_request_event_uuid DEFAULT NEWID(),
        request_id    BIGINT NOT NULL,
        action        NVARCHAR(50) NOT NULL,
        from_status   NVARCHAR(30) NULL,
        to_status     NVARCHAR(30) NULL,
        actor_user_id BIGINT NULL,
        comment       NVARCHAR(500) NULL,
        created_at    DATETIME2(3) NOT NULL CONSTRAINT DF_vac_request_event_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_vac_request_event_request FOREIGN KEY (request_id) REFERENCES dbo.vacation_requests(id),
        CONSTRAINT FK_vac_request_event_actor FOREIGN KEY (actor_user_id) REFERENCES dbo.users(id)
    );

    CREATE UNIQUE INDEX UX_vac_request_event_uuid ON dbo.vac_request_event(uuid);
    CREATE INDEX IX_vac_request_event_request ON dbo.vac_request_event(request_id, created_at);
END;

-- ---------------------------------------------------------------------
-- 5. Extensión de vacation_requests (aditiva)
-- ---------------------------------------------------------------------
-- Empleados sin usuario vinculado: permitir user_id nulo.
IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.vacation_requests')
      AND name = 'user_id'
      AND is_nullable = 0
)
    ALTER TABLE dbo.vacation_requests ALTER COLUMN user_id BIGINT NULL;

IF COL_LENGTH('dbo.vacation_requests', 'uuid') IS NULL
BEGIN
    ALTER TABLE dbo.vacation_requests ADD uuid UNIQUEIDENTIFIER NULL;
    UPDATE dbo.vacation_requests SET uuid = NEWID() WHERE uuid IS NULL;
    ALTER TABLE dbo.vacation_requests ALTER COLUMN uuid UNIQUEIDENTIFIER NOT NULL;
    ALTER TABLE dbo.vacation_requests ADD CONSTRAINT DF_vacation_requests_uuid DEFAULT NEWID() FOR uuid;
END;

IF COL_LENGTH('dbo.vacation_requests', 'employee_id') IS NULL
    ALTER TABLE dbo.vacation_requests ADD employee_id BIGINT NULL;

IF COL_LENGTH('dbo.vacation_requests', 'period_id') IS NULL
    ALTER TABLE dbo.vacation_requests ADD period_id BIGINT NULL;

IF COL_LENGTH('dbo.vacation_requests', 'requested_by') IS NULL
    ALTER TABLE dbo.vacation_requests ADD requested_by BIGINT NULL;

IF COL_LENGTH('dbo.vacation_requests', 'cancelled_at') IS NULL
    ALTER TABLE dbo.vacation_requests ADD cancelled_at DATETIME2(3) NULL;

IF COL_LENGTH('dbo.vacation_requests', 'cancel_reason') IS NULL
    ALTER TABLE dbo.vacation_requests ADD cancel_reason NVARCHAR(500) NULL;

IF COL_LENGTH('dbo.vacation_requests', 'updated_at') IS NULL
    ALTER TABLE dbo.vacation_requests ADD updated_at DATETIME2(3) NULL;

IF COL_LENGTH('dbo.vacation_requests', 'deleted_at') IS NULL
    ALTER TABLE dbo.vacation_requests ADD deleted_at DATETIME2(3) NULL;

IF COL_LENGTH('dbo.vacation_requests', 'version') IS NULL
    ALTER TABLE dbo.vacation_requests ADD version INT NOT NULL
        CONSTRAINT DF_vacation_requests_version DEFAULT 0;

IF COL_LENGTH('dbo.vacation_requests', 'legacy_balance') IS NULL
    ALTER TABLE dbo.vacation_requests ADD legacy_balance BIT NOT NULL
        CONSTRAINT DF_vacation_requests_legacy_balance DEFAULT 0;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_vacation_requests_uuid' AND object_id = OBJECT_ID('dbo.vacation_requests'))
    CREATE UNIQUE INDEX UX_vacation_requests_uuid ON dbo.vacation_requests(uuid);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_vacation_requests_employee_status' AND object_id = OBJECT_ID('dbo.vacation_requests'))
    CREATE INDEX IX_vacation_requests_employee_status ON dbo.vacation_requests(employee_id, status, start_date);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_vacation_requests_period' AND object_id = OBJECT_ID('dbo.vacation_requests'))
    CREATE INDEX IX_vacation_requests_period ON dbo.vacation_requests(period_id);

-- Backfill de empleado y usuario solicitante
UPDATE vr
SET employee_id = e.id
FROM dbo.vacation_requests vr
JOIN dbo.emp_employee e ON e.user_id = vr.user_id AND e.deleted_at IS NULL
WHERE vr.employee_id IS NULL;

UPDATE vr
SET requested_by = u.id
FROM dbo.vacation_requests vr
JOIN dbo.users u ON u.username = vr.requested_by_username
WHERE vr.requested_by IS NULL AND vr.requested_by_username IS NOT NULL;

UPDATE dbo.vacation_requests
SET updated_at = created_at
WHERE updated_at IS NULL;

-- ---------------------------------------------------------------------
-- 6. Política default y reglas
-- ---------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM dbo.org_company WHERE deleted_at IS NULL)
    THROW 50002, 'No existe una empresa para crear la política de vacaciones.', 1;

-- Política default para cada empresa activa que aún no tenga una.
INSERT INTO dbo.vac_policy (
    company_id, code, name, description, effective_from,
    default_policy, accrual_type, expiry_months, allow_negative
)
SELECT
    c.id, 'DEFAULT', 'Política de vacaciones',
    'Política base por aniversario (LFT)',
    '2024-01-01', 1, 'ANNIVERSARY', 18, 0
FROM dbo.org_company c
WHERE c.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM dbo.vac_policy p
      WHERE p.company_id = c.id AND p.code = 'DEFAULT' AND p.deleted_at IS NULL
  );

-- Reglas base para todas las políticas default.
INSERT INTO dbo.vac_policy_rule (policy_id, min_years, max_years, days)
SELECT p.id, v.min_years, v.max_years, v.days
FROM dbo.vac_policy p
CROSS APPLY (VALUES
    (0, 0, 0),
    (1, 1, 12),
    (2, 2, 14),
    (3, 3, 16),
    (4, 4, 18),
    (5, 5, 20),
    (6, 10, 22),
    (11, 15, 24),
    (16, 20, 26),
    (21, 25, 28),
    (26, 30, 30),
    (31, 35, 32),
    (36, 40, 34),
    (41, 45, 36),
    (46, 50, 38),
    (51, 999, 40)
) AS v(min_years, max_years, days)
WHERE p.code = 'DEFAULT'
  AND p.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM dbo.vac_policy_rule r
      WHERE r.policy_id = p.id
        AND r.min_years = v.min_years
        AND r.max_years = v.max_years
  );

-- ---------------------------------------------------------------------
-- 7. Periodo vigente por empleado y saldo inicial
-- ---------------------------------------------------------------------
;WITH emp AS (
    SELECT
        e.id AS employee_id,
        e.company_id,
        e.hire_date,
        u.vacation_days_available,
        CASE
            WHEN DATEADD(YEAR,
                    DATEDIFF(YEAR, e.hire_date, CAST(SYSUTCDATETIME() AS DATE)),
                    e.hire_date) > CAST(SYSUTCDATETIME() AS DATE)
                THEN DATEDIFF(YEAR, e.hire_date, CAST(SYSUTCDATETIME() AS DATE)) - 1
            ELSE DATEDIFF(YEAR, e.hire_date, CAST(SYSUTCDATETIME() AS DATE))
        END AS completed_years
    FROM dbo.emp_employee e
    LEFT JOIN dbo.users u ON u.id = e.user_id
    WHERE e.deleted_at IS NULL
      AND e.hire_date <= CAST(SYSUTCDATETIME() AS DATE)
)
INSERT INTO dbo.vac_period (
    employee_id, policy_id, period_start, period_end,
    anniversary_date, entitled_days, opening_balance, status
)
SELECT
    emp.employee_id,
    pol.id,
    DATEADD(YEAR, emp.completed_years, emp.hire_date),
    DATEADD(DAY, -1, DATEADD(YEAR, emp.completed_years + 1, emp.hire_date)),
    DATEADD(YEAR, emp.completed_years, emp.hire_date),
    COALESCE((
        SELECT TOP 1 r.days
        FROM dbo.vac_policy_rule r
        WHERE r.policy_id = pol.id
          AND emp.completed_years BETWEEN r.min_years AND r.max_years
        ORDER BY r.min_years DESC
    ), 0),
    CASE
        WHEN COALESCE(emp.vacation_days_available, 0) < 0 THEN 0
        ELSE COALESCE(emp.vacation_days_available, 0)
    END,
    'OPEN'
FROM emp
JOIN dbo.vac_policy pol
  ON pol.company_id = emp.company_id
 AND pol.code = 'DEFAULT'
 AND pol.deleted_at IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.vac_period p
    WHERE p.employee_id = emp.employee_id
      AND p.period_start = DATEADD(YEAR, emp.completed_years, emp.hire_date)
);

INSERT INTO dbo.vac_ledger (
    employee_id, period_id, movement_type, days, signed_days,
    reference_type, reference_id, notes
)
SELECT
    p.employee_id, p.id, 'OPENING_BALANCE', p.opening_balance, p.opening_balance,
    'MIGRATION', p.employee_id, 'Saldo inicial migrado desde users.vacation_days_available'
FROM dbo.vac_period p
WHERE p.opening_balance > 0
  AND NOT EXISTS (
      SELECT 1 FROM dbo.vac_ledger l
      WHERE l.period_id = p.id AND l.movement_type = 'OPENING_BALANCE'
  );

-- Asignar periodo a solicitudes históricas cuando la fecha cae en el periodo vigente.
UPDATE vr
SET period_id = p.id
FROM dbo.vacation_requests vr
JOIN dbo.vac_period p
  ON p.employee_id = vr.employee_id
 AND vr.start_date BETWEEN p.period_start AND p.period_end
WHERE vr.period_id IS NULL;

-- Las solicitudes históricas ya descontaron el saldo legado: no generan ledger.
UPDATE dbo.vacation_requests
SET legacy_balance = 1
WHERE legacy_balance = 0;

-- ---------------------------------------------------------------------
-- 8. Foreign keys de vacation_requests
-- ---------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_vacation_requests_employee')
    ALTER TABLE dbo.vacation_requests
        ADD CONSTRAINT FK_vacation_requests_employee
        FOREIGN KEY (employee_id) REFERENCES dbo.emp_employee(id);

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_vacation_requests_period')
    ALTER TABLE dbo.vacation_requests
        ADD CONSTRAINT FK_vacation_requests_period
        FOREIGN KEY (period_id) REFERENCES dbo.vac_period(id);

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_vacation_requests_requested_by')
    ALTER TABLE dbo.vacation_requests
        ADD CONSTRAINT FK_vacation_requests_requested_by
        FOREIGN KEY (requested_by) REFERENCES dbo.users(id);

-- ---------------------------------------------------------------------
-- 9. Permiso de administración de políticas
-- ---------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM dbo.sec_permission WHERE code = 'VACATION_POLICY_MANAGE')
BEGIN
    INSERT INTO dbo.sec_permission (uuid, code, module, description, enabled, created_at, version)
    VALUES (NEWID(), 'VACATION_POLICY_MANAGE', 'vacation',
            'Administrar políticas de vacaciones', 1, SYSUTCDATETIME(), 0);
END;

INSERT INTO dbo.sec_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM dbo.sec_role r
CROSS JOIN dbo.sec_permission p
WHERE p.code = 'VACATION_POLICY_MANAGE'
  AND r.code IN ('ADMIN', 'SUPER_ADMIN', 'HR')
  AND NOT EXISTS (
      SELECT 1 FROM dbo.sec_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
