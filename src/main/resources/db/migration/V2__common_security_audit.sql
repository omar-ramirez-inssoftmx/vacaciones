-- =====================================================================
-- V2 — Fundación de seguridad, RBAC y auditoría
-- ---------------------------------------------------------------------
-- Cambios aditivos sobre el esquema legado. No elimina ni renombra nada.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Columnas comunes en users (identidad)
-- ---------------------------------------------------------------------
IF COL_LENGTH('dbo.users', 'uuid') IS NULL
BEGIN
    -- Se agrega nullable, se llena fila por fila y luego se hace NOT NULL
    -- para garantizar un UUID único en tablas con datos existentes.
    EXEC(N'ALTER TABLE dbo.users ADD uuid UNIQUEIDENTIFIER NULL;');
    EXEC(N'UPDATE dbo.users SET uuid = NEWID() WHERE uuid IS NULL;');
    EXEC(N'ALTER TABLE dbo.users ALTER COLUMN uuid UNIQUEIDENTIFIER NOT NULL;');
    EXEC(N'ALTER TABLE dbo.users ADD CONSTRAINT DF_users_uuid DEFAULT NEWID() FOR uuid;');
END;

IF COL_LENGTH('dbo.users', 'employee_id') IS NULL
    ALTER TABLE dbo.users ADD employee_id BIGINT NULL;

IF COL_LENGTH('dbo.users', 'last_login_at') IS NULL
    ALTER TABLE dbo.users ADD last_login_at DATETIME2(3) NULL;

IF COL_LENGTH('dbo.users', 'password_changed_at') IS NULL
    ALTER TABLE dbo.users ADD password_changed_at DATETIME2(3) NULL;

IF COL_LENGTH('dbo.users', 'failed_attempts') IS NULL
    ALTER TABLE dbo.users ADD failed_attempts INT NOT NULL
        CONSTRAINT DF_users_failed_attempts DEFAULT 0;

IF COL_LENGTH('dbo.users', 'locked_until') IS NULL
    ALTER TABLE dbo.users ADD locked_until DATETIME2(3) NULL;

IF COL_LENGTH('dbo.users', 'created_at') IS NULL
    ALTER TABLE dbo.users ADD created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_users_created_at DEFAULT SYSUTCDATETIME();

IF COL_LENGTH('dbo.users', 'updated_at') IS NULL
    ALTER TABLE dbo.users ADD updated_at DATETIME2(3) NULL;

IF COL_LENGTH('dbo.users', 'deleted_at') IS NULL
    ALTER TABLE dbo.users ADD deleted_at DATETIME2(3) NULL;

IF COL_LENGTH('dbo.users', 'version') IS NULL
    ALTER TABLE dbo.users ADD version INT NOT NULL
        CONSTRAINT DF_users_version DEFAULT 0;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_users_uuid' AND object_id = OBJECT_ID('dbo.users'))
    CREATE UNIQUE INDEX UX_users_uuid ON dbo.users(uuid);

-- ---------------------------------------------------------------------
-- 2. RBAC
-- ---------------------------------------------------------------------
IF OBJECT_ID('dbo.sec_role', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.sec_role (
        id          INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_sec_role PRIMARY KEY,
        uuid        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_sec_role_uuid DEFAULT NEWID(),
        code        NVARCHAR(50)  NOT NULL,
        name        NVARCHAR(100) NOT NULL,
        description NVARCHAR(255) NULL,
        system_role BIT NOT NULL CONSTRAINT DF_sec_role_system DEFAULT 0,
        enabled     BIT NOT NULL CONSTRAINT DF_sec_role_enabled DEFAULT 1,
        created_at  DATETIME2(3) NOT NULL CONSTRAINT DF_sec_role_created_at DEFAULT SYSUTCDATETIME(),
        updated_at  DATETIME2(3) NULL,
        version     INT NOT NULL CONSTRAINT DF_sec_role_version DEFAULT 0,
        CONSTRAINT UX_sec_role_code UNIQUE (code)
    );
END;

IF OBJECT_ID('dbo.sec_permission', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.sec_permission (
        id          INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_sec_permission PRIMARY KEY,
        uuid        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_sec_permission_uuid DEFAULT NEWID(),
        code        NVARCHAR(100) NOT NULL,
        module      NVARCHAR(50)  NOT NULL,
        description NVARCHAR(255) NULL,
        enabled     BIT NOT NULL CONSTRAINT DF_sec_permission_enabled DEFAULT 1,
        created_at  DATETIME2(3) NOT NULL CONSTRAINT DF_sec_permission_created_at DEFAULT SYSUTCDATETIME(),
        updated_at  DATETIME2(3) NULL,
        version     INT NOT NULL CONSTRAINT DF_sec_permission_version DEFAULT 0,
        CONSTRAINT UX_sec_permission_code UNIQUE (code)
    );
END;

IF OBJECT_ID('dbo.sec_user_role', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.sec_user_role (
        user_id BIGINT NOT NULL,
        role_id INT    NOT NULL,
        CONSTRAINT PK_sec_user_role PRIMARY KEY (user_id, role_id),
        CONSTRAINT FK_sec_user_role_user FOREIGN KEY (user_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_sec_user_role_role FOREIGN KEY (role_id) REFERENCES dbo.sec_role(id)
    );
END;

IF OBJECT_ID('dbo.sec_role_permission', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.sec_role_permission (
        role_id       INT NOT NULL,
        permission_id INT NOT NULL,
        CONSTRAINT PK_sec_role_permission PRIMARY KEY (role_id, permission_id),
        CONSTRAINT FK_sec_role_permission_role FOREIGN KEY (role_id) REFERENCES dbo.sec_role(id),
        CONSTRAINT FK_sec_role_permission_permission FOREIGN KEY (permission_id) REFERENCES dbo.sec_permission(id)
    );
END;

-- ---------------------------------------------------------------------
-- 3. Auditoría
-- ---------------------------------------------------------------------
IF OBJECT_ID('dbo.aud_audit_log', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aud_audit_log (
        id             BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_aud_audit_log PRIMARY KEY,
        uuid           UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_aud_audit_log_uuid DEFAULT NEWID(),
        user_id        BIGINT NULL,
        actor_username NVARCHAR(150) NULL,
        action         NVARCHAR(100) NOT NULL,
        entity         NVARCHAR(100) NOT NULL,
        entity_id      NVARCHAR(100) NULL,
        old_value      NVARCHAR(MAX) NULL,
        new_value      NVARCHAR(MAX) NULL,
        ip             NVARCHAR(45)  NULL,
        user_agent     NVARCHAR(500) NULL,
        correlation_id NVARCHAR(64)  NULL,
        created_at     DATETIME2(3) NOT NULL CONSTRAINT DF_aud_audit_log_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_aud_audit_log_user FOREIGN KEY (user_id) REFERENCES dbo.users(id)
    );

    CREATE INDEX IX_aud_audit_log_entity ON dbo.aud_audit_log(entity, entity_id, created_at DESC);
    CREATE INDEX IX_aud_audit_log_user ON dbo.aud_audit_log(user_id, created_at DESC);
    CREATE INDEX IX_aud_audit_log_actor ON dbo.aud_audit_log(actor_username, created_at DESC);
END;

-- ---------------------------------------------------------------------
-- 4. Seed de roles
-- ---------------------------------------------------------------------
DECLARE @roles TABLE (
    code NVARCHAR(50),
    name NVARCHAR(100),
    description NVARCHAR(255),
    system_role BIT
);

INSERT INTO @roles (code, name, description, system_role) VALUES
    ('SUPER_ADMIN', 'Super administrador', 'Acceso total al sistema', 1),
    ('ADMIN',       'Administrador',      'Administración funcional', 1),
    ('HR',          'Recursos Humanos',   'Gestión de empleados y procesos de RH', 1),
    ('LEADER',      'Líder',              'Aprobación y seguimiento de su equipo', 1),
    ('EMPLOYEE',    'Empleado',           'Acceso a su propia información', 1);

INSERT INTO dbo.sec_role (uuid, code, name, description, system_role, enabled, created_at, version)
SELECT NEWID(), r.code, r.name, r.description, r.system_role, 1, SYSUTCDATETIME(), 0
FROM @roles r
WHERE NOT EXISTS (SELECT 1 FROM dbo.sec_role x WHERE x.code = r.code);

-- ---------------------------------------------------------------------
-- 5. Seed de permisos
-- ---------------------------------------------------------------------
DECLARE @permissions TABLE (
    code NVARCHAR(100),
    module NVARCHAR(50),
    description NVARCHAR(255)
);

INSERT INTO @permissions (code, module, description) VALUES
    ('EMPLOYEE_VIEW_SELF',      'employee',      'Ver su propio perfil de empleado'),
    ('EMPLOYEE_VIEW',           'employee',      'Ver empleados'),
    ('EMPLOYEE_CREATE',         'employee',      'Crear empleados'),
    ('EMPLOYEE_UPDATE',         'employee',      'Actualizar empleados'),
    ('EMPLOYEE_TERMINATE',      'employee',      'Dar de baja empleados'),
    ('ORG_VIEW',                'organization',  'Ver organización'),
    ('ORG_MANAGE',              'organization',  'Administrar organización'),
    ('MASTERDATA_VIEW',         'masterdata',    'Ver catálogos'),
    ('MASTERDATA_MANAGE',       'masterdata',    'Administrar catálogos'),
    ('VACATION_CREATE',         'vacation',      'Crear solicitudes de vacaciones'),
    ('VACATION_VIEW_SELF',      'vacation',      'Ver sus vacaciones'),
    ('VACATION_VIEW',           'vacation',      'Ver vacaciones de otros'),
    ('VACATION_APPROVE',        'vacation',      'Aprobar o rechazar vacaciones'),
    ('VACATION_CANCEL',         'vacation',      'Cancelar solicitudes'),
    ('VACATION_ADJUST',         'vacation',      'Ajustar saldos de vacaciones'),
    ('CALENDAR_VIEW',           'calendar',      'Ver calendario laboral'),
    ('CALENDAR_MANAGE',         'calendar',      'Administrar calendario y festivos'),
    ('DOCUMENT_VIEW_SELF',      'documents',     'Ver sus documentos'),
    ('DOCUMENT_VIEW',           'documents',     'Ver documentos de otros'),
    ('DOCUMENT_UPLOAD',         'documents',     'Cargar documentos'),
    ('DOCUMENT_DELETE',         'documents',     'Eliminar documentos'),
    ('PAYROLL_VIEW_SELF',       'payroll',       'Ver sus recibos'),
    ('PAYROLL_VIEW',            'payroll',       'Ver recibos de otros'),
    ('PAYROLL_UPLOAD',          'payroll',       'Cargar recibos'),
    ('COMMUNICATION_VIEW',      'communication', 'Ver comunicados'),
    ('COMMUNICATION_CREATE',    'communication', 'Crear comunicados'),
    ('COMMUNICATION_PUBLISH',   'communication', 'Publicar comunicados'),
    ('INCIDENT_VIEW_SELF',      'incident',      'Ver sus incidencias'),
    ('INCIDENT_VIEW',           'incident',      'Ver incidencias de otros'),
    ('INCIDENT_CREATE',         'incident',      'Registrar incidencias'),
    ('INCIDENT_APPROVE',        'incident',      'Aprobar incidencias'),
    ('TELEWORK_VIEW_SELF',      'telework',      'Ver su acuerdo de teletrabajo'),
    ('TELEWORK_VIEW',           'telework',      'Ver acuerdos de teletrabajo'),
    ('TELEWORK_MANAGE',         'telework',      'Administrar teletrabajo'),
    ('WORKPLAN_VIEW_SELF',      'workplan',      'Ver su plan de trabajo'),
    ('WORKPLAN_VIEW',           'workplan',      'Ver planes de trabajo'),
    ('WORKPLAN_MANAGE',         'workplan',      'Administrar planes de trabajo'),
    ('NOTIFICATION_VIEW_SELF',  'notification',  'Ver sus notificaciones'),
    ('NOTIFICATION_MANAGE',     'notification',  'Administrar notificaciones'),
    ('AUDIT_VIEW',              'audit',         'Consultar auditoría'),
    ('USER_MANAGE',             'security',      'Administrar usuarios'),
    ('ROLE_MANAGE',             'security',      'Administrar roles'),
    ('PERMISSION_MANAGE',       'security',      'Administrar permisos'),
    ('DASHBOARD_VIEW_SELF',     'dashboard',     'Ver su dashboard'),
    ('DASHBOARD_VIEW',          'dashboard',     'Ver dashboard administrativo');

INSERT INTO dbo.sec_permission (uuid, code, module, description, enabled, created_at, version)
SELECT NEWID(), p.code, p.module, p.description, 1, SYSUTCDATETIME(), 0
FROM @permissions p
WHERE NOT EXISTS (SELECT 1 FROM dbo.sec_permission x WHERE x.code = p.code);

-- ---------------------------------------------------------------------
-- 6. Mapeo de usuarios legados a roles nuevos
-- ---------------------------------------------------------------------
INSERT INTO dbo.sec_user_role (user_id, role_id)
SELECT u.id, r.id
FROM dbo.users u
JOIN dbo.sec_role r ON r.code = CASE
        WHEN u.role = 'ROLE_ADMIN' THEN 'ADMIN'
        WHEN u.role = 'ROLE_LIDER' THEN 'LEADER'
        ELSE 'EMPLOYEE'
    END
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.sec_user_role ur
    WHERE ur.user_id = u.id AND ur.role_id = r.id
);

-- ---------------------------------------------------------------------
-- 7. Permisos por rol
-- ---------------------------------------------------------------------
-- ADMIN y SUPER_ADMIN: todos los permisos
INSERT INTO dbo.sec_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM dbo.sec_role r
CROSS JOIN dbo.sec_permission p
WHERE r.code IN ('ADMIN', 'SUPER_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM dbo.sec_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- HR
INSERT INTO dbo.sec_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM dbo.sec_role r
JOIN dbo.sec_permission p ON p.code IN (
    'EMPLOYEE_VIEW_SELF','EMPLOYEE_VIEW','EMPLOYEE_CREATE','EMPLOYEE_UPDATE','EMPLOYEE_TERMINATE',
    'ORG_VIEW','ORG_MANAGE','MASTERDATA_VIEW','MASTERDATA_MANAGE',
    'VACATION_CREATE','VACATION_VIEW_SELF','VACATION_VIEW','VACATION_APPROVE','VACATION_CANCEL','VACATION_ADJUST',
    'CALENDAR_VIEW','CALENDAR_MANAGE',
    'DOCUMENT_VIEW_SELF','DOCUMENT_VIEW','DOCUMENT_UPLOAD','DOCUMENT_DELETE',
    'PAYROLL_VIEW_SELF','PAYROLL_VIEW','PAYROLL_UPLOAD',
    'COMMUNICATION_VIEW','COMMUNICATION_CREATE','COMMUNICATION_PUBLISH',
    'INCIDENT_VIEW_SELF','INCIDENT_VIEW','INCIDENT_CREATE','INCIDENT_APPROVE',
    'TELEWORK_VIEW_SELF','TELEWORK_VIEW','TELEWORK_MANAGE',
    'WORKPLAN_VIEW_SELF','WORKPLAN_VIEW','WORKPLAN_MANAGE',
    'NOTIFICATION_VIEW_SELF','NOTIFICATION_MANAGE',
    'AUDIT_VIEW','DASHBOARD_VIEW_SELF','DASHBOARD_VIEW'
)
WHERE r.code = 'HR'
  AND NOT EXISTS (
      SELECT 1 FROM dbo.sec_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- LEADER
INSERT INTO dbo.sec_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM dbo.sec_role r
JOIN dbo.sec_permission p ON p.code IN (
    'EMPLOYEE_VIEW_SELF','EMPLOYEE_VIEW',
    'ORG_VIEW','MASTERDATA_VIEW','CALENDAR_VIEW',
    'VACATION_CREATE','VACATION_VIEW_SELF','VACATION_VIEW','VACATION_APPROVE','VACATION_CANCEL',
    'DOCUMENT_VIEW_SELF','DOCUMENT_VIEW',
    'INCIDENT_VIEW_SELF','INCIDENT_VIEW','INCIDENT_CREATE','INCIDENT_APPROVE',
    'TELEWORK_VIEW_SELF','TELEWORK_VIEW',
    'WORKPLAN_VIEW_SELF','WORKPLAN_VIEW',
    'COMMUNICATION_VIEW','NOTIFICATION_VIEW_SELF',
    'DASHBOARD_VIEW_SELF','DASHBOARD_VIEW'
)
WHERE r.code = 'LEADER'
  AND NOT EXISTS (
      SELECT 1 FROM dbo.sec_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- EMPLOYEE
INSERT INTO dbo.sec_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM dbo.sec_role r
JOIN dbo.sec_permission p ON p.code IN (
    'EMPLOYEE_VIEW_SELF','ORG_VIEW','MASTERDATA_VIEW','CALENDAR_VIEW',
    'VACATION_CREATE','VACATION_VIEW_SELF','VACATION_CANCEL',
    'DOCUMENT_VIEW_SELF','PAYROLL_VIEW_SELF',
    'INCIDENT_VIEW_SELF','INCIDENT_CREATE',
    'TELEWORK_VIEW_SELF','WORKPLAN_VIEW_SELF',
    'COMMUNICATION_VIEW','NOTIFICATION_VIEW_SELF','DASHBOARD_VIEW_SELF'
)
WHERE r.code = 'EMPLOYEE'
  AND NOT EXISTS (
      SELECT 1 FROM dbo.sec_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
