# Bloque 3 — Vacations (ledger, periodos y workflow)

| Campo | Valor |
|---|---|
| Rama | `feature/vacation-module` (base `develop`) |
| Alcance | Fase 5 del roadmap |
| Fecha | 2026-06-10 |
| Estado | Implementado, compilado y probado (`mvn clean verify`) |
| Pruebas | 151 tests, 0 fallos |
| Cobertura código nuevo | 78.4% instrucciones (JaCoCo) |

---

## 1. Qué se implementó

### 1.1 Migración `V6__vacations.sql`

- `vac_policy` y `vac_policy_rule`: política por empresa y reglas por rango de antigüedad.
- `vac_period`: periodos por aniversario.
- `vac_ledger`: movimientos firmados de saldo.
- `vac_request_event`: historial de workflow por solicitud.
- Extensión aditiva de `vacation_requests`:
  - `uuid`, `employee_id`, `period_id`, `requested_by`
  - `cancelled_at`, `cancel_reason`, `updated_at`, `deleted_at`, `version`
  - `legacy_balance`
  - `user_id` ahora nullable (empleados sin usuario vinculado)
- Política default para **cada empresa activa** con la escala base:
  - 1 año: 12, 2: 14, 3: 16, 4: 18, 5: 20
  - 6–10: 22, 11–15: 24, 16–20: 26, 21–25: 28, 26–30: 30, …
- Periodo vigente por empleado con:
  - `opening_balance` = saldo legado de `users.vacation_days_available`
  - `entitled_days` = regla de política según años cumplidos
  - movimiento `OPENING_BALANCE` en el ledger
- Solicitudes históricas:
  - Se marcan `legacy_balance = 1`
  - **No** generan movimientos de ledger (su saldo ya está reflejado en el opening balance)
  - Si se rechazan/cancelan después de la migración, se devuelven con un `ADJUSTMENT` referenciado a la solicitud.

### 1.2 Ledger de vacaciones

Cada movimiento tiene `days` (positivo) y `signed_days` (impacto firmado en el saldo):

| Tipo | signed_days | Efecto |
|---|---|---|
| `OPENING_BALANCE` | +d | Saldo inicial migrado |
| `ACCRUAL` | +d | Días generados por periodo |
| `HOLD` | -d | Retención al enviar solicitud |
| `RELEASE` | +d | Liberación por rechazo/cancelación o paso a consumo |
| `USAGE` | -d | Consumo al aprobar |
| `USAGE_REVERSAL` | +d | Reverso por cancelación de una aprobada |
| `ADJUSTMENT` | ±d | Ajuste administrativo o liberación legada |
| `EXPIRY` | -d | Reservado para caducidad (fase posterior) |

Cálculos:

```
available = SUM(signed_days)
used      = SUM(USAGE) - SUM(USAGE_REVERSAL)
held      = SUM(HOLD) - SUM(RELEASE)
```

Aprobación: `RELEASE (+d)` + `USAGE (-d)` → el saldo disponible no cambia, pero la retención se convierte en consumo.
Rechazo/cancelación pendiente: `RELEASE (+d)` → el saldo se restaura.
Cancelación de aprobada no iniciada: `USAGE_REVERSAL (+d)` → el saldo se restaura.

### 1.3 Workflow

Estados: `PENDING_LEADER → PENDING_ADMIN → APPROVED / REJECTED`, más `CANCELLED`.

- `submit`: valida fechas/días hábiles, traslape, saldo, líder; bloquea el periodo con lock pesimista; crea solicitud + `HOLD` + evento.
- `approveByLeader`: solo el líder asignado; pasa a `PENDING_ADMIN`.
- `rejectByLeader`: `REJECTED` + liberación de retención.
- `approveByAdmin`: solo etapa administrativa; `APPROVED` + `RELEASE` + `USAGE`.
- `rejectByAdmin`: `REJECTED` + liberación.
- `cancel`: dueño o administración/RRHH; pendiente libera retención; aprobada no iniciada revierte consumo.
- Prohibición de auto-aprobación/auto-rechazo administrativo.
- Eventos y auditoría en cada transición.

### 1.4 Integración con el sistema legado

- `VacationController` (Thymeleaf) ahora usa el workflow/ledger; ya no descuenta directamente `users.vacation_days_available`.
- `LeaderController` y `AdminVacationController` delegan en el workflow.
- `users.vacation_days_available` queda como **caché de compatibilidad**, sincronizado desde el ledger.
- `AdminUserController` convierte cambios de saldo en `ADJUSTMENT` cuando el usuario tiene empleado.
- Se eliminó el job legado `AnniversaryDays`; lo reemplaza `VacationAccrualJob`.
- El cálculo de días hábiles usa un adaptador al `VacationCalculator` legado (fines de semana + festivos MX). Se reemplaza por Calendar en la Fase 6.

### 1.5 Job de acumulación

`VacationAccrualJob` (diario 02:20 América/Mexico_City):

- Recorre empleados activos.
- Garantiza el periodo vigente por aniversario.
- Crea `ACCRUAL` solo si el periodo no existe (idempotente).
- Sincroniza la caché legada.
- Continúa ante errores individuales.

---

## 2. Endpoints

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/vacations/balance` | `VACATION_VIEW_SELF` |
| GET | `/api/v1/vacations/periods` | `VACATION_VIEW_SELF` |
| GET | `/api/v1/vacations/ledger` | `VACATION_VIEW_SELF` |
| GET | `/api/v1/vacations` | `VACATION_VIEW_SELF` |
| POST | `/api/v1/vacations` | `VACATION_CREATE` |
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
| GET | `/api/v1/vacations/policies/{uuid}` | `VACATION_POLICY_MANAGE` / `VACATION_VIEW` |

Notas de seguridad:

- Un líder solo puede aprobar/rechazar solicitudes asignadas a él.
- La etapa administrativa requiere `VACATION_ADJUST` (RRHH/Admin), no solo `VACATION_APPROVE`.
- Un líder solo puede crear solicitudes para sus colaboradores directos.
- Búsqueda administrativa y saldos de otros empleados requieren `VACATION_ADJUST`.
- El dueño o un usuario con `VACATION_ADJUST` puede cancelar.

---

## 3. Ejemplos

Saldo:

```bash
curl -s http://localhost:8443/api/v1/vacations/balance \
  -H "Authorization: Bearer $TOKEN"
```

Crear solicitud propia:

```bash
curl -X POST http://localhost:8443/api/v1/vacations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "startDate": "2026-08-03",
    "endDate": "2026-08-07"
  }'
```

Aprobar/rechazar:

```bash
curl -X POST http://localhost:8443/api/v1/vacations/$UUID/approve \
  -H "Authorization: Bearer $TOKEN"

curl -X POST http://localhost:8443/api/v1/vacations/$UUID/reject \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"comment":"Sin cobertura operativa"}'
```

Ajuste administrativo:

```bash
curl -X POST http://localhost:8443/api/v1/vacations/employees/$EMPLOYEE_UUID/adjust \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"available": 15, "reason": "Ajuste autorizado por RRHH"}'
```

---

## 4. Validación

```bash
mvn clean verify
```

Resultado esperado: `BUILD SUCCESS`, 151 tests, cobertura de código nuevo ≈ 77%.

SQL de verificación en copia:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;

SELECT COUNT(*) FROM vac_policy;
SELECT COUNT(*) FROM vac_policy_rule;
SELECT COUNT(*) FROM vac_period;
SELECT COUNT(*) FROM vac_ledger;

-- Solicitudes históricas sin ledger
SELECT COUNT(*) FROM vacation_requests WHERE legacy_balance = 1;

-- Empleados con periodo vigente
SELECT COUNT(*) FROM vac_period p
WHERE p.enabled = 1 AND p.period_start <= CAST(SYSUTCDATETIME() AS DATE)
  AND p.period_end >= CAST(SYSUTCDATETIME() AS DATE);

-- Conciliación de saldo por periodo
SELECT p.id, p.employee_id,
       p.opening_balance,
       COALESCE(SUM(l.signed_days), 0) AS ledger_balance
FROM vac_period p
LEFT JOIN vac_ledger l ON l.period_id = p.id
GROUP BY p.id, p.employee_id, p.opening_balance
ORDER BY p.id;
```

Flujo funcional:

1. Login API con un usuario con empleado vinculado.
2. `GET /api/v1/vacations/balance` → saldo.
3. Crear solicitud → validar `HOLD` en `vac_ledger` y saldo reducido.
4. Aprobar como líder → estado `PENDING_ADMIN`, sin cambio de saldo.
5. Aprobar como RRHH/Admin → `APPROVED`, `RELEASE` + `USAGE`, saldo disponible igual.
6. Rechazar una solicitud → `REJECTED`, `RELEASE`, saldo restaurado.
7. Cancelar una aprobada futura → `CANCELLED`, `USAGE_REVERSAL`, saldo restaurado.

---

## 5. Pendientes y límites conocidos

| Tema | Estado |
|---|---|
| `V1`/`V6` contra SQL Server real | No ejecutado en este entorno; validar en copia antes de prod |
| Arrastre de días entre periodos (carryover) | No implementado; el periodo nuevo genera solo su acumulación |
| Caducidad de días (`expiry_months`) | Campo y tipo `EXPIRY` reservados; ejecución pendiente |
| CRUD de políticas | API de lectura; administración de reglas pendiente |
| Calendario laboral configurable | Sigue usando el `VacationCalculator` legado (Fase 6) |
| Frontend de vacaciones | Solo API; Thymeleaf usa el workflow |
| Correos fuera de la transacción | Pendiente de outbox (Fase 9) |
| Solicitudes legadas rechazadas antes de V6 | No se corrigen retroactivamente (decisión de preservar saldos) |
| Auto-aprobación | Bloqueada en etapa administrativa |
| Concurrencia en creación inicial de periodo | Lock de periodo + constraint único; un primer acceso concurrente puede requerir reintento |
| Saldo en `users` | Columna legada mantenida como caché de compatibilidad |

---

## 6. Siguiente bloque recomendado

**Fase 6 — Calendar:** calendario laboral por empresa, festivos oficiales/empresa, días especiales/cierres, y reemplazo del adaptador legado de días hábiles por un `WorkingDayCalculator` respaldado por base de datos.

Antes de producción: validar V1/V6 en copia, rotar credenciales, revisar periodos/saldos migrados con RRHH y ejecutar en ventana controlada con respaldo verificado.
