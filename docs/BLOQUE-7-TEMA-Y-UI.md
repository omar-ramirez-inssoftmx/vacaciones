# Bloque 7 — Tema claro/oscuro y ajuste del recorrido

## Objetivo

- Dejar **un solo botón de recorrido** visible.
- Integrar el recorrido y la bienvenida con el diseño existente.
- Permitir que cada usuario elija **modo claro u oscuro**, con persistencia en su cuenta.

## Cambios funcionales

### 1. Un solo botón de recorrido

Antes se mostraban dos acciones con el mismo propósito:

- El botón del hero: `#startTourButton`.
- El botón del banner de bienvenida: `[data-start-tour]`.

Ahora:

- Usuario **nuevo** (`onboarding_completed = 0`): ve el banner con **“Ver recorrido”** y **“Omitir”**. El botón del hero no se renderiza.
- Usuario que **ya completó** el recorrido: ve únicamente el botón **“Ver recorrido”** del hero. El banner no se renderiza.

Resultado: nunca hay dos botones de recorrido al mismo tiempo.

### 2. Estilos integrados

- El banner de bienvenida y la tarjeta del tour usan la paleta del sistema:
  - Oscuro: `#161b22`, `#1a2332`, texto `#e6edf3`, azul `#1f6feb`.
  - Claro: blanco, bordes `#e2e8f0`, azul `#2563eb`.
- Se conservan las clases existentes (`home-card`, `shadow-soft`, etc.) para no romper el diseño actual.

### 3. Tema por usuario

- Migración `V10__theme_preference.sql`:
  - Agrega `users.theme_preference` (`LIGHT` / `DARK`, default `DARK`).
  - Constraint `CK_users_theme_preference`.
- El tema se aplica con `data-theme` en `<html>`.
- Prioridad:
  1. Preferencia del servidor (`data-theme` renderizado desde la cuenta).
  2. Preferencia local (`localStorage`, por navegador).
  3. `DARK` (diseño base actual).
- El botón flotante **“Modo claro / Modo oscuro”** aparece en todas las pantallas y guarda la preferencia.
- Al iniciar sesión, el home renderiza el tema guardado en la cuenta.

## Endpoints

| Método | Ruta | Uso |
|---|---|---|
| GET | `/profile/theme` | Cliente web (sesión) |
| POST | `/profile/theme` | Cliente web (sesión + CSRF) |
| GET | `/api/v1/profile/theme` | Cliente API (JWT) |
| PUT | `/api/v1/profile/theme` | Cliente API (JWT) |

Body:

```json
{ "theme": "LIGHT" }
```

Valores permitidos: `LIGHT`, `DARK` (se normalizan a mayúsculas).

## Archivos principales

- `src/main/resources/db/migration/V10__theme_preference.sql`
- `com.inssoft.people.profile.service.UserPreferenceService`
- `com.inssoft.people.profile.controller.ProfilePreferenceController`
- `com.inssoft.people.profile.controller.ProfileApiController`
- `src/main/resources/static/css/theme-light.css`
- `src/main/resources/static/js/theme-init.js`
- `src/main/resources/static/js/global-loader.js`
- `src/main/resources/static/css/onboarding-tour.css`
- `src/main/resources/templates/home.html`

## Corrección de CSRF para fetch

El cliente `fetch` envía el valor crudo de la cookie `XSRF-TOKEN` en `X-XSRF-TOKEN`.
Para que Spring lo acepte se configuró `CsrfTokenRequestAttributeHandler` en la cadena web.
Esto mantiene CSRF activo y permite guardar el tema sin recargar la página.

## Pruebas

```bash
mvn test
```

- 203 pruebas, 0 fallos.
- Verificación manual con los tres usuarios demo:
  - login `302`, home `200`;
  - un solo `data-start-tour`;
  - “Acciones rápidas” visible;
  - cambio `DARK → LIGHT → DARK` persistido por web y por API;
  - `flyway_schema_history` con V1–V10 en `success = 1`.
