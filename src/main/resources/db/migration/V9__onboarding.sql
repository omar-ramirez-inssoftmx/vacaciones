-- =====================================================================
-- V9 — Onboarding: recorrido inicial para usuarios nuevos
-- =====================================================================

IF COL_LENGTH('dbo.users', 'onboarding_completed') IS NULL
BEGIN
    ALTER TABLE dbo.users
        ADD onboarding_completed BIT NOT NULL
            CONSTRAINT DF_users_onboarding_completed DEFAULT 0;

    -- Los usuarios existentes ya conocen la aplicación; los usuarios
    -- creados a partir de ahora recibirán el recorrido (default 0).
    UPDATE dbo.users SET onboarding_completed = 1;
END;
