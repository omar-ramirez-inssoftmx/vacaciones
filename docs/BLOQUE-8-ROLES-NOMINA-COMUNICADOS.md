# Bloque 8 — Cierre por roles, nómina y comunicados

## Objetivo

Completar la experiencia web para los tres roles sobre los módulos ya construidos, agregar **nómina** y **comunicados internos**, y cerrar ajustes de responsive y del recorrido guiado.

## Completado por rol

### Colaborador

- `Mi perfil`: datos personales/laborales y alta/baja de contactos de emergencia.
- `Mis documentos`: consulta y descarga de documentos visibles del expediente.
- `Mis recibos de nómina`: consulta y descarga de recibos PDF publicados.
- `Comunicados`: avisos internos activos.
- Se mantienen vacaciones, estatus y políticas.

### Líder

- Todo lo anterior para autoservicio.
- `Mi equipo`: colaboradores que reportan directamente al líder.
- Bandeja de solicitudes e historial existentes.
- Permiso `PAYROLL_VIEW_SELF` completado en base de datos (`V12`).

### Administrador / RH

- `Empleados`: directorio, búsqueda, expediente, alta, edición y baja.
- `Documentos`: búsqueda, carga, descarga y eliminación de documentos.
- `Nómina`: creación de periodos, carga de recibos PDF, búsqueda y descarga.
- `Comunicados`: publicación, búsqueda y eliminación.
- Se mantienen usuarios, solicitudes, calendario, políticas y dashboard.

## Migraciones

| Migración | Contenido |
|---|---|
| `V11__payroll.sql` | `pay_period`, `pay_receipt`, índices y restricciones. |
| `V12__leader_payroll_permission.sql` | `PAYROLL_VIEW_SELF` para el rol `LEADER`. |
| `V13__communications.sql` | `com_announcement` y sus índices. |

## API nueva

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/employees/me/emergency-contacts` | `EMPLOYEE_VIEW_SELF` |
| POST | `/api/v1/employees/me/emergency-contacts` | `EMPLOYEE_VIEW_SELF` |
| PUT | `/api/v1/employees/me/emergency-contacts/{uuid}` | `EMPLOYEE_VIEW_SELF` |
| DELETE | `/api/v1/employees/me/emergency-contacts/{uuid}` | `EMPLOYEE_VIEW_SELF` |
| GET | `/api/v1/payroll/periods` | `PAYROLL_VIEW` / `PAYROLL_UPLOAD` |
| POST | `/api/v1/payroll/periods` | `PAYROLL_UPLOAD` |
| GET | `/api/v1/payroll/me` | `PAYROLL_VIEW_SELF` |
| GET | `/api/v1/payroll` | `PAYROLL_VIEW` |
| POST | `/api/v1/payroll/receipts` | `PAYROLL_UPLOAD` |
| GET | `/api/v1/payroll/receipts/{uuid}/download` | `PAYROLL_VIEW` / `PAYROLL_VIEW_SELF` |
| DELETE | `/api/v1/payroll/receipts/{uuid}` | `PAYROLL_UPLOAD` |
| GET | `/api/v1/communications` | `COMMUNICATION_VIEW` |
| POST | `/api/v1/communications` | `COMMUNICATION_PUBLISH` |
| DELETE | `/api/v1/communications/{uuid}` | `COMMUNICATION_PUBLISH` |

## Pantallas web nuevas

- `/profile`
- `/documents`
- `/payroll`
- `/communications`
- `/leader/team`
- `/admin/employees`
- `/admin/employees/new`
- `/admin/employees/{uuid}`
- `/admin/employees/{uuid}/edit`
- `/admin/documents`
- `/admin/payroll`
- `/admin/communications`

Las pantallas nuevas comparten fragmentos (`people-head`, `people-nav`), menú por rol y tema claro/oscuro.

## Responsive

- Se agregó `responsive.css` global en todas las pantallas.
- Se corrigió `viewport` faltante en:
  - `admin/admin_requests_history.html`
  - `admin/calendar.html`
  - `vacations/request.html`
- Ajustes para:
  - navbar y menús;
  - heroes y acciones;
  - filtros, tablas y modales;
  - inputs a 16px en móvil para evitar zoom en iOS;
  - botones y navegación táctil.

## Recorrido guiado

- Un solo botón visible por usuario.
- Nuevos pasos para perfil, documentos, nómina, comunicados, empleados, documentos admin y equipo del líder.
- Barra de progreso y menú de pasos con botones numerados.
- Estilos oscuro/claro y comportamiento tipo bottom-sheet en móvil.
- Navegación por teclado (`←`, `→`, `Esc`) conservada.

## Verificación

- `mvn test`: **212 pruebas, 0 fallos**.
- Login y navegación con `admin`, `lider` y `colaborador`.
- Páginas nuevas responden `200` según rol.
- Alta y edición de empleado probadas desde la UI y limpiadas después.
- Nómina probada de punta a punta:
  - creación de periodo;
  - carga de PDF;
  - consulta desde el colaborador;
  - descarga y comparación byte a byte;
  - limpieza de datos de prueba.
- Comunicados probados:
  - publicación;
  - listado para colaborador;
  - listado administrativo;
  - eliminación.
- Flyway en base `vacaciones`: V1–V13 con `success = 1`.

## Pendiente del roadmap

- Fase 10: incidencias.
- Fase 11: teletrabajo.
- Fase 12: plan de trabajo.
- Fase 9 (complemento): notificaciones/outbox y lectura de comunicados.
- UI de auditoría (`AUDIT_VIEW`).
