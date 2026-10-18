# Bloque 9 — Menú ejecutivo, perfil y navegación de regreso

## Objetivo

- Reemplazar el menú saturado por una navegación ejecutiva y responsiva.
- Hacer visible el perfil del usuario como sección principal.
- Permitir que el usuario actualice sus datos de contacto.
- Hacer que todos los botones “Volver” regresen a la pantalla anterior.

## Menú ejecutivo

### Escritorio

- Barra superior fija con:
  - marca INSSOFT PEOPLE;
  - navegación principal: Inicio, Mi perfil, Vacaciones, Documentos, Recibos y Comunicados;
  - accesos de liderazgo: Mi equipo y Bandeja;
  - menú desplegable **Administración** para RH/Admin con Empleados, Documentos, Nómina, Comunicados, Solicitudes, Historial, Usuarios, Calendario, Políticas y Dashboard;
  - tarjeta de usuario con inicial, nombre y rol;
  - acceso directo a cambio de contraseña y cierre de sesión.

### Móvil

- Botón hamburguesa.
- Drawer lateral con scrim, botón de cierre y todos los accesos agrupados.
- Dropdown de administración adaptado a acordeón.
- Bloqueo del scroll del fondo mientras el menú está abierto.
- Cierre con `Esc`, con el scrim o al seleccionar una opción.

### Implementación

- Fragmento compartido: `templates/fragments/people-nav.html`.
- Comportamiento sin depender de Bootstrap JS: `global-loader.js`.
- Estilos: `static/css/people.css`.
- Aplicado a home, pantallas nuevas y pantallas heredadas.

## Perfil

La sección `/profile` ahora incluye:

- Cabecera con nombre, puesto, departamento, número de empleado y estatus.
- Tarjeta **Mi cuenta** con usuario, rol, correo corporativo, número de empleado y tema.
- Datos personales y laborales.
- Contactos de emergencia con alta y baja.
- Formulario **Actualizar datos de contacto** para:
  - correo personal;
  - teléfono;
  - dirección;
  - ciudad, estado, código postal y país.
- Acceso a cambio de contraseña.

### API

| Método | Ruta | Permiso |
|---|---|---|
| PUT | `/api/v1/employees/me/profile` | `EMPLOYEE_VIEW_SELF` |

El perfil propio permite editar únicamente datos de contacto; los datos laborales y organizacionales siguen siendo administrados por RH.

## Botones “Volver”

- Todos los enlaces de regreso usan `data-back`.
- `global-loader.js` intercepta el clic:
  - si existe historial, ejecuta `history.back()`;
  - si el acceso fue directo, usa el `href` como respaldo.
- Se actualizaron home, vacaciones, calendario, dashboard, solicitudes, historial, usuarios, políticas y expedientes.

## Recorrido

- Los pasos administrativos ahora apuntan a tarjetas visibles del home mediante `data-tour`, no a enlaces ocultos del dropdown.
- El menú de pasos, la barra de progreso y los estilos claro/oscuro se mantienen.

## Verificación

- `mvn verify`: **212 pruebas, 0 fallos**.
- Navegación `200` con admin, líder y colaborador en:
  - home, perfil, documentos, recibos, comunicados;
  - empleados, documentos admin, nómina, comunicados admin;
  - solicitudes, historial, usuarios, calendario, políticas y dashboard;
  - equipo y bandeja del líder.
- Actualización de perfil probada desde la UI y restaurada después.
- Datos de prueba eliminados.
