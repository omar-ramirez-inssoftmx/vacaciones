# Bloque 1 — Foundation + Master Data + Organization

| Campo | Valor |
|---|---|
| Rama | `feature/foundation-api` (base `develop`) |
| Alcance | Fases 1, 2 y 3 del roadmap |
| Fecha | 2026-06-10 |
| Estado | Implementado, compilado y probado (`mvn clean verify`) |
| Pruebas | 66 tests, 0 fallos |
| Cobertura código nuevo | 79.0% instrucciones (JaCoCo) |

> Este bloque **no** implementa empleados, vacaciones nuevas, nómina, documentos ni SSO. Prepara la fundación, la seguridad y los catálogos/organización sobre los que se montarán esas fases.

---

## 1. Qué se implementó

### PR1 — Build & configuración

- Spring Boot actualizado a **3.5.16** (Java 17).
- Dependencias agregadas: validation, Flyway (core + SQL Server), Springdoc OpenAPI, Actuator, OAuth2 Resource Server, tests, JaCoCo.
- Credenciales removidas de `application*.properties`; ahora se leen de variables de entorno.
- Perfiles: base (`application.properties`), `prod` (`application-prod.properties`), ejemplo local (`application-local.properties.example`).
- `ddl-auto=none` por defecto; `validate` en producción.
- Logs portables (`logs/inssoft-people.log`), sin `C:/logs`.
- Eliminados `data.sql` (obsoleto y con `{noop}`) y `keystore.p12` (no referenciado).
- `.gitignore` para secretos, logs, `application-local.properties`, etc.
- `README.md` reescrito con setup, variables, endpoints y validación.

### PR2 — Common kernel

- `BaseEntity`, `SoftDeleteEntity` (UUID, enabled, createdAt, updatedAt, version, deletedAt).
- `ApiResponse<T>`, `PageResponse<T>`, `FieldErrorResponse`, `ErrorCode`, `PageRequestFactory`.
- Excepciones de dominio y `GlobalExceptionHandler` (400/401/403/404/409/422/500).
- `RequestIdFilter` con `X-Request-Id` y MDC.
- `JpaAuditingConfig`, `OpenApiConfig`.
- Auditoría: `AuditLog`, `AuditLogRepository`, `AuditService`/`AuditServiceImpl` (transacción separada; no rompe la operación de negocio).
- Autorización por permisos de negocio: `AccessGuard` (`@PreAuthorize("@accessGuard.has('...')")`).
- `PasswordPolicy` (mín. 8, letra + número).

### PR3 — Seguridad

- **JWT** para `/api/v1`: `POST /api/v1/auth/login`, `GET /api/v1/auth/me`, `JwtConfig`, `JwtTokenService`, `AuthService`.
- Dos cadenas de seguridad:
  - API `/api/v1/**`: stateless, JWT, CSRF deshabilitado (no hay cookies).
  - Web legado: sesión + **CSRF habilitado** con `CookieCsrfTokenRepository`.
- Frontend legado: `global-loader.js` agrega `X-XSRF-TOKEN` a fetch no-GET; formularios convertidos a `th:action` para el token automático.
- **IDOR corregido**: el líder solo decide solicitudes donde es el líder asignado y en `PENDING_LEADER`.
- **Rechazo del líder** ahora deja `REJECTED` y **libera saldo** (antes pasaba a `PENDING_ADMIN`).
- **Admin** solo decide solicitudes accionables; rechazo libera saldo; ya no hay doble decisión.
- **`GET /admin/requests` ya no modifica datos** (antes convertía estados).
- **Cambio de contraseña** exige contraseña actual y aplica política.
- `CustomUserDetailsService` tolera rol/estado nulo.
- Se eliminó `GET /whoami` (debug).
- `HomeController` cuenta pendientes reales (`PENDING_LEADER` + `PENDING_ADMIN`).

### PR4 — Flyway (SQL Server)

- `V1__baseline_legacy.sql`: esquema legado inferido (users, vacation_requests, vacation_history, vacaciones, tb_politicas_empresa).
- `V2__common_security_audit.sql`: columnas comunes en `users`, RBAC (`sec_*`), auditoría (`aud_audit_log`), seeds de roles/permisos y mapeo de usuarios legados.
- `V3__organization.sql`: `org_company`, `org_branch`, `org_department`, `org_position`, FKs e índices filtrados.
- `V4__masterdata.sql`: `md_catalog`, `md_catalog_item`, seeds iniciales.
- Estrategia: aditiva, sin borrar ni renombrar tablas legadas; baseline-on-migrate para bases existentes.

### PR5 — Master Data

- Entidades `Catalog`, `CatalogItem` + repositorios.
- DTOs, mapper, servicio transaccional y controlador REST.
- Catálogos seed: género, estado civil, estatus de empleo, modalidad, contrato, nivel educativo, tipos de documento/incidencia/permiso/festivo.
- CRUD de items con soft delete, validación de duplicados, paginación no aplica (catálogos pequeños) y auditoría.

### PR6 — Organization

- `Company → Branch → Department → Position`.
- Reglas:
  - Código único por padre (empresa/sucursal/departamento según nivel).
  - Jerarquía de departamentos: padre de la misma sucursal, sin ciclos, sin auto-padre.
  - No se puede deshabilitar una entidad con dependencias activas (empresa→sucursales, sucursal→departamentos, departamento→puestos/subdepartamentos).
  - Soft delete (`deletedAt`, `enabled=false`).
- APIs paginadas, filtros (`q`, `enabled`, UUID de padre), DTOs y auditoría.
- Permisos `ORG_VIEW` / `ORG_MANAGE`.

### Hotfixes de integridad

- `AnniversaryDays`: corregido delta siempre 0; guarda entitlement anterior y evita saldo negativo. El motor formal de periodos/ledger llega en Fase 5.
- Eliminado `POST /admin/users/vacations/recalc` (sobrescribía el saldo disponible y destruía consumos).
- `VacationController`: bloqueo pesimista del usuario objetivo antes de validar traslape/descontar saldo; se usa `deductVacationDays`; validación de rol del líder y de que no sea el mismo usuario.
- `EmailService`: remitente configurable (`MAIL_FROM`), branding INSSOFT PEOPLE.
- PDF de calendario: logo desde classpath (funciona empaquetado en JAR).
- Calendario: incluye `PENDING_LEADER` además de `APPROVED`/`PENDING_ADMIN`.

---

## 2. Endpoints nuevos

| Método | Ruta | Permiso |
|---|---|---|
| POST | `/api/v1/auth/login` | público |
| GET | `/api/v1/auth/me` | autenticado |
| GET | `/api/v1/masterdata/catalogs` | `MASTERDATA_VIEW` |
| GET | `/api/v1/masterdata/catalogs/{code}/items?includeDisabled=false` | `MASTERDATA_VIEW` |
| GET | `/api/v1/masterdata/items/{uuid}` | `MASTERDATA_VIEW` |
| POST | `/api/v1/masterdata/catalogs/{code}/items` | `MASTERDATA_MANAGE` |
| PUT | `/api/v1/masterdata/items/{uuid}` | `MASTERDATA_MANAGE` |
| DELETE | `/api/v1/masterdata/items/{uuid}` | `MASTERDATA_MANAGE` |
| GET | `/api/v1/companies?q=&enabled=&page=&size=` | `ORG_VIEW` |
| GET | `/api/v1/companies/{uuid}` | `ORG_VIEW` |
| POST | `/api/v1/companies` | `ORG_MANAGE` |
| PUT | `/api/v1/companies/{uuid}` | `ORG_MANAGE` |
| DELETE | `/api/v1/companies/{uuid}` | `ORG_MANAGE` |
| GET | `/api/v1/branches?companyUuid=&q=&enabled=&page=&size=` | `ORG_VIEW` |
| GET | `/api/v1/branches/{uuid}` | `ORG_VIEW` |
| POST | `/api/v1/branches` | `ORG_MANAGE` |
| PUT | `/api/v1/branches/{uuid}` | `ORG_MANAGE` |
| DELETE | `/api/v1/branches/{uuid}` | `ORG_MANAGE` |
| GET | `/api/v1/departments?branchUuid=&q=&enabled=&page=&size=` | `ORG_VIEW` |
| GET | `/api/v1/departments/{uuid}` | `ORG_VIEW` |
| POST | `/api/v1/departments` | `ORG_MANAGE` |
| PUT | `/api/v1/departments/{uuid}` | `ORG_MANAGE` |
| DELETE | `/api/v1/departments/{uuid}` | `ORG_MANAGE` |
| GET | `/api/v1/positions?departmentUuid=&q=&enabled=&page=&size=` | `ORG_VIEW` |
| GET | `/api/v1/positions/{uuid}` | `ORG_VIEW` |
| POST | `/api/v1/positions` | `ORG_MANAGE` |
| PUT | `/api/v1/positions/{uuid}` | `ORG_MANAGE` |
| DELETE | `/api/v1/positions/{uuid}` | `ORG_MANAGE` |

Documentación interactiva: `/swagger-ui.html` (requiere sesión web) y `/v3/api-docs`.

---

## 3. Cómo validar

### 3.1 Build y pruebas

```bash
mvn clean verify
```

Resultado esperado: `BUILD SUCCESS`, 66 tests, reporte JaCoCo en `target/site/jacoco/index.html`.

### 3.2 Migraciones en una copia

1. Restaurar una copia de la base en un servidor de pruebas.
2. Ejecutar `docs/sql/00_introspection_sqlserver.sql`.
3. Comparar el esquema real con `V1__baseline_legacy.sql`.
4. Ajustar `V1` si difiere (o confirmar que Flyway hará baseline y no lo ejecutará).
5. Configurar `DB_*` y `JWT_SECRET`; arrancar la aplicación.
6. Verificar en SQL Server:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT COUNT(*) FROM sec_role;
SELECT COUNT(*) FROM sec_permission;
SELECT COUNT(*) FROM md_catalog;
SELECT COUNT(*) FROM md_catalog_item;
```

7. Si la base ya existía, confirmar que `users.uuid` es único:

```sql
SELECT COUNT(*) AS total, COUNT(DISTINCT uuid) AS distintos FROM dbo.users;
```

### 3.3 API

```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:8443/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"TU_PASSWORD"}' | jq -r '.data.accessToken')

# Identidad
curl -s http://localhost:8443/api/v1/auth/me -H "Authorization: Bearer $TOKEN"

# Catálogos
curl -s http://localhost:8443/api/v1/masterdata/catalogs -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8443/api/v1/masterdata/catalogs/GENDER/items -H "Authorization: Bearer $TOKEN"

# Organización
curl -s -X POST http://localhost:8443/api/v1/companies \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"code":"INSSOFT","legalName":"INSSOFT SA de CV","timezone":"America/Mexico_City","enabled":true}'

curl -s "http://localhost:8443/api/v1/companies?page=0&size=20" -H "Authorization: Bearer $TOKEN"
```

### 3.4 Web legado

1. Abrir `http://localhost:8443/login`.
2. Iniciar sesión; verificar que el formulario envía `_csrf` (ver código fuente).
3. Solicitar vacaciones, aprobar como líder y rechazar como admin.
4. Verificar que al rechazar se libera el saldo (`users.vacation_days_available`).
5. Verificar que un líder **no** puede aprobar solicitudes de otro líder (403) ni acceder a `/admin/**`.

---

## 4. Pendientes y límites conocidos

| Tema | Estado | Fase |
|---|---|---|
| `V1__baseline_legacy.sql` es inferido de JPA | Requiere validación contra copia real antes de prod | 0.5/1 |
| RBAC en BD (`sec_*`) creado, pero `AccessGuard` usa mapa estático | Cambiar a consulta de permisos por usuario | 4 |
| Ledger de vacaciones / periodos | No implementado; se corrigió el balance legado | 5 |
| Job de aniversario | Hotfix; motor formal en Fase 5 | 5 |
| Correos síncronos dentro de transacción | Outbox/async pendiente | 9 |
| Frontend Next.js | Thymeleaf se mantiene como cliente legado | 5+ |
| HTTPS/keystore | `keystore.p12` eliminado; configurar TLS en despliegue | Operación |
| Secretos en historial de git | Rotar credenciales y evaluar purga del historial | 0.5 |
| Pruebas de integración con SQL Server | No hay Docker/Testcontainers en el entorno actual | 4+ |
| `ddl-auto=validate` en prod | Depende de validar el esquema real | 0.5/1 |
| Rate limiting / lockout por intentos | No implementado | 14 |

---

## 5. Siguiente bloque recomendado

**Fase 4 — Employees** (`emp_employee`, contactos, `/api/v1/employees/me`, migración desde `users`, validaciones CURP/RFC/NSS), precedido por:

1. Validar `V1` contra la copia real.
2. Ejecutar la introspección y reconciliar `users` → `employees`.
3. Implementar `V5__employees.sql`.
4. Migrar la autenticación a permisos de BD (`sec_role_permission`) y retirar el mapa estático de `AccessGuard`.

Antes de producción: rotar credenciales, revisar el checklist de seguridad de la auditoría y ejecutar las migraciones en ventana controlada con respaldo verificado.
