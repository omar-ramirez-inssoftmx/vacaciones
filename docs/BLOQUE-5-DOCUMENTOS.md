# Bloque 5 — Documents (documentos de empleados)

| Campo | Valor |
|---|---|
| Rama | `feature/documents-module` (base `develop`) |
| Alcance | Fase 7 del roadmap |
| Fecha | 2026-06-10 |
| Estado | Implementado, compilado y probado (`mvn clean verify`) |
| Pruebas | 184 tests, 0 fallos |
| Cobertura código nuevo | 75.8% instrucciones (JaCoCo) |

---

## 1. Qué se implementó

### 1.1 Migración `V8__documents.sql`

- `doc_document_type`:
  - reutiliza los items del catálogo `DOCUMENT_TYPE` (no duplica catálogos),
  - agrega comportamiento: `requires_expiry`, `employee_visible`, `max_size_mb`, `allowed_mime`, orden.
- `doc_document_content`:
  - almacenamiento binario (`VARBINARY(MAX)`),
  - `storage_key` único,
  - SHA-256 y tamaño.
- `doc_document`:
  - metadatos del documento,
  - empresa, empleado (opcional), tipo,
  - nombre, descripción, nombre de archivo, MIME, tamaño, hash,
  - proveedor/llave de almacenamiento,
  - visibilidad `EMPLOYEE`, `HR`, `ADMIN`,
  - fecha de vencimiento, usuario que carga,
  - soft delete y versión.
- Seed de tipos desde el catálogo existente.
- **Endurecimiento**: se retira `DOCUMENT_VIEW` del rol `LEADER`; los líderes no deben ver documentos de empleados.

### 1.2 Almacenamiento abstracto

```java
public interface FileStorageService {
    StoredFile store(InputStream input, String originalFileName, String mimeType);
    InputStream open(UUID storageKey);
    void delete(UUID storageKey);
    String provider();
}
```

Implementación inicial: `DatabaseFileStorageService` sobre `doc_document_content`.

La interfaz permite migrar posteriormente a Azure Blob Storage, S3 o servidor privado sin cambiar el dominio de documentos.

### 1.3 Seguridad y permisos

- `DOCUMENT_VIEW_SELF`: el empleado consulta/descarga sus documentos visibles.
- `DOCUMENT_VIEW`: RRHH/Admin consulta documentos.
- `DOCUMENT_UPLOAD`: carga de documentos.
- `DOCUMENT_DELETE`: baja lógica.
- El empleado solo ve documentos:
  - propios,
  - con visibilidad `EMPLOYEE`,
  - de tipos marcados `employee_visible`.
- RRHH/Admin queda limitado a su empresa; puede indicar `companyUuid` si administra varias.
- Descargas autorizadas y auditadas (`DOCUMENT_DOWNLOAD`).
- Cargas y bajas auditadas (`DOCUMENT_UPLOAD`, `DOCUMENT_DELETE`).
- Validaciones:
  - archivo obligatorio y no vacío,
  - tamaño máximo por tipo,
  - MIME permitido (con inferencia por extensión si el cliente envía `application/octet-stream`),
  - fecha de vencimiento cuando el tipo la requiere,
  - tipo de documento activo,
  - nombre/descripción con longitud válida,
  - sanitización del nombre de archivo.

---

## 2. Endpoints

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/documents/types` | `DOCUMENT_VIEW` / `DOCUMENT_UPLOAD` / `DOCUMENT_VIEW_SELF` |
| GET | `/api/v1/documents/me` | `DOCUMENT_VIEW_SELF` |
| GET | `/api/v1/documents?companyUuid=&employeeUuid=&typeCode=&q=&page=&size=` | `DOCUMENT_VIEW` |
| GET | `/api/v1/documents/{uuid}` | `DOCUMENT_VIEW` / `DOCUMENT_VIEW_SELF` |
| GET | `/api/v1/documents/{uuid}/download` | `DOCUMENT_VIEW` / `DOCUMENT_VIEW_SELF` |
| POST | `/api/v1/documents` (multipart) | `DOCUMENT_UPLOAD` |
| DELETE | `/api/v1/documents/{uuid}` | `DOCUMENT_DELETE` |

### Ejemplo de carga

```bash
curl -X POST http://localhost:8443/api/v1/documents \
  -H "Authorization: Bearer $TOKEN" \
  -F "employeeUuid=UUID_EMPLEADO" \
  -F "typeCode=CONTRATO" \
  -F "name=Contrato individual" \
  -F "description=Contrato firmado" \
  -F "visibility=EMPLOYEE" \
  -F "expiresAt=2027-01-01" \
  -F "file=@C:/ruta/contrato.pdf"
```

### Ejemplo de descarga

```bash
curl -L http://localhost:8443/api/v1/documents/UUID_DOCUMENTO/download \
  -H "Authorization: Bearer $TOKEN" \
  -o contrato.pdf
```

---

## 3. Validación

```bash
mvn clean verify
```

Resultado esperado: `BUILD SUCCESS`, 184 tests, cobertura de código nuevo ≈ 76%.

SQL en copia:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;

SELECT COUNT(*) FROM doc_document_type;
SELECT COUNT(*) FROM doc_document_content;
SELECT COUNT(*) FROM doc_document;

SELECT t.id, i.code, i.name, t.requires_expiry, t.employee_visible, t.max_size_mb
FROM doc_document_type t
JOIN md_catalog_item i ON i.id = t.catalog_item_id
ORDER BY i.sort_order;
```

Flujo funcional:

1. Login como RRHH/Admin.
2. `GET /api/v1/documents/types`.
3. Cargar un PDF para un empleado.
4. `GET /api/v1/documents?employeeUuid=...`.
5. Descargar el documento y verificar `Content-Disposition`/MIME.
6. Login como el empleado y `GET /api/v1/documents/me`.
7. Verificar que un empleado no puede descargar documentos de otro.
8. Verificar auditoría en `aud_audit_log` (`DOCUMENT_UPLOAD`, `DOCUMENT_DOWNLOAD`, `DOCUMENT_DELETE`).

---

## 4. Pendientes y límites conocidos

| Tema | Estado |
|---|---|
| `V1`/`V8` contra SQL Server real | Validar en copia antes de producción |
| Antivirus / malware scan | Pendiente |
| Cifrado en reposo de archivos | Pendiente (SQL Server TDE/Always Encrypted o storage cifrado) |
| Azure Blob / S3 | Interfaz lista; implementación futura |
| Migración de políticas PDF legadas (`tb_politicas_empresa`) | Pendiente |
| Versionado/reemplazo de documentos | Pendiente |
| Firma electrónica | Pendiente |
| Retención y purga de contenido tras soft delete | Pendiente |
| Carga autogestionada por el empleado | Actualmente carga RRHH/Admin |
| Vista previa / thumbnails | Pendiente |
| Límite global de carga | 20 MB (`spring.servlet.multipart`) y por tipo |

---

## 5. Siguiente bloque recomendado

**Fase 8 — Payroll:** recibos de pago, periodos, carga administrativa, consulta y descarga autorizada por empleado. Después Communication/Notifications.

Antes de producción: validar V1–V8 en copia, revisar políticas de retención de documentos con RRHH/Legal, rotar credenciales y ejecutar migraciones con respaldo verificado.
