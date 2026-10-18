-- =====================================================================
-- V1 — Baseline del sistema legado (INFERIDO DE JPA)
-- ---------------------------------------------------------------------
-- IMPORTANTE:
--   * En bases existentes Flyway hará baseline en V1 y NO ejecutará este
--     script (baseline-on-migrate=true, baseline-version=1).
--   * En una base vacía este script crea el esquema legado mínimo.
--   * Antes de usar en producción, reemplazar por el DDL real generado
--     desde la base (ver docs/sql/00_introspection_sqlserver.sql).
-- =====================================================================

IF OBJECT_ID('dbo.users', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.users (
        id                      BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_users PRIMARY KEY,
        name                    NVARCHAR(255) NULL,
        username                NVARCHAR(255) NOT NULL,
        password                NVARCHAR(255) NOT NULL,
        email                   NVARCHAR(150) NULL,
        vacation_days_available INT NOT NULL,
        role                    NVARCHAR(255) NULL,
        enabled                 BIT NOT NULL,
        hire_date               DATE NOT NULL,
        vacation_days_entitled  INT NOT NULL,
        temporary_password      BIT NOT NULL
    );

    CREATE UNIQUE INDEX UX_users_username ON dbo.users(username);
    CREATE UNIQUE INDEX UX_users_email ON dbo.users(email) WHERE email IS NOT NULL;
END;

IF OBJECT_ID('dbo.vacation_requests', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vacation_requests (
        id                    BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_vacation_requests PRIMARY KEY,
        username              NVARCHAR(255) NOT NULL,
        start_date            DATE NOT NULL,
        end_date              DATE NOT NULL,
        days                  INT NOT NULL,
        status                NVARCHAR(255) NOT NULL,
        requested_by_username NVARCHAR(255) NULL,
        decided_by            NVARCHAR(255) NULL,
        decided_at            DATETIME2(3) NULL,
        user_id               BIGINT NOT NULL,
        leader_id             BIGINT NULL,
        leader_decision       NVARCHAR(255) NULL,
        leader_decided_at     DATETIME2(3) NULL,
        leader_comment        NVARCHAR(255) NULL,
        created_at            DATETIME2(3) NULL,
        admin_comment         NVARCHAR(255) NULL,
        CONSTRAINT FK_vacation_requests_user
            FOREIGN KEY (user_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_vacation_requests_leader
            FOREIGN KEY (leader_id) REFERENCES dbo.users(id)
    );

    CREATE INDEX IX_vacation_requests_user_dates
        ON dbo.vacation_requests(user_id, start_date, end_date);
    CREATE INDEX IX_vacation_requests_status
        ON dbo.vacation_requests(status, start_date);
    CREATE INDEX IX_vacation_requests_leader_status
        ON dbo.vacation_requests(leader_id, status);
END;

IF OBJECT_ID('dbo.vacation_history', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vacation_history (
        id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_vacation_history PRIMARY KEY,
        fecha       DATETIME2(3) NULL,
        accion      NVARCHAR(255) NULL,
        comentario  NVARCHAR(255) NULL,
        vacation_id BIGINT NULL,
        admin_id    BIGINT NULL,
        CONSTRAINT FK_vacation_history_request
            FOREIGN KEY (vacation_id) REFERENCES dbo.vacation_requests(id),
        CONSTRAINT FK_vacation_history_admin
            FOREIGN KEY (admin_id) REFERENCES dbo.users(id)
    );
END;

IF OBJECT_ID('dbo.vacaciones', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vacaciones (
        id           BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_vacaciones PRIMARY KEY,
        usuario      NVARCHAR(255) NULL,
        fecha_inicio DATE NULL,
        fecha_fin    DATE NULL,
        estatus      NVARCHAR(255) NULL
    );
END;

IF OBJECT_ID('dbo.tb_politicas_empresa', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.tb_politicas_empresa (
        idpolitica       INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_tb_politicas_empresa PRIMARY KEY,
        nombre_politica  NVARCHAR(255) NOT NULL,
        documento_pdf    VARBINARY(MAX) NULL
    );
END;
