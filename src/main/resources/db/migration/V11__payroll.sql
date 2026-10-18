-- =====================================================================
-- V11 — Nómina: periodos y recibos de pago
-- =====================================================================

IF OBJECT_ID('dbo.pay_period', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.pay_period (
        id              BIGINT IDENTITY(1,1) NOT NULL,
        uuid            UNIQUEIDENTIFIER NOT NULL
                        CONSTRAINT DF_pay_period_uuid DEFAULT NEWID(),
        company_id      BIGINT NOT NULL,
        period_year     INT NOT NULL,
        period_month    INT NOT NULL,
        period_type     NVARCHAR(20) NOT NULL
                        CONSTRAINT DF_pay_period_type DEFAULT 'MONTHLY',
        start_date      DATE NOT NULL,
        end_date        DATE NOT NULL,
        status          NVARCHAR(20) NOT NULL
                        CONSTRAINT DF_pay_period_status DEFAULT 'OPEN',
        enabled         BIT NOT NULL CONSTRAINT DF_pay_period_enabled DEFAULT 1,
        created_at      DATETIME2(3) NOT NULL
                        CONSTRAINT DF_pay_period_created DEFAULT SYSUTCDATETIME(),
        updated_at      DATETIME2(3) NULL,
        version         INT NOT NULL CONSTRAINT DF_pay_period_version DEFAULT 0,
        deleted_at      DATETIME2(3) NULL,
        CONSTRAINT PK_pay_period PRIMARY KEY (id),
        CONSTRAINT UQ_pay_period_uuid UNIQUE (uuid),
        CONSTRAINT FK_pay_period_company FOREIGN KEY (company_id) REFERENCES dbo.org_company(id),
        CONSTRAINT CK_pay_period_month CHECK (period_month BETWEEN 1 AND 12),
        CONSTRAINT CK_pay_period_year CHECK (period_year BETWEEN 2000 AND 2100),
        CONSTRAINT CK_pay_period_dates CHECK (end_date >= start_date),
        CONSTRAINT CK_pay_period_type CHECK (period_type IN ('MONTHLY', 'BIWEEKLY', 'EXTRA')),
        CONSTRAINT CK_pay_period_status CHECK (status IN ('OPEN', 'CLOSED'))
    );

    CREATE UNIQUE INDEX UX_pay_period_company_period
        ON dbo.pay_period(company_id, period_year, period_month, period_type)
        WHERE deleted_at IS NULL;

    CREATE INDEX IX_pay_period_company ON dbo.pay_period(company_id, period_year DESC, period_month DESC);
END;

IF OBJECT_ID('dbo.pay_receipt', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.pay_receipt (
        id                  BIGINT IDENTITY(1,1) NOT NULL,
        uuid                UNIQUEIDENTIFIER NOT NULL
                            CONSTRAINT DF_pay_receipt_uuid DEFAULT NEWID(),
        company_id          BIGINT NOT NULL,
        employee_id         BIGINT NOT NULL,
        period_id           BIGINT NOT NULL,
        file_name           NVARCHAR(255) NOT NULL,
        mime_type           NVARCHAR(100) NOT NULL,
        size_bytes          BIGINT NOT NULL,
        sha256              CHAR(64) NOT NULL,
        storage_provider    NVARCHAR(30) NOT NULL
                            CONSTRAINT DF_pay_receipt_provider DEFAULT 'DATABASE',
        storage_key         UNIQUEIDENTIFIER NOT NULL,
        uploaded_by         BIGINT NULL,
        enabled             BIT NOT NULL CONSTRAINT DF_pay_receipt_enabled DEFAULT 1,
        created_at          DATETIME2(3) NOT NULL
                            CONSTRAINT DF_pay_receipt_created DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2(3) NULL,
        version             INT NOT NULL CONSTRAINT DF_pay_receipt_version DEFAULT 0,
        deleted_at          DATETIME2(3) NULL,
        CONSTRAINT PK_pay_receipt PRIMARY KEY (id),
        CONSTRAINT UQ_pay_receipt_uuid UNIQUE (uuid),
        CONSTRAINT UQ_pay_receipt_storage UNIQUE (storage_key),
        CONSTRAINT FK_pay_receipt_company FOREIGN KEY (company_id) REFERENCES dbo.org_company(id),
        CONSTRAINT FK_pay_receipt_employee FOREIGN KEY (employee_id) REFERENCES dbo.emp_employee(id),
        CONSTRAINT FK_pay_receipt_period FOREIGN KEY (period_id) REFERENCES dbo.pay_period(id),
        CONSTRAINT FK_pay_receipt_user FOREIGN KEY (uploaded_by) REFERENCES dbo.users(id),
        CONSTRAINT CK_pay_receipt_size CHECK (size_bytes > 0),
        CONSTRAINT CK_pay_receipt_mime CHECK (mime_type IN ('application/pdf'))
    );

    CREATE UNIQUE INDEX UX_pay_receipt_employee_period
        ON dbo.pay_receipt(employee_id, period_id)
        WHERE deleted_at IS NULL;

    CREATE INDEX IX_pay_receipt_company ON dbo.pay_receipt(company_id, created_at DESC);
    CREATE INDEX IX_pay_receipt_employee ON dbo.pay_receipt(employee_id, created_at DESC);
END;
