# Bloque 10 — Autorización RBAC por rol, permiso, alcance y recurso

## Principio

La decisión final de autorización combina:

```text
ROL + PERMISO + ALCANCE + RELACIÓN JERÁRQUICA + PROPIEDAD DEL RECURSO
```

El frontend solo oculta opciones. El backend vuelve a validar cada operación.

## Componentes

### `AccessGuard`

Valida permisos efectivos `PERM_*` cargados desde base de datos.

### `AuthorizationService`

Servicio central de autorización de recurso:

- `canViewEmployee`
- `canViewSensitiveEmployee`
- `canUpdateEmployee`
- `canViewVacation`
- `canApproveVacation`
- `canCancelVacation`
- `canCreateVacationFor`
- `canViewPayroll`
- `canViewDocument`
- `canViewIncident`
- `canViewTelework`
- `canViewWorkplan`
- y sus variantes `assert...`

También valida:

- propiedad (`self`);
- relación directa líder → colaborador;
- alcance global;
- segregación de funciones;
- que un usuario no apruebe lo que él creó.

## Permisos de alcance

Migración `V14__authorization_scope_permissions.sql`:

- `EMPLOYEE_VIEW_SELF`, `EMPLOYEE_VIEW_TEAM`, `EMPLOYEE_VIEW_ALL`
- `EMPLOYEE_CREATE`, `EMPLOYEE_UPDATE`, `EMPLOYEE_DISABLE`
- `VACATION_CREATE_SELF`, `VACATION_VIEW_SELF`, `VACATION_VIEW_TEAM`
- `VACATION_APPROVE_TEAM`, `VACATION_MANAGE_ALL`, `VACATION_CANCEL_SELF`
- `PAYROLL_VIEW_SELF`, `PAYROLL_VIEW_TEAM`, `PAYROLL_MANAGE`
- `DOCUMENT_VIEW_SELF`, `DOCUMENT_VIEW_TEAM`, `DOCUMENT_MANAGE`
- `INCIDENT_CREATE_SELF`, `INCIDENT_CREATE_TEAM`, `INCIDENT_VIEW_TEAM`, `INCIDENT_MANAGE`
- `TELEWORK_VIEW_TEAM`, `TELEWORK_MANAGE`
- `WORKPLAN_VIEW_TEAM`, `WORKPLAN_MANAGE`
- `DASHBOARD_VIEW_TEAM`, `DASHBOARD_VIEW_ALL`

`V16__leader_legacy_vacation_permissions.sql` retira del líder los permisos históricos amplios de vacaciones.

`PAYROLL_VIEW_TEAM` y `DOCUMENT_VIEW_TEAM` **no** se otorgan automáticamente al líder: requieren asignación explícita por ADMIN, conforme a la regla “SEGÚN”.

## Matriz aplicada

| Función | ADMIN | LÍDER | COLABORADOR |
|---|---|---|---|
| Ver su perfil | Sí | Sí | Sí |
| Ver equipo | Sí | Sí | No |
| Ver cualquier empleado | Sí | No | No |
| Ver datos sensibles | Sí | No | Solo self |
| Crear/editar empleados | Sí | No | No |
| Aprobar vacaciones de equipo | Sí | Solo asignadas | No |
| Aprobar su propia solicitud | No | No | No |
| Ver nómina propia | Sí | Sí | Sí |
| Ver nómina de equipo | Sí | Solo permiso explícito | No |
| Administrar nómina | Sí | No | No |
| Ver documentos propios | Sí | Sí | Sí |
| Ver documentos de equipo | Sí | Solo permiso explícito | No |
| Administrar documentos | Sí | No | No |
| Publicar comunicados | Sí | No | No |
| Consultar auditoría | Sí | No | No |
| Gestionar usuarios/roles | Sí | No | No |

## Reglas críticas implementadas

### IDOR

- `GET /api/v1/employees/{uuid}` valida self/team/all.
- Líder no puede consultar empleados de otro líder.
- Colaborador no puede consultar otro empleado.
- Nómina y documentos validan propiedad/alcance en el servicio.
- Las descargas pasan por la misma validación.

### Aprobación y segregación

- Nadie aprueba su propia solicitud.
- Nadie aprueba una solicitud que él mismo creó.
- El líder solo decide solicitudes asignadas a él.
- Administración solo decide en la etapa administrativa.

### Último administrador

`UserService` impide desactivar o degradar al último `ROLE_ADMIN` activo.

### Cambio de rol

Solo `ADMIN`/`SUPER_ADMIN` puede operar `/admin/users`, adicionalmente protegido por permisos.

### Privacidad

- Líder ve información operativa de su equipo.
- Líder no ve CURP/RFC/NSS, contactos de emergencia, documentos personales ni recibos por defecto.
- Colaborador solo ve su propia información.

### Auditoría

- `aud_audit_log` registra usuario, acción, entidad, valores anterior/nuevo, IP, user-agent, correlación, resultado y fecha.
- `V15__audit_result.sql` agrega `result`.
- Login exitoso/fallido auditado.
- Descargas de documentos y recibos auditadas.
- Ajustes de saldo registran anterior/nuevo/motivo/actor.
- API de consulta: `GET /api/v1/audit` con `AUDIT_VIEW`.

## Verificación

- `mvn test`: **222 pruebas, 0 fallos**.
- Pruebas unitarias de autorización:
  - self/team/all;
  - datos sensibles;
  - aprobación asignada;
  - prohibición de autoaprobación;
  - nómina propia vs. equipo;
  - último administrador.
- Pruebas E2E con JWT:
  - líder consulta otro empleado → `403`;
  - colaborador consulta otro empleado → `403`;
  - colaborador consulta su perfil → `200`;
  - líder consulta nómina/documentos administrativos → `403`;
  - auditoría para líder → `403`, para admin → `200`;
  - descarga de recibo: dueño/admin `200`, líder/otro colaborador `403`.
- Flyway en base `vacaciones`: V1–V16 con `success = 1`.

## Módulos futuros

Incidencias, teletrabajo y plan de trabajo ya tienen permisos y métodos base en `AuthorizationService`. Al implementar sus tablas/controllers deben reutilizar este servicio y no duplicar reglas de alcance.
