-- =====================================================================
-- V8 — Documents: tipos, metadatos y almacenamiento de contenido
-- ---------------------------------------------------------------------
-- Requiere V4 (catálogos), V5 (empleados) y V2 (usuarios/RBAC).
-- =====================================================================

IF OBJECT_ID('dbo.doc_document_type', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.doc_document_type (
        id               BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_doc_document_type PRIMARY KEY,
        uuid             UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_doc_document_type_uuid DEFAULT NEWID(),
        enabled          BIT NOT NULL CONSTRAINT DF_doc_document_type_enabled DEFAULT 1,
        created_at       DATETIME2(3) NOT NULL CONSTRAINT DF_doc_document_type_created_at DEFAULT SYSUTCDATETIME(),
        updated_at       DATETIME2(3) NULL,
        version          INT NOT NULL CONSTRAINT DF_doc_document_type_version DEFAULT 0,
        deleted_at       DATETIME2(3) NULL,
        catalog_item_id  BIGINT NOT NULL,
        requires_expiry  BIT NOT NULL CONSTRAINT DF_doc_document_type_requires_expiry DEFAULT 0,
        employee_visible BIT NOT NULL CONSTRAINT DF_doc_document_type_employee_visible DEFAULT 1,
        max_size_mb      INT NOT NULL CONSTRAINT DF_doc_document_type_max_size DEFAULT 20,
        allowed_mime     NVARCHAR(300) NOT NULL
            CONSTRAINT DF_doc_document_type_allowed_mime DEFAULT 'application/pdf,image/jpeg,image/png',
        sort_order       INT NOT NULL CONSTRAINT DF_doc_document_type_sort_order DEFAULT 0,
        CONSTRAINT FK_doc_document_type_catalog_item
            FOREIGN KEY (catalog_item_id) REFERENCES dbo.md_catalog_item(id)
    );

    CREATE UNIQUE INDEX UX_doc_document_type_uuid ON dbo.doc_document_type(uuid);
    CREATE UNIQUE INDEX UX_doc_document_type_catalog_item ON dbo.doc_document_type(catalog_item_id)
        WHERE deleted_at IS NULL;
END;

IF OBJECT_ID('dbo.doc_document_content', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.doc_document_content (
        id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_doc_document_content PRIMARY KEY,
        storage_key UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_doc_document_content_key DEFAULT NEWID(),
        sha256      NVARCHAR(64) NOT NULL,
        size_bytes  BIGINT NOT NULL,
        content     VARBINARY(MAX) NOT NULL,
        created_at  DATETIME2(3) NOT NULL CONSTRAINT DF_doc_document_content_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UX_doc_document_content_key UNIQUE (storage_key)
    );
END;

IF OBJECT_ID('dbo.doc_document', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.doc_document (
        id               BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_doc_document PRIMARY KEY,
        uuid             UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_doc_document_uuid DEFAULT NEWID(),
        enabled          BIT NOT NULL CONSTRAINT DF_doc_document_enabled DEFAULT 1,
        created_at       DATETIME2(3) NOT NULL CONSTRAINT DF_doc_document_created_at DEFAULT SYSUTCDATETIME(),
        updated_at       DATETIME2(3) NULL,
        version          INT NOT NULL CONSTRAINT DF_doc_document_version DEFAULT 0,
        deleted_at       DATETIME2(3) NULL,
        company_id       BIGINT NOT NULL,
        employee_id      BIGINT NULL,
        type_id          BIGINT NOT NULL,
        name             NVARCHAR(200) NOT NULL,
        description      NVARCHAR(500) NULL,
        file_name        NVARCHAR(255) NOT NULL,
        mime_type        NVARCHAR(100) NOT NULL,
        size_bytes       BIGINT NOT NULL,
        sha256           NVARCHAR(64) NOT NULL,
        storage_provider NVARCHAR(30) NOT NULL CONSTRAINT DF_doc_document_provider DEFAULT 'DATABASE',
        storage_key      UNIQUEIDENTIFIER NOT NULL,
        visibility       NVARCHAR(20) NOT NULL CONSTRAINT DF_doc_document_visibility DEFAULT 'EMPLOYEE',
        expires_at       DATE NULL,
        uploaded_by      BIGINT NULL,
        CONSTRAINT FK_doc_document_company FOREIGN KEY (company_id) REFERENCES dbo.org_company(id),
        CONSTRAINT FK_doc_document_employee FOREIGN KEY (employee_id) REFERENCES dbo.emp_employee(id),
        CONSTRAINT FK_doc_document_type FOREIGN KEY (type_id) REFERENCES dbo.doc_document_type(id),
        CONSTRAINT FK_doc_document_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES dbo.users(id),
        CONSTRAINT FK_doc_document_storage_key
            FOREIGN KEY (storage_key) REFERENCES dbo.doc_document_content(storage_key),
        CONSTRAINT CK_doc_document_visibility CHECK (visibility IN ('EMPLOYEE', 'HR', 'ADMIN')),
        CONSTRAINT CK_doc_document_size CHECK (size_bytes > 0)
    );

    CREATE UNIQUE INDEX UX_doc_document_uuid ON dbo.doc_document(uuid);
    CREATE UNIQUE INDEX UX_doc_document_storage_key ON dbo.doc_document(storage_key);
    CREATE INDEX IX_doc_document_employee ON dbo.doc_document(employee_id, type_id, deleted_at);
    CREATE INDEX IX_doc_document_company ON dbo.doc_document(company_id, type_id, deleted_at);
    CREATE INDEX IX_doc_document_expires ON dbo.doc_document(expires_at);
END;

-- ---------------------------------------------------------------------
-- Tipos de documento: reutilizan los items del catálogo DOCUMENT_TYPE
-- ---------------------------------------------------------------------
INSERT INTO dbo.doc_document_type (
    catalog_item_id, requires_expiry, employee_visible,
    max_size_mb, allowed_mime, sort_order
)
SELECT
    i.id,
    CASE WHEN i.code IN ('IDENTIFICACION', 'CONTRATO', 'COMPROBANTE_DOMICILIO') THEN 1 ELSE 0 END,
    1,
    20,
    'application/pdf,image/jpeg,image/png',
    i.sort_order
FROM dbo.md_catalog_item i
JOIN dbo.md_catalog c ON c.id = i.catalog_id
WHERE c.code = 'DOCUMENT_TYPE'
  AND i.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM dbo.doc_document_type t
      WHERE t.catalog_item_id = i.id AND t.deleted_at IS NULL
  );

-- ---------------------------------------------------------------------
-- Endurecimiento: los líderes no deben ver documentos de empleados.
-- ---------------------------------------------------------------------
DELETE rp
FROM dbo.sec_role_permission rp
JOIN dbo.sec_role r ON r.id = rp.role_id
JOIN dbo.sec_permission p ON p.id = rp.permission_id
WHERE r.code = 'LEADER'
  AND p.code = 'DOCUMENT_VIEW';
