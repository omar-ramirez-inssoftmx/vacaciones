# INSSOFT PEOPLE

Plataforma interna de administración de empleados. Evoluciona el sistema de vacaciones existente hacia un **modular monolith API-first** sobre **Java 17 + Spring Boot 3.5 + SQL Server**.

> Documentación de la evolución: [`docs/AUDITORIA-TECNICA-INSSOFT-PEOPLE.md`](docs/AUDITORIA-TECNICA-INSSOFT-PEOPLE.md)
> Estado del Bloque 1: [`docs/BLOQUE-1-IMPLEMENTACION.md`](docs/BLOQUE-1-IMPLEMENTACION.md)

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

Endpoints del Bloque 1:

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
```

> **Importante:** `V1` fue inferido del mapeo JPA. Antes de producción, ejecutar `docs/sql/00_introspection_sqlserver.sql` sobre una copia y reemplazar `V1` por el DDL real (o confirmar que coincide). En bases existentes Flyway hace baseline en V1 y solo aplica V2–V4.

## Pruebas

```bash
mvn clean verify
```

- 66 pruebas unitarias/de contrato.
- Cobertura de código nuevo: **79.0%** (ver `target/site/jacoco/index.html`).
- No requieren base de datos.

## Seguridad

- Secretos por variables de entorno.
- Web: sesión + CSRF (cookie `XSRF-TOKEN`; el JS agrega `X-XSRF-TOKEN`).
- API: JWT stateless, CSRF no aplica.
- Autorización backend con `@PreAuthorize` y `AccessGuard`.
- Correcciones aplicadas: IDOR líder/admin, rechazo de líder, saldo liberado al rechazar, `GET` sin efectos secundarios, cambio de contraseña con contraseña actual.
- **Rotar** cualquier credencial que haya estado en el historial de git (ver auditoría).

## Ramas

- `develop` — integración
- `feature/*` — desarrollo
- `bugfix/*` — correcciones
- `release/*` — preparación

Commits convencionales: `feat(organization): ...`, `fix(auth): ...`, `test(...)`, `docs(...)`.
