# INSSOFT PEOPLE

Plataforma interna de administración de empleados. Evoluciona el sistema de vacaciones existente hacia un **modular monolith API-first** sobre **Java 17 + Spring Boot 3.5 + SQL Server**.

> Documentación de la evolución: [`docs/AUDITORIA-TECNICA-INSSOFT-PEOPLE.md`](docs/AUDITORIA-TECNICA-INSSOFT-PEOPLE.md)
> Estado del Bloque 1: [`docs/BLOQUE-1-IMPLEMENTACION.md`](docs/BLOQUE-1-IMPLEMENTACION.md)
> Estado del Bloque 2 (Employees + RBAC): [`docs/BLOQUE-2-EMPLEADOS.md`](docs/BLOQUE-2-EMPLEADOS.md)
> Estado del Bloque 3 (Vacations + ledger): [`docs/BLOQUE-3-VACACIONES.md`](docs/BLOQUE-3-VACACIONES.md)
> Estado del Bloque 4 (Calendar): [`docs/BLOQUE-4-CALENDARIO.md`](docs/BLOQUE-4-CALENDARIO.md)
> Estado del Bloque 5 (Documents): [`docs/BLOQUE-5-DOCUMENTOS.md`](docs/BLOQUE-5-DOCUMENTOS.md)
> Guía para levantar en Eclipse: [`docs/GUIA-ECLIPSE.md`](docs/GUIA-ECLIPSE.md)

## Stack

- Java 17, Maven
- Spring Boot 3.5.x
- Spring Security (sesión para Thymeleaf + JWT para `/api/v1`)
- Spring Data JPA / Hibernate
- SQL Server
- Flyway (migraciones)
- Springdoc OpenAPI
- JUnit 5 + Mockito + JaCoCo

## Estructura

```
com.mx.vacaciones        # sistema legado Thymeleaf (se mantiene durante la transición)
com.inssoft.people       # nuevos módulos API-first
  common/                # ApiResponse, errores, auditoría, seguridad, utilidades
  auth/                  # login JWT y /me
  masterdata/            # catálogos reutilizables
  organization/          # Company → Branch → Department → Position
  employee/              # empleados, /me, contactos de emergencia
  vacation/              # políticas, periodos, ledger, workflow y job de acumulación
  calendar/              # calendario laboral, festivos, cierres y eventos
  documents/             # tipos, metadatos, almacenamiento y descarga autorizada
  ...
src/main/resources/db/migration   # Flyway (SQL Server)
```

## Requisitos

- Java 17+
- Maven 3.9+
- SQL Server accesible
- Variables de entorno (nunca secretos en el repositorio)

## Configuración local

1. Copiar el ejemplo:

```bash
cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
```

2. Completar `application-local.properties` (está en `.gitignore`):

```properties
DB_URL=jdbc:sqlserver://localhost:1433;databaseName=VACACIONES_DEV;encrypt=true;trustServerCertificate=true
DB_USERNAME=...
DB_PASSWORD=...
JWT_SECRET=...mínimo 32 caracteres...
MAIL_USERNAME=...
MAIL_PASSWORD=...
```

3. Ejecutar:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway aplica automáticamente las migraciones. `spring.jpa.hibernate.ddl-auto` está en `none` (la base solo la modifica Flyway).

## Variables de entorno principales

| Variable | Uso |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | SQL Server |
| `JWT_SECRET` | Firma HS256 (mín. 32 caracteres) |
| `JWT_ISSUER`, `JWT_EXPIRATION_MINUTES` | Emisor y vigencia del token |
| `APP_SYSTEM_URL` | URL usada en correos |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos para la API (coma-separados) |
| `LOG_FILE` | Ruta del log |
| `OPENAPI_ENABLED` | (opcional) habilitar/deshabilitar Swagger |

## API v1

Autenticación:

```bash
curl -X POST http://localhost:8443/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"..."}'
```

Respuesta:

```json
{
  "success": true,
  "message": "Autenticación correcta",
  "data": {
    "accessToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "username": "admin",
    "authorities": ["ROLE_ADMIN"]
  }
}
```

Usar el token:

```bash
curl http://localhost:8443/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

Endpoints de los Bloques 1–5:

| Método | Ruta | Permiso |
|---|---|---|
| POST | `/api/v1/auth/login` | público |
| GET | `/api/v1/auth/me` | autenticado |
| GET | `/api/v1/masterdata/catalogs` | `MASTERDATA_VIEW` |
| GET | `/api/v1/masterdata/catalogs/{code}/items` | `MASTERDATA_VIEW` |
| GET | `/api/v1/masterdata/items/{uuid}` | `MASTERDATA_VIEW` |
| POST | `/api/v1/masterdata/catalogs/{code}/items` | `MASTERDATA_MANAGE` |
| PUT | `/api/v1/masterdata/items/{uuid}` | `MASTERDATA_MANAGE` |
| DELETE | `/api/v1/masterdata/items/{uuid}` | `MASTERDATA_MANAGE` |
| GET/POST | `/api/v1/companies` | `ORG_VIEW` / `ORG_MANAGE` |
| GET/PUT/DELETE | `/api/v1/companies/{uuid}` | `ORG_VIEW` / `ORG_MANAGE` |
| GET/POST | `/api/v1/branches?companyUuid=...` | `ORG_VIEW` / `ORG_MANAGE` |
| GET/POST | `/api/v1/departments?branchUuid=...` | `ORG_VIEW` / `ORG_MANAGE` |
| GET/POST | `/api/v1/positions?departmentUuid=...` | `ORG_VIEW` / `ORG_MANAGE` |
| GET | `/api/v1/employees` | `EMPLOYEE_VIEW` |
| GET | `/api/v1/employees/me` | `EMPLOYEE_VIEW_SELF` |
| GET | `/api/v1/employees/me/emergency-contacts` | `EMPLOYEE_VIEW_SELF` |
| GET | `/api/v1/employees/{uuid}` | `EMPLOYEE_VIEW` |
| POST | `/api/v1/employees` | `EMPLOYEE_CREATE` |
| PUT | `/api/v1/employees/{uuid}` | `EMPLOYEE_UPDATE` |
| POST | `/api/v1/employees/{uuid}/terminate` | `EMPLOYEE_TERMINATE` |
| DELETE | `/api/v1/employees/{uuid}` | `EMPLOYEE_TERMINATE` |
| GET/POST | `/api/v1/employees/{uuid}/emergency-contacts` | `EMPLOYEE_VIEW` / `EMPLOYEE_UPDATE` |
| PUT/DELETE | `/api/v1/employees/{uuid}/emergency-contacts/{contactUuid}` | `EMPLOYEE_UPDATE` |
| GET | `/api/v1/vacations/balance` | `VACATION_VIEW_SELF` |
| GET | `/api/v1/vacations/periods` | `VACATION_VIEW_SELF` |
| GET | `/api/v1/vacations/ledger` | `VACATION_VIEW_SELF` |
| GET/POST | `/api/v1/vacations` | `VACATION_VIEW_SELF` / `VACATION_CREATE` |
| GET | `/api/v1/vacations/{uuid}` | `VACATION_VIEW` / `VACATION_VIEW_SELF` |
| GET | `/api/v1/vacations/{uuid}/events` | `VACATION_VIEW` / `VACATION_VIEW_SELF` |
| POST | `/api/v1/vacations/{uuid}/approve` | `VACATION_APPROVE` (+ `VACATION_ADJUST` en etapa admin) |
| POST | `/api/v1/vacations/{uuid}/reject` | `VACATION_APPROVE` (+ `VACATION_ADJUST` en etapa admin) |
| POST | `/api/v1/vacations/{uuid}/cancel` | `VACATION_CANCEL` |
| GET | `/api/v1/vacations/pending` | `VACATION_APPROVE` |
| GET | `/api/v1/vacations/search` | `VACATION_ADJUST` |
| GET | `/api/v1/vacations/employees/{uuid}/balance` | `VACATION_ADJUST` |
| POST | `/api/v1/vacations/employees/{uuid}/adjust` | `VACATION_ADJUST` |
| GET | `/api/v1/vacations/policies` | `VACATION_POLICY_MANAGE` / `VACATION_VIEW` |
| GET | `/api/v1/calendar/days` | `CALENDAR_VIEW` |
| GET | `/api/v1/calendar/events` | `CALENDAR_VIEW` |
| GET | `/api/v1/calendar/holidays` | `CALENDAR_VIEW` |
| GET | `/api/v1/calendar/special-days` | `CALENDAR_VIEW` |
| GET/PUT | `/api/v1/calendar/config` | `CALENDAR_VIEW` / `CALENDAR_MANAGE` |
| POST/PUT/DELETE | `/api/v1/calendar/holidays` | `CALENDAR_MANAGE` |
| POST/PUT/DELETE | `/api/v1/calendar/special-days` | `CALENDAR_MANAGE` |
| POST | `/api/v1/calendar/official-holidays/{year}` | `CALENDAR_MANAGE` |
| GET | `/api/v1/documents/types` | `DOCUMENT_VIEW` / `DOCUMENT_UPLOAD` / `DOCUMENT_VIEW_SELF` |
| GET | `/api/v1/documents/me` | `DOCUMENT_VIEW_SELF` |
| GET | `/api/v1/documents` | `DOCUMENT_VIEW` |
| GET | `/api/v1/documents/{uuid}` | `DOCUMENT_VIEW` / `DOCUMENT_VIEW_SELF` |
| GET | `/api/v1/documents/{uuid}/download` | `DOCUMENT_VIEW` / `DOCUMENT_VIEW_SELF` |
| POST | `/api/v1/documents` | `DOCUMENT_UPLOAD` |
| DELETE | `/api/v1/documents/{uuid}` | `DOCUMENT_DELETE` |

Convenciones:

- Prefijo `/api/v1`.
- Respuesta `{ success, message, data }`; errores con `errors[]`.
- Paginación `?page=0&size=20` (máx. 100).
- UUID en rutas externas.
- JWT `Authorization: Bearer`.

Swagger UI: `http://localhost:8443/swagger-ui.html` (requiere sesión web).

## Migraciones

```
V1__baseline_legacy.sql      # esquema legado inferido (reemplazar por DDL real antes de prod)
V2__common_security_audit.sql
V3__organization.sql
V4__masterdata.sql
V5__employees.sql          # empleados, contactos, migración users → employees
V6__vacations.sql          # políticas, periodos, ledger, eventos y workflow
V7__calendar.sql           # calendario, festivos oficiales/empresa y días especiales
V8__documents.sql          # tipos de documento, contenido y metadatos
```

> **Importante:** `V1` fue inferido del mapeo JPA. Antes de producción, ejecutar `docs/sql/00_introspection_sqlserver.sql` sobre una copia y reemplazar `V1` por el DDL real (o confirmar que coincide). En bases existentes Flyway hace baseline en V1 y aplica V2–V8.
>
> `V5` crea una organización mínima (`INSSOFT / MATRIZ / GENERAL / GENERAL`) y migra los usuarios existentes a empleados con número `LEG-<id>` y nombre dividido de forma aproximada. RRHH debe revisar/corregir esos datos.

## Pruebas

```bash
mvn clean verify
```

- 184 pruebas unitarias/de contrato.
- Cobertura de código nuevo: **75.8%** (ver `target/site/jacoco/index.html`).
- No requieren base de datos.

## Seguridad

- Secretos por variables de entorno.
- Web: sesión + CSRF (cookie `XSRF-TOKEN`; el JS agrega `X-XSRF-TOKEN`).
- API: JWT stateless, CSRF no aplica.
- Autorización backend con `@PreAuthorize` y `AccessGuard`.
- RBAC efectivo desde base de datos (`sec_role_permission`); el mapa estático queda solo como respaldo pre-migración.
- Datos sensibles (CURP/RFC/NSS) enmascarados en detalle si el usuario no tiene `EMPLOYEE_VIEW_SENSITIVE`.
- Vacaciones: ledger firmado, periodos por aniversario, retención/consumo/liberación y workflow con validación de etapa administrativa (`VACATION_ADJUST`).
- Calendario: días hábiles por empresa, festivos oficiales/empresa, cierres y eventos; Vacations ya usa el calendario para calcular días hábiles.
- Documentos: metadatos + contenido con abstracción de almacenamiento, descarga autorizada, auditoría y MIME/tamaño validados; líderes sin acceso a documentos de empleados.
- Auto-aprobación/rechazo administrativo bloqueado.
- Correcciones aplicadas: IDOR líder/admin, rechazo de líder, saldo liberado al rechazar, `GET` sin efectos secundarios, cambio de contraseña con contraseña actual.
- **Rotar** cualquier credencial que haya estado en el historial de git (ver auditoría).

## Ramas

- `develop` — integración
- `feature/*` — desarrollo
- `bugfix/*` — correcciones
- `release/*` — preparación

Commits convencionales: `feat(organization): ...`, `fix(auth): ...`, `test(...)`, `docs(...)`.
