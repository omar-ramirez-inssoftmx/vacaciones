-- =====================================================================
-- Seed SOLO PARA DESARROLLO LOCAL
-- ---------------------------------------------------------------------
-- Ejecutar DESPUÉS de que Flyway haya aplicado V1–V8 en la base local.
-- Crea tres usuarios de prueba:
--   admin       / Admin1234
--   lider       / Lider1234
--   colaborador / Colaborador1234
-- =====================================================================

SET NOCOUNT ON;

DECLARE @companyId BIGINT;
DECLARE @branchId BIGINT;
DECLARE @departmentId BIGINT;
DECLARE @positionId BIGINT;
DECLARE @statusActivo BIGINT;
DECLARE @modalityPresencial BIGINT;

SELECT @companyId = id
FROM dbo.org_company
WHERE code = 'INSSOFT' AND deleted_at IS NULL;

IF @companyId IS NULL
    THROW 51001, 'No existe la empresa INSSOFT. Verifica que Flyway V3/V5 se hayan aplicado.', 1;

SELECT @branchId = id
FROM dbo.org_branch
WHERE company_id = @companyId AND code = 'MATRIZ' AND deleted_at IS NULL;

SELECT @departmentId = id
FROM dbo.org_department
WHERE branch_id = @branchId AND code = 'GENERAL' AND deleted_at IS NULL;

SELECT @positionId = id
FROM dbo.org_position
WHERE department_id = @departmentId AND code = 'GENERAL' AND deleted_at IS NULL;

SELECT @statusActivo = i.id
FROM dbo.md_catalog_item i
JOIN dbo.md_catalog c ON c.id = i.catalog_id
WHERE c.code = 'EMPLOYMENT_STATUS' AND i.code = 'ACTIVO' AND i.deleted_at IS NULL;

SELECT @modalityPresencial = i.id
FROM dbo.md_catalog_item i
JOIN dbo.md_catalog c ON c.id = i.catalog_id
WHERE c.code = 'WORK_MODALITY' AND i.code = 'PRESENCIAL' AND i.deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- Usuarios
-- ---------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'admin')
BEGIN
    INSERT INTO dbo.users (
        name, username, password, email,
        vacation_days_available, role, enabled,
        hire_date, vacation_days_entitled, temporary_password
    )
    VALUES (
        'Administrador INSSOFT', 'admin',
        '$2a$10$Ip5vLn9Ee1De0mfFiz0XuOP2Q2D0f3YQv/m.PpX7KeBmOpdA6PH3y',
        'admin@inssoftmx.com', 20, 'ROLE_ADMIN', 1,
        '2020-01-15', 20, 0
    );
END;

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'lider')
BEGIN
    INSERT INTO dbo.users (
        name, username, password, email,
        vacation_days_available, role, enabled,
        hire_date, vacation_days_entitled, temporary_password
    )
    VALUES (
        'Líder de Equipo', 'lider',
        '$2a$10$IP72wm/3pHyKdagLhkT0IOBmaQUhAzn3/fCnBK67Km7J38YTLS2De',
        'lider@inssoftmx.com', 15, 'ROLE_LIDER', 1,
        '2021-03-01', 15, 0
    );
END;

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'colaborador')
BEGIN
    INSERT INTO dbo.users (
        name, username, password, email,
        vacation_days_available, role, enabled,
        hire_date, vacation_days_entitled, temporary_password
    )
    VALUES (
        'Colaborador de Prueba', 'colaborador',
        '$2a$10$Ub2aNZjlIXm.7ptfvahxBuCWe9bq53.qLW4I38lqoxlclogGTkoy2',
        'colaborador@inssoftmx.com', 12, 'ROLE_COLABORADOR', 1,
        '2022-06-01', 12, 0
    );
END;

DECLARE @adminUserId BIGINT = (SELECT id FROM dbo.users WHERE username = 'admin');
DECLARE @leaderUserId BIGINT = (SELECT id FROM dbo.users WHERE username = 'lider');
DECLARE @employeeUserId BIGINT = (SELECT id FROM dbo.users WHERE username = 'colaborador');

-- ---------------------------------------------------------------------
-- Empleados (admin, líder, colaborador)
-- ---------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM dbo.emp_employee WHERE user_id = @adminUserId AND deleted_at IS NULL)
BEGIN
    INSERT INTO dbo.emp_employee (
        user_id, company_id, branch_id, department_id, position_id,
        employee_number, first_name, last_name, corporate_email,
        hire_date, status_id, work_modality_id
    )
    VALUES (
        @adminUserId, @companyId, @branchId, @departmentId, @positionId,
        'ADMIN-001', 'Administrador', 'INSSOFT', 'admin@inssoftmx.com',
        '2020-01-15', @statusActivo, @modalityPresencial
    );
END;

DECLARE @adminEmployeeId BIGINT = (
    SELECT id FROM dbo.emp_employee WHERE user_id = @adminUserId AND deleted_at IS NULL
);

IF NOT EXISTS (SELECT 1 FROM dbo.emp_employee WHERE user_id = @leaderUserId AND deleted_at IS NULL)
BEGIN
    INSERT INTO dbo.emp_employee (
        user_id, company_id, branch_id, department_id, position_id, manager_id,
        employee_number, first_name, last_name, corporate_email,
        hire_date, status_id, work_modality_id
    )
    VALUES (
        @leaderUserId, @companyId, @branchId, @departmentId, @positionId, @adminEmployeeId,
        'LIDER-001', 'Líder', 'de Equipo', 'lider@inssoftmx.com',
        '2021-03-01', @statusActivo, @modalityPresencial
    );
END;

DECLARE @leaderEmployeeId BIGINT = (
    SELECT id FROM dbo.emp_employee WHERE user_id = @leaderUserId AND deleted_at IS NULL
);

IF NOT EXISTS (SELECT 1 FROM dbo.emp_employee WHERE user_id = @employeeUserId AND deleted_at IS NULL)
BEGIN
    INSERT INTO dbo.emp_employee (
        user_id, company_id, branch_id, department_id, position_id, manager_id,
        employee_number, first_name, last_name, corporate_email,
        hire_date, status_id, work_modality_id
    )
    VALUES (
        @employeeUserId, @companyId, @branchId, @departmentId, @positionId, @leaderEmployeeId,
        'EMP-001', 'Colaborador', 'de Prueba', 'colaborador@inssoftmx.com',
        '2022-06-01', @statusActivo, @modalityPresencial
    );
END;

UPDATE u
SET employee_id = e.id
FROM dbo.users u
JOIN dbo.emp_employee e ON e.user_id = u.id AND e.deleted_at IS NULL
WHERE u.employee_id IS NULL;

-- ---------------------------------------------------------------------
-- Roles RBAC
-- ---------------------------------------------------------------------
INSERT INTO dbo.sec_user_role (user_id, role_id)
SELECT u.id, r.id
FROM dbo.users u
JOIN dbo.sec_role r ON r.code = CASE u.username
        WHEN 'admin' THEN 'ADMIN'
        WHEN 'lider' THEN 'LEADER'
        ELSE 'EMPLOYEE'
    END
WHERE u.username IN ('admin', 'lider', 'colaborador')
  AND NOT EXISTS (
      SELECT 1 FROM dbo.sec_user_role ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

SELECT 'Seed local aplicado' AS result,
       (SELECT COUNT(*) FROM dbo.users WHERE username IN ('admin','lider','colaborador')) AS usuarios,
       (SELECT COUNT(*) FROM dbo.emp_employee WHERE employee_number IN ('ADMIN-001','LIDER-001','EMP-001')) AS empleados;
