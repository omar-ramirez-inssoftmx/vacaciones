# Auditoría Técnica — Evolución de `vacaciones` a INSSOFT PEOPLE

| Campo | Valor |
|---|---|
| Repositorio | `https://github.com/omar-ramirez-inssoftmx/vacaciones.git` |
| Commit auditado | `86f4fa9` (main) |
| Fecha de auditoría | 2026-06-10 |
| Alcance | Backend, base de datos, seguridad, frontend Thymeleaf, configuración, operación |
| Método | Revisión estática del código, configuración y plantillas. **Sin acceso a la base productiva** |
| Regla aplicada | Fase 0: **no se modificó código de aplicación**. Solo se agregan documentos y scripts de solo lectura |
| Autor | Tech Lead / Arquitecto de Software |

> **Limitación importante:** el esquema de base de datos de este documento fue **inferido** de las entidades JPA y de la configuración (`ddl-auto=update`). No se ejecutó nada contra la base. Antes de crear la migración baseline (`V1`) se debe ejecutar `docs/sql/00_introspection_sqlserver.sql` en una copia de la base y comparar el resultado con este análisis.

---

## 0. Resumen ejecutivo

El sistema actual es un **prototipo funcional** de gestión de vacaciones construido como monolito MVC con Thymeleaf, sesión HTTP, JPA/Hibernate y SQL Server. Cumple el flujo básico (solicitar, aprobar por líder, aprobar/rechazar por admin, calendario, correos, políticas), pero **no tiene la fundación técnica necesaria para convertirse en una plataforma de RRHH** sin una refactorización previa.

**Veredicto:** no se debe empezar agregando módulos (empleados, nómina, teletrabajo) sobre la base actual. Primero hay que estabilizar fundación, seguridad e integridad de datos. La buena noticia: el tamaño es manejable (**31 archivos Java, ~3,778 líneas; 14 plantillas, ~5,696 líneas**), por lo que la modernización es viable sin reescribir todo desde cero.

### 0.1 Hallazgos críticos (prioridad inmediata)

| # | Hallazgo | Severidad | Evidencia |
|---|---|---|---|
| 1 | Credenciales de base de datos, correo y rutas internas **commiteadas en texto plano** | 🔴 Crítica | `application.properties`, `application-prod.properties`, `keystore.p12` |
| 2 | **IDOR / bypass de autorización**: cualquier `ROLE_LIDER` puede aprobar/rechazar cualquier solicitud por ID, y además puede usar los endpoints de admin | 🔴 Crítica | `SecurityConfig.java:89-103`, `LeaderController.java:51-79`, `AdminVacationController.java:133-186` |
| 3 | **CSRF deshabilitado globalmente** con autenticación por sesión y cookies | 🔴 Crítica | `SecurityConfig.java:69` |
| 4 | `spring.jpa.hibernate.ddl-auto=update` en todos los entornos (incluido prod) | 🔴 Crítica | `application.properties`, `application-prod.properties` |
| 5 | Saldo de vacaciones sin ledger, descontado al **crear** la solicitud, **nunca repuesto** al rechazar y con condición de carrera | 🔴 Crítica | `VacationController.java:328`, `User.java:113-122` |
| 6 | `GET /admin/requests` **modifica datos** (convierte `PENDING_LEADER` sin líder a `PENDING_ADMIN`) | 🔴 Alta | `AdminVacationController.java:102-111` |
| 7 | Job de aniversario **no hace nada**: calcula un delta que siempre es 0 | 🔴 Alta | `AnniversaryDays.java:43-47` |
| 8 | `recalcAllUsers` **destruye el saldo real**: asigna disponible = días correspondientes, ignorando consumos | 🔴 Alta | `AdminUserController.java:131-132` |
| 9 | Rechazo del líder deja la solicitud en `PENDING_ADMIN` (puede terminar aprobada) | 🔴 Alta | `LeaderController.java:74` |
| 10 | Sin API REST, sin DTO, sin OpenAPI, sin tests, sin auditoría, sin migraciones | 🔴 Alta (estratégica) | Todo el repositorio |
| 11 | `data.sql` obsoleto (columnas inexistentes, passwords `{noop}`) | 🟠 Media | `src/main/resources/data.sql` |
| 12 | Días festivos de México **hardcodeados**; no hay festivos de empresa | 🟠 Media | `VacationCalculator.java:70-120` |
| 13 | Correo acoplado al flujo de negocio, síncrono, con HTML inline y excepciones no controladas | 🟠 Media | `EmailService.java` (679 líneas), `VacationController.java:360-394` |
| 14 | Código muerto y duplicado: `Vacacion`, `VacationHistory`, `VacationService.createRequest`, plantilla `admin/request-vacation.html` | 🟠 Media | Ver sección B |
| 15 | Sin índices en columnas FK ni en filtros (`status`, `start_date`, `end_date`, `username`) | 🟠 Media | Inferido del mapeo JPA |

### 0.2 Decisiones de arquitectura tomadas (ADR-lite)

| # | Decisión | Justificación |
|---|---|---|
| D1 | **Modular Monolith** por dominio, no microservicios | Volumen y equipo actual no justifican microservicios; se preserva la posibilidad de extraer módulos |
| D2 | **API First** en `/api/v1`, con Thymeleaf como cliente temporal (patrón strangler) | Permite web, móvil e integraciones sin reescribir todo de golpe |
| D3 | **SQL Server se mantiene** | Requisito explícito; no hay justificación para cambiar |
| D4 | **Flyway** como único mecanismo de evolución de esquema; `ddl-auto=validate` | Elimina el riesgo de `update` en producción y permite rollback documentado |
| D5 | Separar **`users` (identidad) de `employees` (persona)** | Requisito de negocio y base para SSO/Google Workspace |
| D6 | Saldo de vacaciones como **ledger de movimientos** + periodos, no como columna mutable | Integridad, auditoría, repetición de cálculos, cancelaciones y reclamos |
| D7 | Retención de saldo al **enviar** solicitud, consumo al **aprobar**, liberación al **rechazar/cancelar** | Evita sobreventa de días y corrige el defecto actual |
| D8 | **No** implementar JWT/SSO en la Fase 1; sí dejar la abstracción y dos cadenas de seguridad | Evita romper el login web actual; móvil se habilita en Fase 14 |
| D9 | Catálogos: tabla genérica `md_catalog`/`md_catalog_item` para listas simples; tablas dedicadas para tipos con comportamiento | Cumple “no una tabla por catálogo” sin sacrificar integridad referencial |
| D10 | Archivos: abstracción `FileStorageService`; primera implementación en SQL Server, luego Azure Blob/S3 | Evita acoplar documentos a `VARBINARY(MAX)` para siempre |
| D11 | Notificaciones: abstracción con providers (web/email/push) + outbox; correo fuera de la transacción | Evita perder notificaciones y bloqueos por SMTP |
| D12 | Multi-empresa lógica (single DB, `company_id` en entidades de negocio, filtrado en servicio) | La jerarquía Company→Branch→Department→Position lo exige; multi-tenant físico no es necesario aún |
| D13 | Spring Boot se actualiza a la línea **3.5.x** (Java 17 se mantiene) | 3.2.1 está fuera de soporte y acumula CVEs; 3.5.x es la línea estable compatible |
| D14 | Migración de datos de vacaciones: **no recalcular historial**; saldo actual se convierte en `OPENING_BALANCE` | Regla 50: no romper ni reinterpretar datos históricos |

### 0.3 Estado general por dimensión

| Dimensión | Nivel | Comentario |
|---|---|---|
| Funcionalidad | 🟡 | Flujo principal existe; casos borde y consistencia deficientes |
| Seguridad | 🔴 | Secretos expuestos, CSRF off, IDOR, sin auditoría |
| Integridad de datos | 🔴 | Saldo mutable, sin ledger, sin constraints, `ddl-auto=update` |
| Mantenibilidad | 🟠 | Lógica en controladores, duplicación, código muerto |
| API / Mobile readiness | ⚪ | No existe API |
| Pruebas | ⚪ | 0 tests |
| Operación | 🟠 | Rutas Windows hardcodeadas, logs sin estructura, sin health checks |
| Documentación | 🟠 | README de 2 líneas; sin ADRs ni contratos |

**Esfuerzo estimado del backend núcleo (1 desarrollador senior + QA parcial):** 6 a 9 meses para las fases 1–13; 2 a 4 semanas adicionales para Fase 14 y 3 a 5 para Fase 15 (SSO). Las fases 1–6 son la base crítica (≈3–4 meses).

---

## A. Arquitectura actual

### A.1 Stack

| Capa | Tecnología | Observaciones |
|---|---|---|
| Lenguaje | Java 17 | Correcto para Spring Boot 3.x |
| Framework | Spring Boot 3.2.1 | Versión de dic-2023, fuera de soporte OSS |
| Web | Spring MVC + Thymeleaf | Renderizado en servidor; sin API |
| Seguridad | Spring Security (form login, sesión, BCrypt) | CSRF deshabilitado; autorización por URL incompleta |
| Persistencia | Spring Data JPA / Hibernate | `ddl-auto=update`; entidades expuestas a vistas |
| Base de datos | SQL Server (driver `mssql-jdbc 12.6.1.jre11`) | `encrypt=false`; credenciales en properties |
| Correo | Spring Mail (Gmail/Brevo) | Envío síncrono desde controladores |
| PDF | OpenPDF 1.3.35 | Exportación de calendario |
| Utilidades | Lombok | Uso parcial |
| Build | Maven | Sin wrapper, sin CI, sin perfiles Maven |
| Pruebas | — | No hay `src/test` |

### A.2 Estructura real

```
com.mx.vacaciones
├── VacacionesApplication.java      # @EnableScheduling; crea C:/logs
├── config/SecurityConfig.java
├── security/CustomAuthenticationSuccessHandler.java
├── controller/                     # 11 controladores (MVC)
├── model/                          # 5 entidades JPA
├── repository/                     # 3 repositorios
├── service/                        # 6 servicios
└── job/AnniversaryDays.java
```

**No hay separación por dominio.** Es una arquitectura en capas plana, y las capas se saltan: la mayoría de los controladores usan repositorios directamente y contienen la lógica de negocio. La capa `service` existe pero está mayormente eludida (`VacationService.createRequest` no se invoca desde ningún controlador).

### A.3 Flujo actual de una solicitud de vacaciones

```
[Colaborador]
GET  /vacations/request        -> carga festivos MX, líderes, (admin: todos los usuarios)
POST /vacations/request        -> valida fechas, traslape, saldo
                               -> descuenta saldo del usuario (userRepository.save)
                               -> crea VacationRequest (PENDING_LEADER o PENDING_ADMIN)
                               -> envía 2-4 correos en la misma petición HTTP
[Líder]
GET  /leader/requests          -> solicitudes PENDING_LEADER asignadas
POST /leader/approve/{id}      -> PENDING_ADMIN
POST /leader/reject/{id}       -> PENDING_ADMIN  (¡no rechaza!)
[Admin]
GET  /admin/requests           -> lista PENDING_LEADER/PENDING_ADMIN
                               -> ¡convierte en GET los PENDING_LEADER sin líder!
POST /admin/requests/{id}/approve -> APPROVED + correo (no repone nada)
POST /admin/requests/{id}/reject  -> REJECTED + correo (no repone saldo)
```

### A.4 Observaciones arquitectónicas

1. **Lógica de negocio en controladores.** `VacationController.submitVacationRequest` (~200 líneas) y `AdminVacationController` concentran validaciones, persistencia, cálculo y notificación. Esto impide reutilizar reglas desde una API o desde móvil.
2. **Entidades JPA como modelo de vista.** Las plantillas Thymeleaf acceden a entidades (`VacationRequest`, `User`, `Politica`). No existe DTO ni contrato estable.
3. **Dos caminos para crear solicitudes** (colaborador/admin) con reglas duplicadas y divergentas.
4. **Estado del flujo como `String` libre** (`PENDING`, `PENDING_LEADER`, `PENDING_ADMIN`, `APPROVED`, `REJECTED`). No hay enum, máquina de estados ni constraints.
5. **Saldo como columna mutable** en `users`, no como historial de movimientos.
6. **Notificaciones dentro del request HTTP**, síncronas, con `try/catch` que solo imprime en consola.
7. **Dependencias de infraestructura dentro del dominio:** `EmailService` conoce entidades, roles y URLs; el controlador arma correos.
8. **Configuración por archivos con secretos**, no por variables de entorno.
9. **Despliegue:** puerto 8443 sin configuración SSL en properties (el `keystore.p12` no se referencia); `app.system.url` apunta a una IP HTTP interna; ruta de logs `C:\logs`; dependencia de `src/main/resources/static/images/logo.png` en tiempo de ejecución (falla en JAR empaquetado).
10. **Ramas:** `main`, `dev_omar`, `dev_juan`, `dev_octavio`, `dev_jesus`, `main2`. No existe `develop`, `release/*` ni políticas de PR. El historial contiene un experimento abandonado de React/Vite + API Node + **migración a PostgreSQL/Supabase** que no está en `main`; no debe considerarse parte del sistema.

### A.5 Deuda técnica estructural

| Deuda | Impacto |
|---|---|
| Sin migraciones de BD | Cambios no reproducibles, riesgo de pérdida de datos |
| Sin DTO/mappers | Acoplamiento vista-entidad; difícil API/móvil |
| Sin validación Bean Validation | Reglas dispersas, mensajes inconsistentes |
| Sin manejo global de errores | Respuestas heterogéneas, stack traces y `e.getMessage()` en URLs |
| Sin tests | Refactor sin red de seguridad |
| Sin auditoría | No se puede responder “quién aprobó/cambió qué” |
| Sin paginación | `findAll()` en usuarios y calendario; degradación con volumen |
| Sin índices | Consultas por estado/fechas/usuario hacen scan |
| Sin observabilidad | Sin Actuator, métricas, correlation-id, logs estructurados |
| Código muerto | Confusión y mantenimiento innecesario |

---

## B. Módulos existentes

### B.1 Módulos funcionales

| Módulo | Archivos principales | Estado | Observaciones |
|---|---|---|---|
| Autenticación web | `SecurityConfig`, `CustomUserDetailsService`, `CustomAuthenticationSuccessHandler`, `login.html`, `PasswordController` | 🟡 Funcional con riesgos | Form login + sesión + BCrypt; cambio obligatorio de contraseña temporal; **CSRF off**; sin bloqueo por intentos; sin auditoría; cambio de contraseña sin validar la actual |
| Gestión de usuarios | `AdminUserController`, `UserService`, `admin/users.html` | 🟡 CRUD básico | Sin paginación/filtros; rol único; sin UUID; email único nullable; sin borrado lógico; recálculo destructivo |
| Roles y autorización | `Role` (enum), `SecurityConfig` | 🔴 Insuficiente | Solo `ROLE_ADMIN`, `ROLE_COLABORADOR`, `ROLE_LIDER`; sin permisos; reglas por URL incompletas y con bypass |
| Solicitudes de vacaciones | `VacationController`, `VacationRequest`, `VacationRepository`, `vacations/request.html`, `vacations/status.html` | 🟡 Funcional con deuda alta | Saldo descontado al crear; sin reposición; sin lock; sin cancelación; estados string; lógica en controlador |
| Aprobación por líder | `LeaderController`, `leader/requests.html` | 🔴 Inseguro | No valida que la solicitud pertenezca al líder (IDOR); “rechazar” deja `PENDING_ADMIN`; sin `@Transactional`; endpoints JSON |
| Aprobación administrativa | `AdminVacationController`, `admin/requests.html` | 🔴 Inseguro | GET con efectos secundarios; sin máquina de estados; líderes pueden usar `/admin/requests/**`; sin auditoría |
| Historial | `AdminVacationController.history`, `findHistoryFiltered` | 🟡 Consulta | Sin paginación; sin exportación; no usa `VacationHistory` |
| Calendario | `AdminVacationController.calendar*`, `admin/calendar.html` | 🟡 Parcial | FullCalendar; solo `APPROVED`/`PENDING_ADMIN`; sin `PENDING_LEADER`; festivos hardcodeados; PDF con ruta local |
| Dashboard | `AdminDashboardController`, `admin/dashboard.html` | 🟡 Básico | 4 métricas; suma de días no prorrateada; sin filtro por empresa; sin índices |
| Políticas | `PoliticaController`, `ColaboradorPoliticaController`, `PoliticaService`, `tb_politicas_empresa` | 🟡 Duplicado | PDF en BD; dos controladores casi idénticos; borrado físico; sin metadata, validación ni auditoría |
| Correos | `EmailService` | 🔴 Acoplado | 679 líneas; HTML inline; remitente hardcodeado; envío síncrono; sin reintentos/outbox; sin escape de HTML |
| Job de aniversario | `AnniversaryDays`, `VacationPolicyService` | 🔴 Roto | Delta siempre 0; sin historial; no idempotente; sin periodo; sin lock |
| Reglas de política | `VacationPolicyService`, `VacationCalculator` | 🟡 Parcial | Escala LFT correcta (12/14/16/18/20/22…), pero hardcodeada; festivos MX fijos; sin prima vacacional ni caducidad |
| Cambio de contraseña | `PasswordController` | 🟡 Funcional | No solicita contraseña actual; sin política de complejidad |

### B.2 Código muerto o no utilizado

| Elemento | Evidencia | Acción propuesta |
|---|---|---|
| Entidad `Vacacion` / tabla `vacaciones` | Sin referencias en repositorios, servicios ni controladores | Deprecar; no borrar hasta validar datos |
| Entidad `VacationHistory` / tabla `vacation_history` | Nunca se escribe ni se lee | Reemplazar por `vac_ledger` + `vac_request_event` + `aud_audit_log` |
| `VacationService.createRequest` | No se invoca | Eliminar o convertir en el servicio real del nuevo módulo |
| Plantilla `admin/request-vacation.html` | No hay mapping que la renderice | Eliminar tras confirmar |
| `data.sql` | Columnas `dias_disponibles` inexistentes; `{noop}` | Eliminar o reemplazar por seeds Flyway |
| `GET /whoami` | Debug | Eliminar o mover a endpoint de diagnóstico protegido |
| `System.out.println` | Varios controladores/job | Reemplazar por logging estructurado |

---

## C. Base de datos

### C.1 Método y limitación

El esquema se infiere del mapeo JPA (Hibernate `ddl-auto=update`). No hay scripts DDL, migraciones, vistas ni procedimientos en el repositorio. **No se accedió a la base productiva.**

Antes de la Fase 1 se debe:

1. Ejecutar `docs/sql/00_introspection_sqlserver.sql` sobre una **copia** de la base.
2. Generar el DDL real con SSMS → “Generate Scripts” (o `mssql-scripter`).
3. Comparar con la sección C.2 y ajustar la migración `V1__baseline.sql`.
4. Tomar respaldo completo y verificar restauración.

### C.2 Tablas inferidas

#### `users` — identidad y (hoy) empleado + saldo

| Columna | Tipo SQL Server esperado | Nulo | Notas |
|---|---|---|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `name` | `NVARCHAR(255)` | Sí | Nombre completo en un solo campo |
| `username` | `NVARCHAR(255)` | No | Unique |
| `password` | `NVARCHAR(255)` | No | BCrypt |
| `email` | `NVARCHAR(150)` | Sí | Unique (SQL Server solo permite **un** NULL en unique) |
| `vacation_days_available` | `INT` | No | Saldo mutable |
| `role` | `NVARCHAR(255)` | Sí | Enum como string |
| `enabled` | `BIT` | No | |
| `hire_date` | `DATE` | No | |
| `vacation_days_entitled` | `INT` | No | |
| `temporary_password` | `BIT` | No | |

**Problemas:** mezcla identidad + empleado + saldo; sin `uuid`, `created_at`, `updated_at`, `deleted_at`, `version`; sin `employee_number`; sin datos personales/laborales; email unique nullable problemático; saldo sin historial.

#### `vacation_requests` — solicitudes

| Columna | Tipo esperado | Nulo | Notas |
|---|---|---|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `username` | `NVARCHAR(255)` | No | Duplicado denormalizado de `users.username` |
| `start_date` / `end_date` | `DATE` | No | Sin CHECK `end_date >= start_date` |
| `days` | `INT` | No | Sin CHECK `days > 0` |
| `status` | `NVARCHAR(255)` | No | Sin CHECK; valores históricos mixtos |
| `requested_by_username` | `NVARCHAR(255)` | Sí | Quién capturó (admin) |
| `decided_by` | `NVARCHAR(255)` | Sí | Username del admin |
| `decided_at` | `DATETIME2` | Sí | |
| `user_id` | `BIGINT` | No | FK → `users.id` |
| `leader_id` | `BIGINT` | Sí | FK → `users.id` |
| `leader_decision` | `NVARCHAR(255)` | Sí | `APPROVED`/`REJECTED` |
| `leader_decided_at` | `DATETIME2` | Sí | |
| `leader_comment` | `NVARCHAR(255)` | Sí | |
| `created_at` | `DATETIME2` | Sí | Seteado en `@PrePersist` |
| `admin_comment` | `NVARCHAR(255)` | Sí | Motivo de rechazo |

**Problemas:** sin `uuid`, `period_id`, `cancelled_*`, `updated_at`, `deleted_at`, `version`; sin índices; sin FK a usuario decisor; sin historial de eventos; `username` duplicado puede desincronizarse; sin constraint de dominio.

#### `vacation_history` — historial (muerta)

`id`, `fecha`, `accion`, `comentario`, `vacation_id` (FK), `admin_id` (FK). **Nunca se escribe.**

#### `vacaciones` — tabla legada (muerta)

`id`, `usuario`, `fecha_inicio`, `fecha_fin`, `estatus`. **Sin uso; posible residuo de una primera versión.**

#### `tb_politicas_empresa` — políticas PDF

| Columna | Tipo esperado | Notas |
|---|---|---|
| `idpolitica` | `INT IDENTITY` | PK; naming inconsistente |
| `nombre_politica` | `NVARCHAR(255)` | |
| `documento_pdf` | `VARBINARY(MAX)` | `@Lob byte[]`; sin mime, tamaño, hash, autor, fechas |

**Problemas:** binario en BD sin metadata; borrado físico; sin empresa; sin control de acceso por alcance.

### C.3 Relaciones inferidas

```
users 1 ─── N vacation_requests (user_id)
users 1 ─── N vacation_requests (leader_id)
vacation_requests 1 ─── N vacation_history (vacation_id)
users 1 ─── N vacation_history (admin_id)
users 1 ─── 1 vacaciones (por username, sin FK)      [legado]
```

### C.4 Objetos de base de datos ausentes

- Sin procedimientos almacenados, vistas, funciones ni triggers documentados.
- Sin jobs de SQL Server documentados (el único job es el `@Scheduled` de la aplicación).
- Sin tablas de auditoría, catálogos, organización ni empleados.
- Sin `rowversion`/`@Version` para concurrencia optimista.
- Sin índices explícitos en FKs ni en filtros. SQL Server **no** crea índices automáticos en FKs.

### C.5 Riesgos de datos actuales (a verificar con el script)

| Riesgo | Consulta de verificación |
|---|---|
| Solicitudes traslapadas por usuario | `docs/sql/00_introspection_sqlserver.sql` §7 |
| Saldos negativos | §7 |
| `end_date < start_date` | §7 |
| Días declarados distintos a días hábiles | §7 |
| Solicitudes rechazadas sin reposición de saldo | Comparar saldo vs. consumos |
| Emails duplicados o nulos | §7 |
| Usuarios con `hire_date` inválida/futura | §7 |
| Estados desconocidos | §7 |
| Políticas sin PDF o con PDF vacío | §7 |
| Datos en `vacaciones` no migrados | §7 |

### C.6 `data.sql` y arranque

- `data.sql` referencia `dias_disponibles` (columna que ya no existe) y passwords `{noop}`.
- En SQL Server, Spring Boot no ejecuta `data.sql` por defecto (`spring.sql.init.mode=embedded`), por lo que probablemente no corre hoy; si alguien habilita `always`, **falla el arranque**.
- `keystore.p12` está en el repositorio y no se usa en la configuración; no hay SSL configurado.

---

## D. Propuesta INSSOFT PEOPLE

### D.1 Arquitectura objetivo

```
┌──────────────────────┐        ┌──────────────────────┐
│ WEB (Thymeleaf hoy,  │        │ MOBILE (React Native │
│ Next.js después)     │        │ + Expo, futuro)      │
└──────────┬───────────┘        └──────────┬───────────┘
           │                               │
           └──────────────┬────────────────┘
                          │ HTTPS / REST /api/v1
                 ┌────────▼─────────┐
                 │  API GATEWAY     │  (futuro: reverse proxy)
                 └────────┬─────────┘
                          │
        ┌─────────────────▼──────────────────┐
        │  INSSOFT PEOPLE — MODULAR MONOLITH │
        │  auth | employee | organization    │
        │  masterdata | vacation | calendar  │
        │  documents | payroll | comms       │
        │  notifications | incidents         │
        │  telework | workplan | audit       │
        │  dashboard | common                │
        └─────────────────┬──────────────────┘
                          │ JPA / Flyway
                 ┌────────▼─────────┐
                 │   SQL SERVER     │
                 └──────────────────┘
```

### D.2 Estructura de paquetes objetivo

```
com.inssoft.people
├── common
│   ├── api           # ApiResponse, PageResponse, ErrorResponse, ApiError
│   ├── exception     # GlobalExceptionHandler, BusinessException, NotFound...
│   ├── entity        # BaseEntity, AuditableEntity, SoftDeletable
│   ├── security      # CurrentUser, PermissionEvaluator, SecurityConfig
│   ├── storage       # FileStorageService
│   ├── notification  # NotificationService + providers
│   ├── audit         # AuditService, @Audited
│   └── util
├── auth              # login, tokens, password, roles/permissions
├── employee
├── organization
├── masterdata
├── vacation
├── calendar
├── documents
├── payroll
├── communication
├── notification
├── incident
├── telework
├── workplan
└── audit
```

Cada módulo: `controller/`, `service/`, `repository/`, `entity/`, `dto/`, `mapper/`, `validation/`.

### D.3 Reglas de capa (obligatorias)

1. `controller` **solo** recibe/valida DTO y delega. No usa repositorios ni entidades.
2. `service` contiene reglas de negocio y transacciones. Es el único que usa repositorios.
3. `repository` solo persistencia; sin lógica de negocio.
4. `entity` nunca sale por API. Se mapea a DTO (`MapStruct` o mapper manual).
5. Validaciones con Bean Validation + validadores de dominio (`validation/`).
6. Errores con excepciones de dominio y `@RestControllerAdvice`.
7. Notificaciones **después del commit** (eventos/outbox), nunca dentro de la transacción.
8. Autorización con `@PreAuthorize` y permisos, no solo por URL.

### D.4 Convenciones de API

- Prefijo `/api/v1`.
- Respuesta estándar: `{ success, message, data }`; errores: `{ success:false, message, data:null, errors? }`.
- Paginación `?page=0&size=20&sort=createdAt,desc`; respuesta con `content`, `page`, `size`, `totalElements`, `totalPages`.
- Filtros explícitos por recurso; sin `findAll()` sin paginar.
- UUID en recursos externos; `id` numérico solo interno.
- Códigos HTTP correctos (201, 204, 400, 401, 403, 404, 409, 422, 500).
- `409` para conflictos de estado/traslape; `422` para reglas de negocio.
- `Idempotency-Key` en POST críticos (aprobaciones, cargas) en fases posteriores.
- OpenAPI 3 generado y publicado en `/swagger-ui` solo en entornos no productivos (o protegido).

### D.5 Seguridad objetivo

| Tema | Objetivo |
|---|---|
| Web Thymeleaf | Sesión + CSRF habilitado; `SameSite=Lax`; formularios con token |
| API | `/api/v1/**` stateless; JWT + refresh token (Fase 14); hoy puede usar sesión para el cliente web |
| RBAC | `sec_user`, `sec_role`, `sec_permission`, `sec_user_role`, `sec_role_permission`; authorities `PERM_*` |
| Roles iniciales | `EMPLOYEE`, `LEADER`, `HR`, `ADMIN`, `SUPER_ADMIN` |
| Ownership | Todo servicio valida que el recurso pertenece al usuario/empresa; nunca confiar en IDs del cliente |
| Contraseñas | BCrypt; política de longitud/complejidad; expiración de temporales; bloqueo por intentos; cambio con contraseña actual |
| Secretos | Variables de entorno; `application-local.properties` fuera de git; rotación |
| Auditoría | Login/logout, CRUD, aprobaciones, descargas, cambios de roles/permisos |
| Transporte | HTTPS obligatorio; `encrypt=true` en JDBC o VPN/red privada |
| Datos sensibles | CURP/RFC/NSS/bank: cifrado en reposo + enmascarado en API + permisos específicos |
| Archivos | Validación MIME/tamaño, hash, autorización en descarga, sin URLs públicas |

### D.6 Abstracciones clave

```java
// Almacenamiento
public interface FileStorageService {
    StoredFile store(InputStream in, FileMetadata meta);
    InputStream open(String storageKey);
    void delete(String storageKey);
}

// Notificaciones
public interface NotificationProvider {
    NotificationChannel channel();
    void send(NotificationMessage message);
}
// NotificationService orquesta providers + outbox

// Auditoría
@Audited(action = "VACATION_APPROVE", entity = "VacationRequest")
public void approve(UUID requestUuid, ApprovalCommand cmd) { ... }
```

### D.7 Estrategia de transición (strangler)

1. Mantener Thymeleaf funcionando con el esquema actual.
2. Crear `/api/v1` y nuevos módulos **al lado**, reutilizando tablas legadas cuando sea seguro.
3. Migrar primero lecturas (catálogos, organización, empleados, saldo).
4. Migrar escrituras críticas (solicitudes/aprobaciones) cuando exista ledger y máquina de estados.
5. Reemplazar pantallas Thymeleaf por Next.js módulo por módulo.
6. Retirar controladores/plantillas legados solo cuando no tengan tráfico.

---

## E. Mapeo: sistema actual → INSSOFT PEOPLE

| Actual | Nuevo | Acción |
|---|---|---|
| `users` (identidad + empleado + saldo) | `sec_user` + `emp_employee` + `vac_balance/ledger` | Separar; conservar tabla `users` para credenciales y agregar columnas |
| `users.name` | `emp_employee.first_name/middle_name/last_name/second_last_name` | Migrar con parseo asistido; validar manualmente casos ambiguos |
| `users.email` | `sec_user.email` + `emp_employee.corporate_email/personal_email` | Definir cuál es corporativo |
| `users.role` (enum) | `sec_role` + `sec_user_role` + permisos | Mapear `ROLE_ADMIN→ADMIN`, `ROLE_COLABORADOR→EMPLOYEE`, `ROLE_LIDER→LEADER` |
| `users.vacation_days_available` | `vac_period.opening_balance` + `vac_ledger` | Convertir a `OPENING_BALANCE`; no recalcular historial |
| `users.vacation_days_entitled` | `vac_period.entitled_days` + `vac_policy_rule` | Migrar como periodo vigente |
| `User.hireDate` | `emp_employee.hire_date` | Migrar |
| `VacationRequest` | `vac_request` | Mantener ID/datos; agregar UUID, FK de empleado, periodo, versión, soft delete |
| `VacationRequest.status` | `vac_request.status` (enum + CHECK) | Normalizar; conservar valores legados |
| `VacationRequest.leader*` | `vac_request` + `vac_request_event` | Mantener; agregar ownership y transiciones |
| `VacationRequest.username` | `emp_employee.employee_number` / join | Eliminar duplicación gradualmente (vista de compatibilidad) |
| `VacationHistory` (muerta) | `vac_request_event` + `aud_audit_log` | Nueva implementación; no hay datos que migrar |
| `Vacacion` (muerta) | — | Deprecar tabla; conservar solo si contiene datos |
| `tb_politicas_empresa` | `doc_document` (tipo `POLICY`) | Migrar PDFs a almacenamiento con metadata; mantener tabla legada temporalmente |
| `VacationCalculator` (festivos MX) | `cal_holiday` + `CalendarService` | Sembrar festivos MX; permitir festivos de empresa |
| `VacationPolicyService` | `vac_policy` + `vac_policy_rule` | Parametrizar por empresa y vigencia |
| `EmailService` | `notification` + `not_template` + providers | Desacoplar; plantillas versionadas; async/outbox |
| `AnniversaryDays` | `AccrualService` + `vac_period`/`vac_ledger` | Rehacer con idempotencia y periodo |
| `AdminDashboardController` | `dashboard` (read models) | Mantener métricas, agregar alcance por rol/empresa |
| Thymeleaf | `/api/v1` + Next.js | Migración progresiva |
| `ROLE_*` hardcodeados | `PERM_*` + roles | RBAC |
| Sin auditoría | `aud_audit_log` | Nuevo |

---

## F. Cambios SQL Server

### F.1 Principios

1. **No eliminar ni renombrar** tablas/columnas existentes en las primeras fases.
2. Cambios **aditivos** (nuevas columnas nullable con default, nuevas tablas, índices, FKs).
3. Toda evolución vía **Flyway**; `ddl-auto=validate` (nunca `update` en prod).
4. `DATETIME2(3)` en lugar de `DATETIME`; `DATE` para fechas puras; `BIT`; `DECIMAL(5,2)` para días/horas; `NVARCHAR` para texto; `UNIQUEIDENTIFIER` con `DEFAULT NEWID()` para UUID.
5. IDs: `BIGINT` para alto volumen (`aud_*`, `not_*`, `vac_ledger`, `doc_document`); `INT` para catálogos.
6. Soft delete: `deleted_at DATETIME2(3) NULL` + índices únicos filtrados `WHERE deleted_at IS NULL`.
7. Concurrencia: `version INT NOT NULL DEFAULT 0` o `ROWVERSION`.
8. Nombres: `snake_case`, tablas en plural, prefijo por módulo (`sec_`, `org_`, `md_`, `emp_`, `vac_`, `cal_`, `doc_`, `pay_`, `com_`, `not_`, `inc_`, `tw_`, `wp_`, `aud_`).
9. Constraints nombrados (`FK_`, `UX_`, `IX_`, `CK_`, `DF_`) para poder evolucionar sin adivinar.
10. Documentar cada migración y su rollback en `docs/sql/rollback/`.

### F.2 Tablas reutilizables (no duplicar)

| Tabla actual | Uso futuro | Acción |
|---|---|---|
| `users` | Identidad/credenciales (`sec_user` lógico) | **Reutilizar**; agregar `uuid`, `employee_id`, `last_login_at`, `password_changed_at`, `failed_attempts`, `locked_until`, `created_at`, `updated_at`, `deleted_at`, `version` |
| `vacation_requests` | `vac_request` (histórico + nuevas) | **Reutilizar**; agregar columnas; no renombrar todavía |
| `tb_politicas_empresa` | Evidencia histórica de políticas | **Mantener** hasta migrar PDFs a `doc_document`; después marcar como legado |
| `vacation_history` | — | **Reutilizar** como base de `vac_request_event` o dejar legado; no borrar sin backup |
| `vacaciones` | — | **No usar**; validar contenido antes de decidir |

> **No crear** una segunda tabla de usuarios, solicitudes o políticas. El nuevo modelo extiende lo existente.

### F.3 Tablas nuevas por módulo (inventario objetivo)

**Seguridad / común**

- `sec_role`, `sec_permission`, `sec_user_role`, `sec_role_permission`
- `sec_refresh_token` (Fase 14)
- `aud_audit_log`, `aud_login_attempt` (opcional)

**Master data**

- `md_catalog` (tipos de catálogo), `md_catalog_item` (items genéricos)
- `md_document_type`, `md_incident_type`, `md_leave_type` (tipos con comportamiento)

**Organización**

- `org_company`, `org_branch`, `org_department`, `org_position`

**Empleados**

- `emp_employee`, `emp_emergency_contact`, `emp_employment_history` (opcional), `emp_bank_account` (Fase 8)

**Vacaciones**

- `vac_policy`, `vac_policy_rule`, `vac_period`, `vac_ledger`, `vac_request_event`
- Extensión de `vacation_requests` (no tabla nueva)

**Calendario**

- `cal_calendar`, `cal_holiday`, `cal_special_day`

**Documentos**

- `doc_document`

**Nómina (recibos)**

- `pay_receipt`, `pay_period` (opcional)

**Comunicación / notificaciones**

- `com_announcement`, `com_announcement_read`
- `not_template`, `not_notification`, `not_outbox`, `not_device_token` (Fase 14)

**Incidencias**

- `inc_incident`

**Teletrabajo**

- `tw_agreement`, `tw_checklist_item`

**Plan de trabajo**

- `wp_plan`, `wp_objective`, `wp_activity`, `wp_comment`

**Dashboard**

- Sin tablas nuevas en fase 1; consultas/vistas cuando se justifique.

### F.4 DDL representativo (SQL Server)

```sql
-- ============================================================
-- V2__common_security_audit.sql (extracto)
-- ============================================================
ALTER TABLE dbo.users ADD
    uuid UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_users_uuid DEFAULT NEWID(),
    employee_id BIGINT NULL,
    last_login_at DATETIME2(3) NULL,
    password_changed_at DATETIME2(3) NULL,
    failed_attempts INT NOT NULL CONSTRAINT DF_users_failed_attempts DEFAULT 0,
    locked_until DATETIME2(3) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_users_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NULL,
    deleted_at DATETIME2(3) NULL,
    version INT NOT NULL CONSTRAINT DF_users_version DEFAULT 0;

CREATE UNIQUE INDEX UX_users_uuid ON dbo.users(uuid);
CREATE INDEX IX_users_employee_id ON dbo.users(employee_id) WHERE employee_id IS NOT NULL;
GO

CREATE TABLE dbo.sec_role (
    id            INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_sec_role PRIMARY KEY,
    uuid          UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_sec_role_uuid DEFAULT NEWID(),
    code          NVARCHAR(50)  NOT NULL,
    name          NVARCHAR(100) NOT NULL,
    description   NVARCHAR(255) NULL,
    system_role   BIT NOT NULL CONSTRAINT DF_sec_role_system DEFAULT 0,
    enabled       BIT NOT NULL CONSTRAINT DF_sec_role_enabled DEFAULT 1,
    created_at    DATETIME2(3) NOT NULL CONSTRAINT DF_sec_role_created_at DEFAULT SYSUTCDATETIME(),
    updated_at    DATETIME2(3) NULL,
    CONSTRAINT UX_sec_role_code UNIQUE (code)
);
GO

CREATE TABLE dbo.sec_permission (
    id            INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_sec_permission PRIMARY KEY,
    uuid          UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_sec_permission_uuid DEFAULT NEWID(),
    code          NVARCHAR(100) NOT NULL,
    module        NVARCHAR(50)  NOT NULL,
    description   NVARCHAR(255) NULL,
    enabled       BIT NOT NULL CONSTRAINT DF_sec_permission_enabled DEFAULT 1,
    CONSTRAINT UX_sec_permission_code UNIQUE (code)
);
GO

CREATE TABLE dbo.sec_user_role (
    user_id BIGINT NOT NULL,
    role_id INT    NOT NULL,
    CONSTRAINT PK_sec_user_role PRIMARY KEY (user_id, role_id),
    CONSTRAINT FK_sec_user_role_user FOREIGN KEY (user_id) REFERENCES dbo.users(id),
    CONSTRAINT FK_sec_user_role_role FOREIGN KEY (role_id) REFERENCES dbo.sec_role(id)
);
GO

CREATE TABLE dbo.aud_audit_log (
    id             BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_aud_audit_log PRIMARY KEY,
    uuid           UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_aud_audit_log_uuid DEFAULT NEWID(),
    user_id        BIGINT NULL,
    action         NVARCHAR(100) NOT NULL,
    entity         NVARCHAR(100) NOT NULL,
    entity_id      NVARCHAR(100) NULL,
    old_value      NVARCHAR(MAX) NULL,
    new_value      NVARCHAR(MAX) NULL,
    ip             NVARCHAR(45)  NULL,
    user_agent     NVARCHAR(500) NULL,
    correlation_id UNIQUEIDENTIFIER NULL,
    created_at     DATETIME2(3) NOT NULL CONSTRAINT DF_aud_audit_log_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_aud_audit_log_user FOREIGN KEY (user_id) REFERENCES dbo.users(id)
);
CREATE INDEX IX_aud_audit_log_entity ON dbo.aud_audit_log(entity, entity_id, created_at DESC);
CREATE INDEX IX_aud_audit_log_user   ON dbo.aud_audit_log(user_id, created_at DESC);
GO

-- ============================================================
-- V3__organization.sql (extracto)
-- ============================================================
CREATE TABLE dbo.org_company (
    id          INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_org_company PRIMARY KEY,
    uuid        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_org_company_uuid DEFAULT NEWID(),
    code        NVARCHAR(30)  NOT NULL,
    legal_name  NVARCHAR(200) NOT NULL,
    trade_name  NVARCHAR(200) NULL,
    rfc         NVARCHAR(13)  NULL,
    timezone    NVARCHAR(64)  NOT NULL CONSTRAINT DF_org_company_tz DEFAULT 'America/Mexico_City',
    enabled     BIT NOT NULL CONSTRAINT DF_org_company_enabled DEFAULT 1,
    created_at  DATETIME2(3) NOT NULL CONSTRAINT DF_org_company_created_at DEFAULT SYSUTCDATETIME(),
    updated_at  DATETIME2(3) NULL,
    deleted_at  DATETIME2(3) NULL,
    version     INT NOT NULL CONSTRAINT DF_org_company_version DEFAULT 0,
    CONSTRAINT UX_org_company_code UNIQUE (code)
);
GO

CREATE TABLE dbo.org_branch (
    id          INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_org_branch PRIMARY KEY,
    uuid        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_org_branch_uuid DEFAULT NEWID(),
    company_id  INT NOT NULL,
    code        NVARCHAR(30)  NOT NULL,
    name        NVARCHAR(150) NOT NULL,
    address     NVARCHAR(300) NULL,
    enabled     BIT NOT NULL CONSTRAINT DF_org_branch_enabled DEFAULT 1,
    created_at  DATETIME2(3) NOT NULL CONSTRAINT DF_org_branch_created_at DEFAULT SYSUTCDATETIME(),
    updated_at  DATETIME2(3) NULL,
    deleted_at  DATETIME2(3) NULL,
    version     INT NOT NULL CONSTRAINT DF_org_branch_version DEFAULT 0,
    CONSTRAINT FK_org_branch_company FOREIGN KEY (company_id) REFERENCES dbo.org_company(id),
    CONSTRAINT UX_org_branch_company_code UNIQUE (company_id, code)
);
CREATE INDEX IX_org_branch_company ON dbo.org_branch(company_id);
GO

-- org_department: FK branch_id + parent_department_id (jerarquía), UX(branch_id, code)
-- org_position:   FK department_id, UX(department_id, code)
-- Ver implementación en Fase 3.

-- ============================================================
-- V5__employees.sql (extracto)
-- ============================================================
CREATE TABLE dbo.emp_employee (
    id                BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_emp_employee PRIMARY KEY,
    uuid              UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_emp_employee_uuid DEFAULT NEWID(),
    user_id           BIGINT NULL,
    company_id        INT NOT NULL,
    branch_id         INT NOT NULL,
    department_id     INT NOT NULL,
    position_id       INT NOT NULL,
    manager_id        BIGINT NULL,
    employee_number   NVARCHAR(30) NOT NULL,
    first_name        NVARCHAR(100) NOT NULL,
    middle_name       NVARCHAR(100) NULL,
    last_name         NVARCHAR(100) NOT NULL,
    second_last_name  NVARCHAR(100) NULL,
    birth_date        DATE NULL,
    gender_id         INT NULL,
    marital_status_id INT NULL,
    curp              NVARCHAR(18) NULL,
    rfc               NVARCHAR(13) NULL,
    nss               NVARCHAR(11) NULL,
    corporate_email   NVARCHAR(150) NULL,
    personal_email    NVARCHAR(150) NULL,
    phone             NVARCHAR(20)  NULL,
    hire_date         DATE NOT NULL,
    termination_date  DATE NULL,
    status_id         INT NOT NULL,
    work_modality_id  INT NULL,
    enabled           BIT NOT NULL CONSTRAINT DF_emp_employee_enabled DEFAULT 1,
    created_at        DATETIME2(3) NOT NULL CONSTRAINT DF_emp_employee_created_at DEFAULT SYSUTCDATETIME(),
    updated_at        DATETIME2(3) NULL,
    deleted_at        DATETIME2(3) NULL,
    version           INT NOT NULL CONSTRAINT DF_emp_employee_version DEFAULT 0,
    CONSTRAINT FK_emp_employee_user       FOREIGN KEY (user_id) REFERENCES dbo.users(id),
    CONSTRAINT FK_emp_employee_company    FOREIGN KEY (company_id) REFERENCES dbo.org_company(id),
    CONSTRAINT FK_emp_employee_branch     FOREIGN KEY (branch_id) REFERENCES dbo.org_branch(id),
    CONSTRAINT FK_emp_employee_department FOREIGN KEY (department_id) REFERENCES dbo.org_department(id),
    CONSTRAINT FK_emp_employee_position   FOREIGN KEY (position_id) REFERENCES dbo.org_position(id),
    CONSTRAINT FK_emp_employee_manager    FOREIGN KEY (manager_id) REFERENCES dbo.emp_employee(id)
);
CREATE UNIQUE INDEX UX_emp_employee_uuid ON dbo.emp_employee(uuid);
CREATE UNIQUE INDEX UX_emp_employee_user ON dbo.emp_employee(user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX UX_emp_employee_number ON dbo.emp_employee(company_id, employee_number) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX UX_emp_employee_curp ON dbo.emp_employee(curp) WHERE curp IS NOT NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX UX_emp_employee_rfc ON dbo.emp_employee(rfc) WHERE rfc IS NOT NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX UX_emp_employee_nss ON dbo.emp_employee(nss) WHERE nss IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX IX_emp_employee_org ON dbo.emp_employee(company_id, branch_id, department_id, position_id);
CREATE INDEX IX_emp_employee_manager ON dbo.emp_employee(manager_id);
GO

-- ============================================================
-- V6__vacation_ledger.sql (extracto)
-- ============================================================
ALTER TABLE dbo.vacation_requests ADD
    uuid           UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_vacation_requests_uuid DEFAULT NEWID(),
    employee_id    BIGINT NULL,
    period_id      BIGINT NULL,
    requested_by   BIGINT NULL,
    cancelled_at   DATETIME2(3) NULL,
    cancel_reason  NVARCHAR(500) NULL,
    updated_at     DATETIME2(3) NULL,
    deleted_at     DATETIME2(3) NULL,
    version        INT NOT NULL CONSTRAINT DF_vacation_requests_version DEFAULT 0;
CREATE UNIQUE INDEX UX_vacation_requests_uuid ON dbo.vacation_requests(uuid);
CREATE INDEX IX_vacation_requests_user_dates ON dbo.vacation_requests(user_id, start_date, end_date);
CREATE INDEX IX_vacation_requests_status ON dbo.vacation_requests(status, start_date);
GO

CREATE TABLE dbo.vac_period (
    id              BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_vac_period PRIMARY KEY,
    uuid            UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_vac_period_uuid DEFAULT NEWID(),
    employee_id     BIGINT NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    anniversary_date DATE NOT NULL,
    entitled_days   DECIMAL(5,2) NOT NULL,
    opening_balance DECIMAL(5,2) NOT NULL CONSTRAINT DF_vac_period_opening DEFAULT 0,
    status          NVARCHAR(20) NOT NULL CONSTRAINT DF_vac_period_status DEFAULT 'OPEN',
    created_at      DATETIME2(3) NOT NULL CONSTRAINT DF_vac_period_created_at DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2(3) NULL,
    version         INT NOT NULL CONSTRAINT DF_vac_period_version DEFAULT 0,
    CONSTRAINT FK_vac_period_employee FOREIGN KEY (employee_id) REFERENCES dbo.emp_employee(id),
    CONSTRAINT CK_vac_period_dates CHECK (period_end >= period_start),
    CONSTRAINT CK_vac_period_days CHECK (entitled_days >= 0 AND opening_balance >= 0)
);
CREATE UNIQUE INDEX UX_vac_period_employee_start ON dbo.vac_period(employee_id, period_start);
GO

CREATE TABLE dbo.vac_ledger (
    id             BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_vac_ledger PRIMARY KEY,
    uuid           UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_vac_ledger_uuid DEFAULT NEWID(),
    employee_id    BIGINT NOT NULL,
    period_id      BIGINT NOT NULL,
    movement_type  NVARCHAR(30) NOT NULL,  -- OPENING_BALANCE, ACCRUAL, HOLD, USAGE, RELEASE, ADJUSTMENT, EXPIRY
    days           DECIMAL(5,2) NOT NULL,  -- positivo/negativo según el tipo
    reference_type NVARCHAR(30) NULL,
    reference_id   BIGINT NULL,
    notes          NVARCHAR(500) NULL,
    created_by     BIGINT NULL,
    created_at     DATETIME2(3) NOT NULL CONSTRAINT DF_vac_ledger_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_vac_ledger_employee FOREIGN KEY (employee_id) REFERENCES dbo.emp_employee(id),
    CONSTRAINT FK_vac_ledger_period   FOREIGN KEY (period_id) REFERENCES dbo.vac_period(id)
);
CREATE INDEX IX_vac_ledger_employee_period ON dbo.vac_ledger(employee_id, period_id, created_at);
CREATE INDEX IX_vac_ledger_reference ON dbo.vac_ledger(reference_type, reference_id);
GO
```

> El DDL definitivo se entregará en la migración correspondiente. Este extracto valida tipos y convenciones para SQL Server.

### F.5 Índices mínimos requeridos

| Tabla | Índices |
|---|---|
| `users` | `UX(username)` (existente), `UX(email)` filtrado `WHERE email IS NOT NULL`, `UX(uuid)`, `IX(employee_id)` |
| `vacation_requests` | `IX(user_id, start_date, end_date)`, `IX(status, start_date)`, `IX(leader_id, status)`, `UX(uuid)` |
| `emp_employee` | `UX(company_id, employee_number)`, `UX(curp/rfc/nss)` filtrados, `IX(company_id, branch_id, department_id, position_id)`, `IX(manager_id)` |
| `vac_period` | `UX(employee_id, period_start)` |
| `vac_ledger` | `IX(employee_id, period_id, created_at)`, `IX(reference_type, reference_id)` |
| `cal_holiday` | `UX(company_id, holiday_date, type)`, `IX(holiday_date)` |
| `doc_document` | `IX(employee_id, type_id)`, `UX(uuid)`, `IX(storage_key)` |
| `pay_receipt` | `IX(employee_id, period_start)`, `UX(uuid)` |
| `not_notification` | `IX(user_id, read_at, created_at)` |
| `aud_audit_log` | `IX(entity, entity_id, created_at)`, `IX(user_id, created_at)` |

### F.6 Estrategia de migraciones Flyway

```
src/main/resources/db/migration/
├── V1__baseline_legacy.sql              # DDL real de la BD actual (generado y revisado)
├── V2__common_security_audit.sql        # columnas comunes, RBAC, auditoría
├── V3__organization.sql                 # company/branch/department/position
├── V4__masterdata.sql                   # catálogos + seeds
├── V5__employees.sql                    # empleados + migración users→employees
├── V6__vacation_ledger.sql              # periodos, ledger, extensión de solicitudes + backfill
├── V7__calendar.sql                     # calendario + festivos MX seed
├── V8__documents.sql                    # documentos + migración de políticas
├── V9__payroll.sql
├── V10__communication_notifications.sql
├── V11__incidents.sql
├── V12__telework.sql
├── V13__workplan.sql
└── V14__indexes_constraints_cleanup.sql
```

**Configuración:**

```properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
spring.flyway.locations=classpath:db/migration
spring.jpa.hibernate.ddl-auto=validate
```

**Procedimiento obligatorio por migración:**

1. Respaldo completo + verificación de restauración.
2. Ejecutar en copia de producción.
3. Medir tiempos y bloqueos (DDL en SQL Server puede bloquear).
4. Validar conteos antes/después.
5. Documentar rollback en `docs/sql/rollback/Vxx__rollback.sql`.
6. Aprobar ventana de mantenimiento.
7. Desplegar y validar.

### F.7 Migración de saldos (decisión crítica)

La base actual descuenta días **al crear** la solicitud y **no los repone** al rechazar. Los saldos actuales ya reflejan esos consumos. Por lo tanto:

1. **No recalcular** el historial ni “corregir” saldos automáticamente.
2. Crear `vac_period` vigente por empleado con:
   - `entitled_days = users.vacation_days_entitled`
   - `opening_balance = users.vacation_days_available`
3. Crear un movimiento `OPENING_BALANCE` por ese saldo.
4. Marcar las solicitudes históricas como `legacy_balance = 1` para que **no generen movimientos** en el ledger.
5. A partir del corte, toda solicitud nueva usa `HOLD → USAGE / RELEASE`.
6. Generar un reporte de reconciliación: `opening_balance + movimientos posteriores = saldo mostrado`.
7. Congelar escrituras al saldo legado (`users.vacation_days_available`) y mantenerlo solo como referencia de solo lectura hasta retirarlo.

Esto preserva los datos históricos (Regla 50) y evita dobles descuentos.

### F.8 Consultas de introspección

Ver `docs/sql/00_introspection_sqlserver.sql` (solo lectura). Incluye:

- Tablas y conteos.
- Columnas, tipos, nulabilidad, defaults.
- PK, FK, unique, check, índices, identity.
- Procedimientos, vistas, funciones, triggers.
- Jobs de SQL Server.
- Chequeos de calidad de datos (traslapes, saldos negativos, estados, fechas inválidas, duplicados, huérfanos).

---

## G. Roadmap

> Ajuste al plan original: se agrega **Fase 0 (auditoría, completada)** y **Fase 0.5 (remediación de seguridad)** antes de tocar código funcional. La numeración 1–15 se conserva.

| Fase | Objetivo | Entregables | Criterio de salida | Est. |
|---|---|---|---|---|
| 0 | Auditoría | Este documento + script de introspección | Revisado y aprobado | ✅ |
| 0.5 | **Remediación urgente** | Rotación de secretos, CSRF, IDOR, `ddl-auto=none` en prod, backups | Checklist §H.3 completo | 1 sem |
| 1 | Foundation | Boot 3.5.x, paquetes por dominio, `ApiResponse`, errores, DTO/mappers, OpenAPI, validación, logs, Flyway baseline, auditoría base, tests de humo | `mvn verify` verde; `/api/v1/ping` documentado; baseline aplicada en copia | 3–4 sem |
| 2 | Master data | Catálogos + seeds + APIs de lectura + administración | CRUD probado, permisos, paginación | 1–2 sem |
| 3 | Organization | Company/Branch/Department/Position + jerarquía + APIs | CRUD, validación de jerarquía, scoping, tests | 2 sem |
| 4 | Employees | `emp_employee`, contactos, validaciones CURP/RFC/NSS, `/me`, migración desde `users` | Empleados migrados y reconciliados; APIs seguras | 3–4 sem |
| 5 | Vacations | Ledger, periodos, políticas, máquina de estados, aprobaciones, cancelación, historial, job de acumulación | Reglas en backend; saldo conciliado; sin sobreventa; tests de concurrencia | 4–6 sem |
| 6 | Calendar | Calendario laboral, festivos oficiales/empresa, eventos, días especiales | `CalendarService.isWorkingDay`; festivos parametrizados | 2 sem |
| 7 | Documents | Tipos, carga/descarga autorizada, metadata, hash, storage abstraction | Sin URLs públicas; auditoría de descargas | 2–3 sem |
| 8 | Payroll | Recibos, periodos, carga admin, consulta/descarga empleado | Autorización por empleado; storage seguro | 2 sem |
| 9 | Communication + Notifications | Comunicados, avisos, lectura, outbox, email/web providers | Notificaciones desacopladas y reintentables | 2–3 sem |
| 10 | Incidents | Tipos, registro, aprobación, evidencias | Reglas parametrizables; sin asumir legalidad | 2–3 sem |
| 11 | Telework | Acuerdos, modalidad, porcentaje, checklist, evidencias, desconexión, reversibilidad | Campos marcados requisito legal vs control interno | 2–3 sem |
| 12 | Work plan | Planes, objetivos, actividades, avance, evidencias | Empleado consulta su plan; líder da seguimiento | 2 sem |
| 13 | Dashboard | Dashboards por rol (empleado, líder, RRHH, admin) | Métricas correctas y paginadas; sin cargas masivas | 2 sem |
| 14 | Mobile ready | JWT + refresh, `/me`, push, contratos estables, CORS, versionado | App móvil puede operar sin reglas propias | 2–4 sem |
| 15 | Google Workspace SSO | OAuth2, dominio corporativo, mapeo Google→User→Employee | Validación de dominio; pruebas de seguridad | 3–5 sem |

**Paralelo:** migración de frontend a Next.js por módulos a partir de la Fase 5; no bloquea backend.

**Ramas:** crear `develop`; trabajar `feature/<módulo>`, `bugfix/<tema>`, `release/<versión>`; PR obligatorio con revisión y CI. Commits convencionales (`feat(employee): ...`).

---

## H. Riesgos

### H.1 Riesgos técnicos

| Riesgo | Prob. | Impacto | Mitigación |
|---|---|---|---|
| Secretos ya filtrados y usados por terceros | Alta | Crítico | Rotar todo **hoy**; revisar logs de BD/SMTP; considerar purga de historial con BFG tras coordinar |
| Pérdida/corrupción de datos por `ddl-auto=update` | Media | Crítico | Flyway + `validate`; backups automáticos; ventana de cambio |
| Sobregiro de saldo por concurrencia | Alta | Alto | Ledger + lock/versión + tests de concurrencia |
| Aprobaciones indebidas (IDOR) | Alta | Crítico | Ownership en servicios; permisos; tests de autorización |
| Migración de saldos incorrecta | Media | Alto | Estrategia §F.7; reconciliación; corte controlado |
| Duplicidad de usuarios/empleados | Media | Alto | `employee_id` único; migración idempotente; validaciones |
| Regresiones sin tests | Alta | Alto | Red de pruebas desde Fase 1; CI obligatorio |
| Lentitud por `findAll()` y falta de índices | Alta | Medio | Paginación, proyecciones, índices |
| Correos dentro de transacciones | Alta | Medio | Outbox + async; after-commit |
| Dependencia de CDNs sin SRI | Media | Medio | Empaquetar assets o fijar versión + SRI |
| Ruta `C:\logs` y `src/main/resources` en JAR | Alta | Medio | Configuración portable; classpath resources |
| Actualización de Spring Boot rompe algo | Media | Medio | Actualizar en Fase 1 con tests; cambios incrementales |

### H.2 Riesgos de negocio / legales

| Riesgo | Mitigación |
|---|---|
| Suponer obligaciones de NOM-037 no aplicables | Etiquetar cada campo como **requisito legal** o **control interno**; validación con Legal/RRHH antes de publicar |
| Política de vacaciones distinta a la LFT o contratos | Parametrizar por empresa; revisión legal; conservar escala actual como configuración inicial |
| Prima vacacional y caducidad (18 meses) no contempladas | Incluir en `vac_policy`/`vac_period`; validar con RRHH |
| Datos personales sensibles (CURP, RFC, NSS, domicilio, banco) | Cifrado, minimización, enmascarado, permisos, retención |
| Historial laboral usado en auditorías | Ledger inmutable + `aud_audit_log`; no borrado físico |
| Dependencia de una sola persona | Documentación, ADRs, tests, PRs |

### H.3 Checklist de remediación inmediata (Fase 0.5)

1. Rotar contraseña de BD y de correo; revocar app password de Gmail; rotar Brevo.
2. Restringir acceso de red a SQL Server (IP allowlist / VPN); habilitar TLS (`encrypt=true`).
3. Sacar secretos de `application*.properties`; usar variables de entorno; `.gitignore` para `application-local.properties`.
4. Poner `spring.jpa.hibernate.ddl-auto=none` en producción hasta tener baseline.
5. Deshabilitar `spring.jpa.show-sql` en producción.
6. Habilitar CSRF en la cadena web (formularios + fetch con token).
7. Corregir IDOR: validar ownership en líder y admin; separar endpoints de líder y admin.
8. Quitar mutación de `GET /admin/requests`.
9. Corregir el rechazo del líder para que no pase a `PENDING_ADMIN`.
10. Respaldar la base y probar restauración.
11. Revisar si las credenciales expuestas fueron utilizadas (logs).
12. No desplegar el experimento Supabase/Vercel que está en el historial.

---

## I. Primer bloque de desarrollo

### I.1 Decisión

El primer bloque **no es “Empleados”**. Técnicamente el orden correcto es:

> **Bloque 1 = Fase 1 (Foundation) + Fase 2 (Master Data) + Fase 3 (Organization).**

Razones:

1. Sin fundación (API envelope, errores, DTO, Flyway, tests, seguridad) cualquier módulo nace con la misma deuda actual.
2. `Employee` depende de `Company/Branch/Department/Position` y de catálogos (`gender`, `marital_status`, `employment_status`, `work_modality`).
3. `Vacation` depende de `Employee` y del calendario.
4. Migrar vacaciones antes de tener empleados y ledger obligaría a rehacer la migración.

**Primer módulo de negocio a implementar:** `organization` (después de `masterdata`), porque es el esqueleto sobre el que se montan empleados y todo lo demás.

### I.2 Alcance del Bloque 1

#### PR 1 — Build & configuration hardening

- Actualizar Spring Boot a 3.5.x; mantener Java 17.
- Agregar: `spring-boot-starter-validation`, `flyway-core`, `flyway-sqlserver`, `springdoc-openapi-starter-webmvc-ui`, `spring-boot-starter-actuator`, `spring-boot-starter-test`, JaCoCo, Spotless.
- Reemplazar secretos por `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`, `${MAIL_*}`.
- Perfiles `local`, `dev`, `prod`; logs portables; `ddl-auto=validate` (tras baseline).
- Eliminar `data.sql` y `keystore.p12` del control de versiones.
- `.gitignore` para `application-local.properties`, `*.log`, `.env`.

#### PR 2 — Common kernel

- `BaseEntity` (`id`, `uuid`, `createdAt`, `updatedAt`, `version`), `SoftDeletable`.
- `ApiResponse<T>`, `PageResponse<T>`, `ApiError`, `FieldError`.
- `GlobalExceptionHandler` (`ResourceNotFoundException`, `BusinessException`, `ValidationException`, `AccessDeniedException`, `AuthenticationException`).
- `ErrorCode` catalog.
- Configuración de OpenAPI + ejemplos.
- `AuditService` base + tabla `aud_audit_log`.

#### PR 3 — Security baseline

- Dos `SecurityFilterChain`: web (sesión + CSRF) y `/api/v1/**` (stateless; inicialmente sesión, preparada para JWT).
- RBAC: `sec_role`, `sec_permission`, `sec_user_role`, `sec_role_permission`; seeds.
- `@PreAuthorize` + `PermissionEvaluator`.
- Corregir IDOR y CSRF (hotfix).
- Política de contraseñas y bloqueo por intentos (mínimo viable).
- Tests de autorización (empleado no puede ver a otro; líder solo sus solicitudes; líder no puede usar endpoints admin).

#### PR 4 — Database baseline & migrations

- Ejecutar introspección y generar `V1__baseline_legacy.sql`.
- `V2__common_security_audit.sql` (columnas comunes, RBAC, auditoría).
- Aplicar en copia; validar `ddl-auto=validate`.
- Documentar rollback.
- Añadir índices mínimos a tablas legadas.

#### PR 5 — Master data

- `md_catalog`, `md_catalog_item` + APIs:
  - `GET /api/v1/masterdata/catalogs/{type}`
  - `GET /api/v1/masterdata/catalogs/{type}/{code}`
  - CRUD admin con permisos.
- Seeds: género, estado civil, estatus de empleo, modalidad, tipo de contrato, nivel educativo, tipo de documento, tipo de incidencia, tipo de permiso, tipo de festivo.
- Paginación, validación, tests.

#### PR 6 — Organization

- Entidades y tablas `org_company`, `org_branch`, `org_department`, `org_position`.
- APIs:
  - `GET/POST /api/v1/companies`
  - `GET/POST /api/v1/branches?companyUuid=...`
  - `GET/POST /api/v1/departments?branchUuid=...`
  - `GET/POST /api/v1/positions?departmentUuid=...`
- Reglas: código único por padre, jerarquía válida, no borrar con dependencias, soft delete.
- Autorización: `ORG_VIEW`, `ORG_MANAGE`.
- Tests de integración y validación.

### I.3 Definition of Done del Bloque 1

- `mvn clean verify` verde (unit + integration).
- Cobertura ≥ 70% en código nuevo.
- OpenAPI generado y probado.
- Migraciones aplicadas en copia y en entorno de pruebas; rollback probado.
- `ddl-auto=validate` funcionando.
- Sin secretos en el repositorio (verificación con `gitleaks` o similar).
- CSRF e IDOR corregidos y cubiertos por tests.
- README técnico actualizado (arquitectura, cómo correr, variables de entorno).
- PRs revisados y mergeados a `develop`.

### I.4 Fuera de alcance del Bloque 1

- JWT/refresh, push, SSO Google.
- Empleados, vacaciones, nómina, documentos, etc. (fases posteriores).
- Migración de frontend a Next.js.
- Recálculo o “corrección” masiva de saldos.

### I.5 Primeros comandos (cuando se apruebe el inicio)

```bash
# 1. Crear develop y rama de trabajo
git checkout main
git pull origin main
git checkout -b develop
git push -u origin develop
git checkout -b feature/foundation-api

# 2. Baseline de base de datos (solo lectura, en copia)
# Ejecutar docs/sql/00_introspection_sqlserver.sql en SSMS contra la copia.

# 3. Build de referencia
mvn clean verify

# 4. Implementar PR 1 y PR 2, luego PR 3/4/5/6 en ese orden.
```

---

## Apéndice A — Evidencia por archivo

| Archivo | Hallazgo |
|---|---|
| `config/SecurityConfig.java:69` | CSRF deshabilitado |
| `config/SecurityConfig.java:89-103` | Líderes con acceso a `/admin/requests/**`; reglas por URL |
| `controller/LeaderController.java:51-79` | Sin ownership; rechazo pasa a `PENDING_ADMIN`; sin transacción |
| `controller/AdminVacationController.java:102-111` | `GET` muta estado |
| `controller/AdminVacationController.java:133-186` | Aprobar/rechazar sin máquina de estados ni auditoría |
| `controller/VacationController.java:226-238` | Bloqueo de fechas en inicio/fin |
| `controller/VacationController.java:285-330` | Traslape + descuento de saldo sin lock |
| `controller/VacationController.java:360-394` | Correos dentro de la petición/transacción |
| `controller/AdminUserController.java:124-136` | Recálculo destructivo de saldo |
| `job/AnniversaryDays.java:43-47` | Delta siempre 0; no idempotente |
| `model/User.java:113-122` | Saldo mutable con regla embebida |
| `model/VacationRequest.java:57-65` | Estados string; default `PENDING_LEADER` |
| `service/EmailService.java` | 679 líneas; HTML inline; remitente fijo; sin outbox |
| `service/VacationService.java:41-58` | Código no utilizado |
| `resources/application.properties` | Credenciales, IP, `ddl-auto=update`, `show-sql=true` |
| `resources/application-prod.properties` | Credenciales, `ddl-auto=update`, `show-sql=true` |
| `resources/data.sql` | Columnas inexistentes; `{noop}` |
| `templates/admin/request-vacation.html` | Plantilla huérfana |

## Apéndice B — Catálogo inicial de permisos

```
EMPLOYEE_VIEW_SELF, EMPLOYEE_VIEW, EMPLOYEE_CREATE, EMPLOYEE_UPDATE, EMPLOYEE_TERMINATE
ORG_VIEW, ORG_MANAGE
MASTERDATA_VIEW, MASTERDATA_MANAGE
VACATION_CREATE, VACATION_VIEW_SELF, VACATION_VIEW, VACATION_APPROVE, VACATION_CANCEL, VACATION_ADJUST
CALENDAR_VIEW, CALENDAR_MANAGE
DOCUMENT_VIEW_SELF, DOCUMENT_VIEW, DOCUMENT_UPLOAD, DOCUMENT_DELETE
PAYROLL_VIEW_SELF, PAYROLL_VIEW, PAYROLL_UPLOAD
COMMUNICATION_VIEW, COMMUNICATION_CREATE, COMMUNICATION_PUBLISH
INCIDENT_VIEW_SELF, INCIDENT_VIEW, INCIDENT_CREATE, INCIDENT_APPROVE
TELEWORK_VIEW_SELF, TELEWORK_VIEW, TELEWORK_MANAGE
WORKPLAN_VIEW_SELF, WORKPLAN_VIEW, WORKPLAN_MANAGE
NOTIFICATION_VIEW_SELF, NOTIFICATION_MANAGE
AUDIT_VIEW
USER_MANAGE, ROLE_MANAGE, PERMISSION_MANAGE
DASHBOARD_VIEW_SELF, DASHBOARD_VIEW
```

## Apéndice C — Glosario de estados de vacaciones

| Estado | Significado | Transiciones permitidas |
|---|---|---|
| `DRAFT` | Borrador (futuro) | `SUBMITTED` |
| `PENDING_LEADER` | Esperando líder | `PENDING_ADMIN`, `REJECTED`, `CANCELLED` |
| `PENDING_ADMIN` | Esperando RRHH/Admin | `APPROVED`, `REJECTED`, `CANCELLED` |
| `APPROVED` | Aprobada y consumida | `CANCELLED` (con reverso en ledger) |
| `REJECTED` | Rechazada (saldo liberado) | — |
| `CANCELLED` | Cancelada por el empleado (saldo liberado) | — |

> Los estados legados `PENDING` deben mapearse a `PENDING_ADMIN` durante la migración, previa verificación de datos.

---

**Fin de la auditoría. No se modificó código de aplicación.**
