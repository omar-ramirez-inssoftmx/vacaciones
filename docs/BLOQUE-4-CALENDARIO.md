# Bloque 4 — Calendar (calendario laboral)

| Campo | Valor |
|---|---|
| Rama | `feature/calendar-module` (base `develop`) |
| Alcance | Fase 6 del roadmap |
| Fecha | 2026-06-10 |
| Estado | Implementado, compilado y probado (`mvn clean verify`) |
| Pruebas | 170 tests, 0 fallos |
| Cobertura código nuevo | 76.7% instrucciones (JaCoCo) |

---

## 1. Qué se implementó

### 1.1 Migración `V7__calendar.sql`

- `cal_calendar`: calendario por empresa.
  - `workweek` configurable (por defecto `MON,TUE,WED,THU,FRI`).
  - zona horaria.
- `cal_holiday`: festivos.
  - `company_id` nulo = festivo **oficial global**.
  - `company_id` con valor = festivo **de empresa**.
  - tipos `OFFICIAL` / `COMPANY`.
  - `year`, fecha, descripción, enabled, soft delete.
  - índices únicos filtrados para evitar duplicados por fecha/tipo/empresa.
- `cal_special_day`: eventos, cierres y días especiales.
  - rango de fechas.
  - `affects_workdays`: si el día deja de ser laborable.
  - tipos `EVENT` / `CLOSURE` / `SPECIAL`.
- Calendario default para cada empresa activa.
- Festivos oficiales de México **2024–2030** precargados.
- Se conservan `CALENDAR_VIEW` y `CALENDAR_MANAGE` del RBAC existente.

### 1.2 Motor de días hábiles

`CalendarService.isWorkingDay(companyId, date)`:

1. Verifica que el día pertenezca a la semana laboral configurada.
2. Verifica que no exista un festivo oficial o de empresa.
3. Verifica que no exista un día especial/cierre con `affectsWorkdays = true`.

`CalendarWorkingDayCalculator` implementa `WorkingDayCalculator` y reemplaza el cálculo hardcodeado anterior en Vacations. El flujo de solicitudes y validaciones ahora usa:

```
empresa → calendario → festivos/cierres → días hábiles
```

### 1.3 Eventos de calendario

El endpoint de eventos combina:

- festivos oficiales,
- festivos de empresa,
- días especiales/cierres,
- vacaciones aprobadas de la empresa.

### 1.4 Integración con el sistema legado

- El formulario Thymeleaf de vacaciones carga los festivos/cierres desde el calendario de la empresa.
- Se eliminaron las validaciones de fecha hardcodeadas del controlador legado; la autoridad es el backend/calendario.
- El `VacationCalculator` legado queda solo como fallback para usuarios sin empleado/empresa.

---

## 2. Endpoints

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/calendar/days?from=&to=&companyUuid=` | `CALENDAR_VIEW` |
| GET | `/api/v1/calendar/events?from=&to=&companyUuid=` | `CALENDAR_VIEW` |
| GET | `/api/v1/calendar/holidays?from=&to=&companyUuid=` | `CALENDAR_VIEW` |
| GET | `/api/v1/calendar/special-days?from=&to=&companyUuid=` | `CALENDAR_VIEW` |
| GET | `/api/v1/calendar/config?companyUuid=` | `CALENDAR_VIEW` |
| PUT | `/api/v1/calendar/config?companyUuid=` | `CALENDAR_MANAGE` |
| POST | `/api/v1/calendar/holidays` | `CALENDAR_MANAGE` |
| PUT | `/api/v1/calendar/holidays/{uuid}` | `CALENDAR_MANAGE` |
| DELETE | `/api/v1/calendar/holidays/{uuid}` | `CALENDAR_MANAGE` |
| POST | `/api/v1/calendar/special-days` | `CALENDAR_MANAGE` |
| PUT | `/api/v1/calendar/special-days/{uuid}` | `CALENDAR_MANAGE` |
| DELETE | `/api/v1/calendar/special-days/{uuid}` | `CALENDAR_MANAGE` |
| POST | `/api/v1/calendar/official-holidays/{year}` | `CALENDAR_MANAGE` |

Reglas de acceso:

- Empleado/líder consulta el calendario de su propia empresa.
- RRHH/Admin puede consultar y administrar otras empresas con `CALENDAR_MANAGE`.
- Los festivos oficiales son globales (`company_id = NULL`).
- Los festivos de empresa requieren `companyUuid`.
- Soft delete en festivos y días especiales.

---

## 3. Ejemplos

Días del mes:

```bash
curl -s "http://localhost:8443/api/v1/calendar/days?from=2026-08-01&to=2026-08-31" \
  -H "Authorization: Bearer $TOKEN"
```

Eventos (festivos + cierres + vacaciones):

```bash
curl -s "http://localhost:8443/api/v1/calendar/events?from=2026-08-01&to=2026-08-31" \
  -H "Authorization: Bearer $TOKEN"
```

Crear festivo de empresa:

```bash
curl -X POST http://localhost:8443/api/v1/calendar/holidays \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "companyUuid": "UUID_EMPRESA",
    "name": "Cierre corporativo de fin de año",
    "date": "2026-12-31",
    "type": "COMPANY",
    "description": "Cierre administrativo"
  }'
```

Crear cierre:

```bash
curl -X POST http://localhost:8443/api/v1/calendar/special-days \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "companyUuid": "UUID_EMPRESA",
    "name": "Cierre de fin de año",
    "startDate": "2026-12-24",
    "endDate": "2026-12-31",
    "type": "CLOSURE",
    "affectsWorkdays": true
  }'
```

Generar festivos oficiales de un año:

```bash
curl -X POST http://localhost:8443/api/v1/calendar/official-holidays/2031 \
  -H "Authorization: Bearer $TOKEN"
```

---

## 4. Validación

```bash
mvn clean verify
```

Resultado esperado: `BUILD SUCCESS`, 170 tests, cobertura de código nuevo ≈ 76.7%.

SQL en copia:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;

SELECT COUNT(*) FROM cal_calendar;
SELECT COUNT(*) FROM cal_holiday WHERE company_id IS NULL AND type = 'OFFICIAL';
SELECT COUNT(*) FROM cal_holiday WHERE company_id IS NOT NULL;
SELECT COUNT(*) FROM cal_special_day;

-- Calendario por empresa
SELECT c.code, cal.workweek, cal.timezone
FROM org_company c
LEFT JOIN cal_calendar cal ON cal.company_id = c.id AND cal.deleted_at IS NULL;
```

Flujo funcional:

1. `GET /api/v1/calendar/days` para una semana.
2. Verificar que sábado/domingo y festivos aparezcan como no laborables.
3. Crear un festivo de empresa y repetir la consulta.
4. Crear un cierre con `affectsWorkdays=true` y verificar que el rango quede no laborable.
5. Solicitar vacaciones en un rango con festivo/cierre; el backend debe contar solo días hábiles.
6. Consultar `/events` y verificar festivos, cierres y vacaciones aprobadas.

---

## 5. Pendientes y límites conocidos

| Tema | Estado |
|---|---|
| `V1`/`V7` contra SQL Server real | No ejecutado en este entorno; validar en copia antes de prod |
| Festivos oficiales después de 2030 | Generables con `POST /official-holidays/{year}`; no hay job automático aún |
| UI de calendario nuevo | Solo API; el calendario legacy sigue mostrando vacaciones |
| Turnos/jornadas por empleado | No implementado; el calendario es por empresa |
| Excepciones por sucursal/departamento | No implementado; el calendario es por empresa |
| Zona horaria | Se almacena; el cálculo usa la fecha del servidor |
| Días festivos oficiales estatales/municipales | No incluidos; se pueden agregar como festivos de empresa |
| Eventos de calendario en Thymeleaf | El admin legacy no consume aún `/api/v1/calendar/events` |
| Sincronización con Google Calendar/Outlook | Fuera de alcance |

---

## 6. Siguiente bloque recomendado

**Fase 7 — Documents:** tipos de documento, carga/descarga autorizada, metadata, hash, almacenamiento abstracto y auditoría de acceso. Después, Payroll (recibos) y Communication/Notifications.

Antes de producción: validar V1–V7 en copia, revisar festivos oficiales con RRHH/Legal, rotar credenciales y ejecutar en ventana controlada con respaldo verificado.
