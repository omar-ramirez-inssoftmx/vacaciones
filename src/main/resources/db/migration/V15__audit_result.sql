-- =====================================================================
-- V15 — Resultado explícito en auditoría
-- =====================================================================

IF COL_LENGTH('dbo.aud_audit_log', 'result') IS NULL
BEGIN
    EXEC(N'ALTER TABLE dbo.aud_audit_log
        ADD result NVARCHAR(20) NOT NULL
            CONSTRAINT DF_aud_audit_log_result DEFAULT ''SUCCESS'';');
END;
