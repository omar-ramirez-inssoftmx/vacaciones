-- =====================================================================
-- V10 — Preferencia visual del usuario (tema claro/oscuro)
-- =====================================================================

IF COL_LENGTH('dbo.users', 'theme_preference') IS NULL
BEGIN
    EXEC(N'ALTER TABLE dbo.users
        ADD theme_preference NVARCHAR(10) NOT NULL
            CONSTRAINT DF_users_theme_preference DEFAULT ''DARK'';');
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = 'CK_users_theme_preference'
      AND parent_object_id = OBJECT_ID('dbo.users')
)
BEGIN
    EXEC(N'ALTER TABLE dbo.users
        ADD CONSTRAINT CK_users_theme_preference
            CHECK (theme_preference IN (''LIGHT'', ''DARK''));');
END;
