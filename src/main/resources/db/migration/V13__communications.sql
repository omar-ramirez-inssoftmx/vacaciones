-- =====================================================================
-- V13 — Comunicados internos
-- =====================================================================

IF OBJECT_ID('dbo.com_announcement', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.com_announcement (
        id              BIGINT IDENTITY(1,1) NOT NULL,
        uuid            UNIQUEIDENTIFIER NOT NULL
                        CONSTRAINT DF_com_announcement_uuid DEFAULT NEWID(),
        company_id      BIGINT NOT NULL,
        title           NVARCHAR(200) NOT NULL,
        body            NVARCHAR(MAX) NOT NULL,
        priority        NVARCHAR(20) NOT NULL
                        CONSTRAINT DF_com_announcement_priority DEFAULT 'NORMAL',
        published_at    DATETIME2(3) NULL,
        expires_at      DATETIME2(3) NULL,
        created_by      BIGINT NULL,
        enabled         BIT NOT NULL CONSTRAINT DF_com_announcement_enabled DEFAULT 1,
        created_at      DATETIME2(3) NOT NULL
                        CONSTRAINT DF_com_announcement_created DEFAULT SYSUTCDATETIME(),
        updated_at      DATETIME2(3) NULL,
        version         INT NOT NULL CONSTRAINT DF_com_announcement_version DEFAULT 0,
        deleted_at      DATETIME2(3) NULL,
        CONSTRAINT PK_com_announcement PRIMARY KEY (id),
        CONSTRAINT UQ_com_announcement_uuid UNIQUE (uuid),
        CONSTRAINT FK_com_announcement_company FOREIGN KEY (company_id) REFERENCES dbo.org_company(id),
        CONSTRAINT FK_com_announcement_user FOREIGN KEY (created_by) REFERENCES dbo.users(id),
        CONSTRAINT CK_com_announcement_priority CHECK (priority IN ('NORMAL', 'IMPORTANT', 'URGENT'))
    );

    CREATE INDEX IX_com_announcement_company
        ON dbo.com_announcement(company_id, published_at DESC);
END;
