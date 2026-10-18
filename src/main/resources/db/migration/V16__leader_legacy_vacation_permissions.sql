-- =====================================================================
-- V16 — Retirar permisos históricos amplios del líder
-- =====================================================================
-- El líder conserva VACATION_VIEW_TEAM y VACATION_APPROVE_TEAM.
-- Los permisos históricos VACATION_VIEW / VACATION_APPROVE permitían
-- anotaciones amplias; la autorización de recurso ya usa los permisos
-- de alcance, pero se retiran para reducir superficie de ataque.

DELETE rp
FROM dbo.sec_role_permission rp
JOIN dbo.sec_role r ON r.id = rp.role_id
JOIN dbo.sec_permission p ON p.id = rp.permission_id
WHERE r.code = 'LEADER'
  AND p.code IN ('VACATION_VIEW', 'VACATION_APPROVE');
