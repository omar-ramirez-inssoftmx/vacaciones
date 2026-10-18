# Bloque 2 — Employees + RBAC de base de datos

| Campo | Valor |
|---|---|
| Rama | `feature/employee-module` (base `develop`) |
| Alcance | Fase 4 del roadmap |
| Fecha | 2026-06-10 |
| Estado | Implementado, compilado y probado (`mvn clean verify`) |
| Pruebas | 100 tests, 0 fallos |
| Cobertura código nuevo | 80.2% instrucciones (JaCoCo) |

---

## 1. Qué se implementó

### 1.1 Migración `V5__employees.sql`

- Tabla `emp_employee` con:
  - Identidad: `user_id`, `employee_number`, nombres, fecha de nacimiento, género, estado civil.
  - Datos laborales: empresa, sucursal, departamento, puesto, jefe directo, fecha de ingreso, fecha de baja, estatus, modalidad.
  - Datos personales: CURP, RFC, NSS, correos, teléfono, dirección.
  - Campos comunes: UUID, enabled, createdAt, updatedAt, version, deletedAt.
- Tabla `emp_emergency_contact` con contacto primario único por empleado.
- Índices únicos filtrados para:
  - `(company_id, employee_number)`
  - `user_id`, `corporate_email`, `curp`, `rfc`, `nss`
  - contacto primario por empleado
- Checks de fechas: baja ≥ ingreso y nacimiento < ingreso.
- FK `users.employee_id → emp_employee.id`.
- Permiso `EMPLOYEE_VIEW_SENSITIVE` mapeado a ADMIN, SUPER_ADMIN, HR y EMPLOYEE.
- Organización mínima para migrar usuarios existentes:
  - Empresa `INSSOFT`
  - Sucursal `MATRIZ`
  - Departamento `GENERAL`
  - Puesto `GENERAL`
- Migración idempotente de `users` a `emp_employee`:
  - `employee_number = LEG-<id>`
  - Nombre dividido en primera palabra (nombre) y resto (apellido); si no hay apellido, `SIN_APELLIDO`.
  - `hire_date` tomada de `users.hire_date` (fallback a hoy).
  - Estatus `ACTIVO`, modalidad `PRESENCIAL`.
  - `users.employee_id` actualizado.
- **El administrador debe revisar/corregir** los datos migrados (nombres, correo corporativo, organización).

### 1.2 RBAC desde base de datos

- `UserPermissionService` carga permisos efectivos con `JdbcTemplate`:
  `sec_user_role → sec_role_permission → sec_permission`.
- `CustomUserDetailsService` agrega autoridades `ROLE_*` + `PERM_*`.
- `AccessGuard` usa los permisos de BD como fuente autoritativa; si no hay permisos de BD, cae al mapa estático de respaldo (compatibilidad pre-migración).
- Los JWT incluyen los permisos, por lo que `/api/v1` también queda cubierto.
- Fallo al leer RBAC no rompe el login: se registra warning y se usa el fallback.

### 1.3 Módulo Employee

- `Employee` y `EmergencyContact` (JPA).
- Repositorios con búsqueda paginada y filtros.
- DTOs:
  - `EmployeeRequest`, `EmployeeResponse`, `EmployeeSummaryResponse`
  - `EmergencyContactRequest`, `EmergencyContactResponse`
  - `TerminationRequest`
- Mapper con:
  - Nombre completo calculado.
  - Enmascarado de CURP/RFC/NSS cuando el usuario no tiene `EMPLOYEE_VIEW_SENSITIVE`.
- Validaciones:
  - Bean Validation para campos obligatorios, longitudes, correos, teléfono, código postal.
  - Anotaciones propias `@Curp`, `@Rfc`, `@Nss`.
  - `EmployeeValidator`: fechas, jerarquía organización, jefe directo (misma empresa, sin auto-jefe, sin ciclos).
  - Unicidad de número de empleado, CURP, RFC, NSS y correo corporativo.
  - Consistencia estatus `BAJA` ↔ fecha de baja.
- Reglas:
  - Vinculación/desvinculación de usuario (`userUuid`).
  - Baja laboral (`/terminate`) deshabilita al empleado y al usuario.
  - Reactivación vía `PUT` con estatus `ACTIVO` limpia fecha de baja y rehabilita usuario.
  - Borrado lógico solo si no tiene subordinados.
  - Contacto de emergencia primario único.
- Auditoría en creación, actualización, baja, borrado y contactos.

---

## 2. Endpoints

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/employees?companyUuid=&branchUuid=&departmentUuid=&positionUuid=&status=&workModality=&q=&enabled=&page=&size=` | `EMPLOYEE_VIEW` |
| GET | `/api/v1/employees/me` | `EMPLOYEE_VIEW_SELF` |
| GET | `/api/v1/employees/me/emergency-contacts` | `EMPLOYEE_VIEW_SELF` |
| GET | `/api/v1/employees/{uuid}` | `EMPLOYEE_VIEW` |
| POST | `/api/v1/employees` | `EMPLOYEE_CREATE` |
| PUT | `/api/v1/employees/{uuid}` | `EMPLOYEE_UPDATE` |
| POST | `/api/v1/employees/{uuid}/terminate` | `EMPLOYEE_TERMINATE` |
| DELETE | `/api/v1/employees/{uuid}` | `EMPLOYEE_TERMINATE` |
| GET | `/api/v1/employees/{uuid}/emergency-contacts` | `EMPLOYEE_VIEW` |
| POST | `/api/v1/employees/{uuid}/emergency-contacts` | `EMPLOYEE_UPDATE` |
| PUT | `/api/v1/employees/{uuid}/emergency-contacts/{contactUuid}` | `EMPLOYEE_UPDATE` |
| DELETE | `/api/v1/employees/{uuid}/emergency-contacts/{contactUuid}` | `EMPLOYEE_UPDATE` |

Notas:

- `GET /employees/{uuid}` enmascara CURP/RFC/NSS si el solicitante no tiene `EMPLOYEE_VIEW_SENSITIVE`.
- `GET /employees/me` devuelve los datos completos del propio empleado.
- Los listados usan `EmployeeSummaryResponse` (sin datos sensibles).

---

## 3. Ejemplo de creación

```bash
curl -X POST http://localhost:8443/api/v1/employees \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "companyUuid": "UUID_EMPRESA",
    "branchUuid": "UUID_SUCURSAL",
    "departmentUuid": "UUID_DEPARTAMENTO",
    "positionUuid": "UUID_PUESTO",
    "userUuid": "UUID_USUARIO_OPCIONAL",
    "employeeNumber": "EMP-001",
    "firstName": "Omar",
    "lastName": "Ramírez",
    "birthDate": "1990-01-01",
    "curp": "GARC850101HDFRRN09",
    "rfc": "GARC850101AB1",
    "nss": "12345678901",
    "corporateEmail": "omar@inssoftmx.com",
    "hireDate": "2020-01-01",
    "statusCode": "ACTIVO",
    "workModalityCode": "PRESENCIAL"
  }'
```

Baja:

```bash
curl -X POST http://localhost:8443/api/v1/employees/$UUID/terminate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"terminationDate":"2026-06-30","reason":"Renuncia voluntaria"}'
```

---

## 4. Validación

```bash
mvn clean verify
```

Resultado esperado: `BUILD SUCCESS`, 100 tests, cobertura de código nuevo ≈ 80%.

Validación en base (copia):

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT COUNT(*) FROM emp_employee;
SELECT COUNT(*) FROM emp_emergency_contact;
SELECT COUNT(*) FROM users WHERE employee_id IS NOT NULL;
SELECT COUNT(*) FROM emp_employee e
JOIN users u ON u.id = e.user_id
WHERE u.employee_id <> e.id; -- debe ser 0
```

Validación de permisos:

```sql
SELECT r.code, p.code
FROM sec_role r
JOIN sec_role_permission rp ON rp.role_id = r.id
JOIN sec_permission p ON p.id = rp.permission_id
WHERE r.code IN ('ADMIN','HR','LEADER','EMPLOYEE')
ORDER BY r.code, p.code;
```

---

## 5. Pendientes y límites conocidos

| Tema | Estado |
|---|---|
| `V1__baseline_legacy.sql` inferido | Validar contra copia real antes de producción |
| CURP/RFC con dígito verificador | Solo formato; checksum en fase de expedientes |
| Cifrado de PII en reposo | Pendiente (CURP/RFC/NSS/teléfono/dirección) |
| Enmascarado en listados | Implementado; detalle con permiso sensible |
| Desvincular usuario sin asignar otro | No hay endpoint explícito; se hace vía actualización de empleado/estado |
| Frontend de empleados | Solo API; pantallas Next.js/Thymeleaf pendientes |
| Migración de nombres | Automática y aproximada; requiere revisión de RRHH |
| Nómina, documentos, incidencias, teletrabajo, plan de trabajo | Fases 7–12 |
| Ledger de vacaciones | Fase 5 (siguiente bloque recomendado) |

---

## 6. Siguiente bloque recomendado

**Fase 5 — Vacations:** ledger de movimientos, periodos por aniversario, políticas parametrizables, máquina de estados (`PENDING_LEADER → PENDING_ADMIN → APPROVED/REJECTED/CANCELLED`), retención/consumo/liberación de saldo, cancelación y job de acumulación idempotente.

Antes de producción: validar `V1` con la base real, rotar credenciales y ejecutar las migraciones en ventana controlada con respaldo verificado.
