# Bloque 6 — Onboarding: recorrido para usuarios nuevos

| Campo | Valor |
|---|---|
| Rama | `feature/onboarding-tour` |
| Alcance | Mejora transversal de experiencia de usuario |
| Fecha | 2026-06-10 |
| Estado | Implementado y probado (`mvn clean verify`) |
| Pruebas | 193 tests, 0 fallos |
| Cobertura código nuevo | 76.1% instrucciones (JaCoCo) |

---

## 1. Comportamiento

- Todo usuario nuevo (`onboarding_completed = 0`) ve automáticamente un recorrido guiado al entrar a `/home`.
- El recorrido se puede **omitir**, avanzar, retroceder o finalizar.
- Al omitir/finalizar, se marca como completado en base de datos.
- El botón **«Ver recorrido»** queda siempre visible en el home para repetirlo.
- Los **usuarios existentes** se marcan como completados en la migración V9 para no interrumpir su operación.
- Los usuarios creados después de V9 nacen con `onboarding_completed = 0` y verán el recorrido.
- El seed local fuerza `onboarding_completed = 0` en `admin`, `lider` y `colaborador` para pruebas.

---

## 2. Pasos por rol

### Empleado

1. Panel principal.
2. Solicitar vacaciones.
3. Consultar estatus de solicitudes.
4. Políticas de la empresa.
5. Repetir recorrido.

### Líder

Incluye los pasos de empleado más:

- Revisar solicitudes del equipo.
- Historial.

### Administrador

1. Panel principal.
2. Solicitar vacaciones.
3. Mis vacaciones.
4. Revisar solicitudes.
5. Administración de usuarios.
6. Historial.
7. Calendario.
8. Políticas de la empresa.
9. Dashboard KPI.
10. Repetir recorrido.

Los pasos apuntan a los elementos reales del menú (`a[href="..."]`) y el frontend resalta el elemento correspondiente.

---

## 3. Backend

### Migración `V9__onboarding.sql`

```sql
ALTER TABLE users ADD onboarding_completed BIT NOT NULL DEFAULT 0;
UPDATE users SET onboarding_completed = 1; -- usuarios existentes
```

- Usuarios existentes: completado.
- Usuarios nuevos: pendiente.

### API

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/onboarding` | Estado y pasos del recorrido para el usuario autenticado |
| POST | `/api/v1/onboarding/complete` | Marca el recorrido como completado |

### Web legado

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/onboarding/complete` | Marca el recorrido como completado desde el home Thymeleaf |

El POST web está protegido por CSRF; el JavaScript global agrega `X-XSRF-TOKEN`.

---

## 4. Frontend

Archivos:

- `src/main/resources/static/js/onboarding-tour.js`
- `src/main/resources/static/css/onboarding-tour.css`

Funciones:

- Overlay/spotlight sobre el elemento actual.
- Tarjeta con paso, título, descripción y acciones.
- Navegación con botones, flechas y `Esc`.
- Auto-inicio si `onboardingCompleted = false`.
- Botón permanente `#startTourButton`.
- Marcado de completado vía `POST /onboarding/complete`.

---

## 5. Pruebas

```bash
mvn clean verify
```

Resultado esperado: `BUILD SUCCESS`, 193 tests.

Prueba manual en local:

1. Ejecutar la app con perfil `local`.
2. Ejecutar `docs/sql/01_seed_local_admin.sql`.
3. Entrar como `colaborador / Colaborador1234`.
4. El recorrido debe abrirse automáticamente.
5. Recorrerlo, omitirlo o finalizarlo.
6. Verificar `users.onboarding_completed = 1` para `colaborador`.
7. Cerrar sesión y volver a entrar: ya no debe abrirse automáticamente.
8. Pulsar **Ver recorrido**: debe abrirse de nuevo.
9. Repetir con `lider` y `admin` para validar los pasos por rol.

SQL de validación:

```sql
SELECT username, role, onboarding_completed
FROM users
WHERE username IN ('admin', 'lider', 'colaborador');
```

---

## 6. Límites conocidos

- El recorrido actual cubre las pantallas Thymeleaf existentes; cuando se agreguen Employees/Documents/Payroll en el frontend, se deben agregar sus pasos.
- No se incluye un endpoint de “reset” para el usuario; el botón «Ver recorrido» permite repetirlo sin cambiar el flag.
- La ejecución end-to-end contra SQL Server no se realizó en este entorno porque no hay una base desechable ni permisos de `CREATE DATABASE`/`BACKUP`; no se modificó la base `vacaciones`.
- La prueba de base de datos debe hacerse en una copia o base de desarrollo con respaldo.

---

## 7. Siguiente bloque recomendado

Continuar con **Fase 8 — Payroll** y, en paralelo, agregar pasos de onboarding para los nuevos módulos cuando tengan interfaz web.
