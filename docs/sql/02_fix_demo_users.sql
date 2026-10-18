-- =====================================================================
-- Usuarios de demostración — SOLO ENTORNO DE PRUEBAS
-- ---------------------------------------------------------------------
-- Deja estas credenciales:
--   admin       / Admin1234
--   lider       / Lider1234
--   colaborador / Colaborador1234
--
-- Los hashes están calculados con BCrypt (mismo PasswordEncoder de la app).
-- Ejecutar después del seed de empleados (docs/sql/01_seed_local_admin.sql).
-- =====================================================================

SET NOCOUNT ON;

UPDATE dbo.users
SET password = '$2a$10$Ip5vLn9Ee1De0mfFiz0XuOP2Q2D0f3YQv/m.PpX7KeBmOpdA6PH3y',
    enabled = 1,
    temporary_password = 0,
    role = 'ROLE_ADMIN',
    onboarding_completed = 0
WHERE username = 'admin';

UPDATE dbo.users
SET password = '$2a$10$IP72wm/3pHyKdagLhkT0IOBmaQUhAzn3/fCnBK67Km7J38YTLS2De',
    enabled = 1,
    temporary_password = 0,
    role = 'ROLE_LIDER',
    onboarding_completed = 0
WHERE username = 'lider';

UPDATE dbo.users
SET password = '$2a$10$Ub2aNZjlIXm.7ptfvahxBuCWe9bq53.qLW4I38lqoxlclogGTkoy2',
    enabled = 1,
    temporary_password = 0,
    role = 'ROLE_COLABORADOR',
    onboarding_completed = 0
WHERE username = 'colaborador';

-- Reasignar el rol RBAC esperado a los usuarios demo.
DELETE ur
FROM dbo.sec_user_role ur
JOIN dbo.users u ON u.id = ur.user_id
WHERE u.username IN ('admin', 'lider', 'colaborador');

INSERT INTO dbo.sec_user_role (user_id, role_id)
SELECT u.id, r.id
FROM dbo.users u
JOIN dbo.sec_role r ON r.code = CASE u.username
        WHEN 'admin' THEN 'ADMIN'
        WHEN 'lider' THEN 'LEADER'
        WHEN 'colaborador' THEN 'EMPLOYEE'
    END
WHERE u.username IN ('admin', 'lider', 'colaborador')
  AND NOT EXISTS (
      SELECT 1 FROM dbo.sec_user_role ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

SELECT u.username, u.role, u.enabled, u.temporary_password, u.onboarding_completed,
       r.code AS rbac_role, e.employee_number
FROM dbo.users u
LEFT JOIN dbo.sec_user_role ur ON ur.user_id = u.id
LEFT JOIN dbo.sec_role r ON r.id = ur.role_id
LEFT JOIN dbo.emp_employee e ON e.user_id = u.id AND e.deleted_at IS NULL
WHERE u.username IN ('admin', 'lider', 'colaborador')
ORDER BY u.username;
