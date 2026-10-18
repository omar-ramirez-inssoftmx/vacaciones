-- =====================================================================
-- V14 — Permisos de alcance (self / team / all / manage)
-- =====================================================================
-- Los permisos históricos se conservan para no romper pantallas legadas,
-- pero la autorización de recurso usa los permisos de alcance nuevos.

DECLARE @permissions TABLE (
    code NVARCHAR(100),
    module NVARCHAR(50),
    description NVARCHAR(255)
);

INSERT INTO @permissions (code, module, description) VALUES
    ('EMPLOYEE_VIEW_TEAM',      'employee',      'Ver empleados de su equipo'),
    ('EMPLOYEE_VIEW_ALL',       'employee',      'Ver cualquier empleado'),
    ('EMPLOYEE_DISABLE',        'employee',      'Desactivar empleados'),
    ('VACATION_CREATE_SELF',    'vacation',      'Crear sus propias solicitudes'),
    ('VACATION_VIEW_TEAM',      'vacation',      'Ver vacaciones de su equipo'),
    ('VACATION_APPROVE_TEAM',   'vacation',      'Aprobar vacaciones de su equipo'),
    ('VACATION_MANAGE_ALL',     'vacation',      'Administrar vacaciones de toda la organización'),
    ('VACATION_CANCEL_SELF',    'vacation',      'Cancelar sus propias solicitudes'),
    ('PAYROLL_VIEW_TEAM',       'payroll',       'Ver recibos de su equipo (permiso explícito)'),
    ('PAYROLL_MANAGE',          'payroll',       'Administrar recibos de nómina'),
    ('DOCUMENT_VIEW_TEAM',      'documents',     'Ver documentos de su equipo (permiso explícito)'),
    ('DOCUMENT_MANAGE',         'documents',     'Administrar documentos'),
    ('INCIDENT_VIEW_TEAM',      'incident',      'Ver incidencias de su equipo'),
    ('INCIDENT_CREATE_SELF',    'incident',      'Registrar sus incidencias'),
    ('INCIDENT_CREATE_TEAM',    'incident',      'Registrar incidencias de su equipo'),
    ('INCIDENT_MANAGE',         'incident',      'Administrar incidencias'),
    ('TELEWORK_VIEW_TEAM',      'telework',      'Ver teletrabajo de su equipo'),
    ('TELEWORK_MANAGE',         'telework',      'Administrar teletrabajo'),
    ('WORKPLAN_VIEW_TEAM',      'workplan',      'Ver plan de trabajo de su equipo'),
    ('WORKPLAN_MANAGE',         'workplan',      'Administrar planes de trabajo'),
    ('DASHBOARD_VIEW_TEAM',     'dashboard',     'Ver dashboard de su equipo'),
    ('DASHBOARD_VIEW_ALL',      'dashboard',     'Ver dashboard global');

INSERT INTO dbo.sec_permission (uuid, code, module, description, enabled, created_at, version)
SELECT NEWID(), p.code, p.module, p.description, 1, SYSUTCDATETIME(), 0
FROM @permissions p
WHERE NOT EXISTS (SELECT 1 FROM dbo.sec_permission x WHERE x.code = p.code);

-- ADMIN / SUPER_ADMIN: todos los permisos nuevos
INSERT INTO dbo.sec_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM dbo.sec_role r
JOIN dbo.sec_permission p ON p.code IN (SELECT code FROM @permissions)
WHERE r.code IN ('ADMIN', 'SUPER_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM dbo.sec_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- HR: alcance global administrativo, sin gestión de roles/usuarios
INSERT INTO dbo.sec_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM dbo.sec_role r
JOIN dbo.sec_permission p ON p.code IN (
    'EMPLOYEE_VIEW_TEAM','EMPLOYEE_VIEW_ALL','EMPLOYEE_DISABLE',
    'VACATION_CREATE_SELF','VACATION_VIEW_TEAM','VACATION_APPROVE_TEAM','VACATION_MANAGE_ALL','VACATION_CANCEL_SELF',
    'PAYROLL_VIEW_TEAM','PAYROLL_MANAGE',
    'DOCUMENT_VIEW_TEAM','DOCUMENT_MANAGE',
    'INCIDENT_VIEW_TEAM','INCIDENT_CREATE_SELF','INCIDENT_CREATE_TEAM','INCIDENT_MANAGE',
    'TELEWORK_VIEW_TEAM','TELEWORK_MANAGE',
    'WORKPLAN_VIEW_TEAM','WORKPLAN_MANAGE',
    'DASHBOARD_VIEW_TEAM','DASHBOARD_VIEW_ALL'
)
WHERE r.code = 'HR'
  AND NOT EXISTS (
      SELECT 1 FROM dbo.sec_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- LEADER: self + team, sin acceso global
INSERT INTO dbo.sec_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM dbo.sec_role r
JOIN dbo.sec_permission p ON p.code IN (
    'EMPLOYEE_VIEW_TEAM',
    'VACATION_CREATE_SELF','VACATION_VIEW_TEAM','VACATION_APPROVE_TEAM','VACATION_CANCEL_SELF',
    'INCIDENT_CREATE_SELF','INCIDENT_CREATE_TEAM','INCIDENT_VIEW_TEAM',
    'TELEWORK_VIEW_TEAM',
    'WORKPLAN_VIEW_TEAM',
    'DASHBOARD_VIEW_TEAM'
)
WHERE r.code = 'LEADER'
  AND NOT EXISTS (
      SELECT 1 FROM dbo.sec_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- EMPLOYEE: únicamente self
INSERT INTO dbo.sec_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM dbo.sec_role r
JOIN dbo.sec_permission p ON p.code IN (
    'EMPLOYEE_VIEW_SELF',
    'VACATION_CREATE_SELF','VACATION_VIEW_SELF','VACATION_CANCEL_SELF',
    'PAYROLL_VIEW_SELF',
    'DOCUMENT_VIEW_SELF',
    'INCIDENT_CREATE_SELF',
    'TELEWORK_VIEW_SELF',
    'WORKPLAN_VIEW_SELF',
    'DASHBOARD_VIEW_SELF'
)
WHERE r.code = 'EMPLOYEE'
  AND NOT EXISTS (
      SELECT 1 FROM dbo.sec_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- El líder NO debe conservar permisos históricos de alcance global.
DELETE rp
FROM dbo.sec_role_permission rp
JOIN dbo.sec_role r ON r.id = rp.role_id
JOIN dbo.sec_permission p ON p.id = rp.permission_id
WHERE r.code = 'LEADER'
  AND p.code IN ('EMPLOYEE_VIEW', 'DOCUMENT_VIEW', 'PAYROLL_VIEW');

-- El empleado no debe conservar permisos históricos de gestión.
DELETE rp
FROM dbo.sec_role_permission rp
JOIN dbo.sec_role r ON r.id = rp.role_id
JOIN dbo.sec_permission p ON p.id = rp.permission_id
WHERE r.code = 'EMPLOYEE'
  AND p.code IN ('VACATION_VIEW', 'VACATION_CANCEL', 'DOCUMENT_VIEW', 'PAYROLL_VIEW');
