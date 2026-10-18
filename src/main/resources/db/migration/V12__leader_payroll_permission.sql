-- =====================================================================
-- V12 — Completar permisos de autoservicio para líderes
-- =====================================================================

INSERT INTO dbo.sec_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM dbo.sec_role r
CROSS JOIN dbo.sec_permission p
WHERE r.code = 'LEADER'
  AND p.code = 'PAYROLL_VIEW_SELF'
  AND NOT EXISTS (
      SELECT 1
      FROM dbo.sec_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
