# Guía rápida — Levantar INSSOFT PEOPLE en Eclipse

> Rama actual de desarrollo: `feature/documents-module`
> Para una base local desde cero, Flyway crea el esquema V1–V9 al arrancar.
> **No uses `ddl-auto=update`**. El proyecto ya está configurado con `ddl-auto=none` (local) y `validate` (prod).

---

## 1. Requisitos

1. **JDK 17** instalado.
2. **Eclipse IDE for Enterprise Java and Web Developers** (o Spring Tools 4).
3. **Maven 3.9+** (Eclipse puede usar el embebido, pero se recomienda el externo).
4. **SQL Server** accesible (local o remoto).
5. **Git** (el repositorio ya está clonado en `D:\Proyectos\vacaciones`).

Verifica en una terminal:

```bash
java -version
mvn -version
```

Debe aparecer Java 17 y Maven 3.9+.

---

## 2. Importar el proyecto en Eclipse

1. `File → Import…`
2. `Maven → Existing Maven Projects`
3. Selecciona la carpeta raíz:
   `D:\Proyectos\vacaciones`
4. Eclipse detecta `pom.xml` → `Finish`.
5. Clic derecho sobre el proyecto → `Maven → Update Project…`
   - Marca **Force Update of Snapshots/Releases**.
   - `OK`.

### Configurar Java 17

1. `Window → Preferences → Java → Installed JREs`
   - Agrega el JDK 17 (`C:\Program Files\Java\jdk-17`).
   - Márcalo como default.
2. Clic derecho en el proyecto → `Properties`:
   - `Java Build Path → Libraries`: JRE System Library = JDK 17.
   - `Java Compiler`: Compiler compliance level = **17**.
   - `Project Facets`: Java = 17.

### Lombok (importante en Eclipse)

El proyecto usa Lombok. Maven compila bien, pero el editor de Eclipse puede marcar errores en getters/setters si Lombok no está instalado:

1. Descarga `lombok.jar` desde `https://projectlombok.org/download`.
2. Ejecuta:
   ```bash
   java -jar lombok.jar
   ```
3. Selecciona tu instalación de Eclipse (`eclipse.exe`) y pulsa **Install/Update**.
4. Reinicia Eclipse.
5. Si sigue fallando: `Project → Clean…`.

---

## 3. Crear la base local

En SQL Server crea una base vacía, por ejemplo:

```sql
CREATE DATABASE VACACIONES_DEV;
```

> Si ya tienes una copia de la base real, úsala en lugar de crearla.
> En ese caso Flyway hará baseline en V1 y aplicará V2–V9; **haz respaldo antes**.

---

## 4. Crear `application-local.properties`

Copia el ejemplo:

```bash
copy src\main\resources\application-local.properties.example ^
     src\main\resources\application-local.properties
```

Edita `src/main/resources/application-local.properties`:

```properties
DB_URL=jdbc:sqlserver://localhost:1433;databaseName=VACACIONES_DEV;encrypt=true;trustServerCertificate=true
DB_USERNAME=sa
DB_PASSWORD=TuPasswordDeSQLServer

# Mínimo 32 caracteres. Ejemplo (cámbialo):
JWT_SECRET=InsssoftPeopleLocal2026SecretKey32Chars!!

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=

APP_SYSTEM_URL=http://localhost:8443/login
```

Notas:

- `application-local.properties` está en `.gitignore`. **No lo subas al repositorio.**
- Si no configuras correo, el login funciona; los envíos de correo fallarán y en la mayoría de flujos se registran como warning.
- Si el puerto 8443 está ocupado, agrega:
  ```properties
  SERVER_PORT=8080
  ```

### Alternativa: variables de entorno

En Eclipse puedes definirlas en la configuración de ejecución (`Environment`), sin tocar archivos:

- `DB_URL` (o el alias `BD_HOST`)
- `DB_USERNAME` (o el alias `BD_USER`)
- `DB_PASSWORD` (o el alias `BD_PASS`)
- `JWT_SECRET`
- `MAIL_*`
- `APP_SYSTEM_URL`
- `SERVER_PORT` (opcional)

La aplicación acepta ambos juegos de nombres. Si `DB_URL` no está definido, usa `BD_HOST`; lo mismo para usuario y contraseña.

> No agregues credenciales reales a `application-local.properties.example`; ese archivo sí está versionado. Usa variables de entorno o `application-local.properties`, que está ignorado por Git.

---

## 5. Configurar la ejecución en Eclipse

### Opción A — Spring Boot App (recomendado si tienes Spring Tools)

1. `Run → Run Configurations…`
2. `Spring Boot App → New`
3. Project: `vacaciones` (o `inssoft-people`)
4. Main type: `com.mx.vacaciones.VacacionesApplication`
5. En **VM arguments**:
   ```text
   -Dspring.profiles.active=local
   ```
6. En **Environment** (opcional): variables `DB_*`, `JWT_SECRET`, etc.
7. `Apply → Run`.

### Opción B — Java Application

1. `Run → Run Configurations…`
2. `Java Application → New`
3. Project: `vacaciones`
4. Main class:
   `com.mx.vacaciones.VacacionesApplication`
5. `Arguments → VM arguments`:
   ```text
   -Dspring.profiles.active=local
   ```
6. `Run`.

### Opción C — Maven desde terminal

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

---

## 6. Primer arranque y Flyway

Al iniciar, Flyway ejecuta:

```
V1__baseline_legacy.sql
V2__common_security_audit.sql
V3__organization.sql
V4__masterdata.sql
V5__employees.sql
V6__vacations.sql
V7__calendar.sql
V8__documents.sql
V9__onboarding.sql
```

Si la base está vacía, V1 crea el esquema legado mínimo y las demás migraciones lo evolucionan.

Si la base ya existía, Flyway hace baseline en V1 y aplica V2–V9.

### Si Flyway falla

- Revisa que `DB_URL`, usuario y contraseña sean correctos.
- Revisa que SQL Server acepte TCP/IP en el puerto configurado.
- Si la base no está vacía y el esquema no coincide con `V1`, **no borres datos**: restaura una copia y valida el DDL real con `docs/sql/00_introspection_sqlserver.sql`.
- Para desarrollo local puedes crear una base nueva y limpia.

---

## 7. Crear usuarios de prueba (seed local)

Con la aplicación ya arrancada al menos una vez (para que Flyway haya creado las tablas), ejecuta en SQL Server Management Studio:

```
docs/sql/01_seed_local_admin.sql
```

Crea:

| Usuario | Contraseña | Rol | Empleado |
|---|---|---|---|
| `admin` | `Admin1234` | ADMIN | ADMIN-001 |
| `lider` | `Lider1234` | LEADER | LIDER-001 |
| `colaborador` | `Colaborador1234` | EMPLOYEE | EMP-001 |

El colaborador tiene como jefe directo al líder; el líder tiene como jefe al admin.

El seed deja `onboarding_completed = 0` en los tres usuarios, por lo que al entrar verán el banner de bienvenida con la opción **Ver recorrido**.

> Este script es **solo para desarrollo**. No lo ejecutes en producción.

Si no ejecutas el seed y tu base ya tenía usuarios, usa esas credenciales existentes.

---

## 8. Abrir la aplicación

- Web: `http://localhost:8443/login`
- Si cambiaste el puerto: `http://localhost:8080/login`
- Swagger UI: `http://localhost:8443/swagger-ui.html`
  - Primero inicia sesión web en el mismo navegador.
  - Para probar APIs con JWT, usa `POST /api/v1/auth/login` y el botón **Authorize**.

### Login por API

```bash
curl -X POST http://localhost:8443/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin1234"}'
```

Guarda el `accessToken` y úsalo:

```bash
curl http://localhost:8443/api/v1/auth/me \
  -H "Authorization: Bearer TU_TOKEN"
```

### Recorrido inicial

1. Entra como `colaborador / Colaborador1234`.
2. Debe mostrarse el banner de bienvenida; pulsa **Ver recorrido**.
3. Prueba **Siguiente**, **Anterior**, **Omitir** y **Finalizar**.
4. Verifica en SQL:
   ```sql
   SELECT username, onboarding_completed FROM users WHERE username = 'colaborador';
   ```
5. Vuelve a entrar: ya no aparece el banner automáticamente.
6. Pulsa **🎯 Ver recorrido** en el home: debe abrirse de nuevo.
7. Repite con `lider` y `admin` para ver los pasos por rol.

---

## 9. Pruebas rápidas de la plataforma

### Empleado actual

```bash
curl http://localhost:8443/api/v1/employees/me \
  -H "Authorization: Bearer TU_TOKEN"
```

### Saldo de vacaciones

```bash
curl http://localhost:8443/api/v1/vacations/balance \
  -H "Authorization: Bearer TU_TOKEN"
```

### Calendario

```bash
curl "http://localhost:8443/api/v1/calendar/days?from=2026-08-01&to=2026-08-31" \
  -H "Authorization: Bearer TU_TOKEN"
```

### Cargar un documento

```bash
curl -X POST http://localhost:8443/api/v1/documents \
  -H "Authorization: Bearer TU_TOKEN" \
  -F "employeeUuid=UUID_DEL_EMPLEADO" \
  -F "typeCode=CONTRATO" \
  -F "name=Contrato de prueba" \
  -F "visibility=EMPLOYEE" \
  -F "file=@C:/ruta/contrato.pdf"
```

Para obtener el `employeeUuid`, consulta:

```bash
curl "http://localhost:8443/api/v1/employees?page=0&size=10" \
  -H "Authorization: Bearer TU_TOKEN"
```

---

## 10. Ejecutar pruebas desde Eclipse

### Maven

Clic derecho en `pom.xml` → `Run As → Maven build…`

Goals:

```text
clean verify
```

### JUnit

Clic derecho en `src/test/java` → `Run As → JUnit Test`.

O ejecuta una clase concreta, por ejemplo:

```
com.inssoft.people.documents.service.DocumentServiceTest
```

Desde terminal:

```bash
mvn clean verify
```

---

## 11. Problemas comunes

| Problema | Causa / solución |
|---|---|
| `Could not resolve placeholder 'DB_URL'` | Falta `application-local.properties` o no activaste el perfil `local`. |
| `JWT_SECRET es obligatorio` | Define `JWT_SECRET` con al menos 32 caracteres. |
| `Login failed for user` | Credenciales SQL Server incorrectas. |
| `The TCP/IP connection to the host has failed` | SQL Server no acepta TCP/IP o el puerto no es 1433. |
| `Port 8443 already in use` | Cambia `SERVER_PORT` o cierra el proceso anterior. |
| Flyway falla en V1 | La base no está vacía y su esquema no coincide; usa una base limpia o valida el DDL real. |
| Eclipse marca errores en `getX()`/`setX()` | Instala Lombok en Eclipse y reinicia. |
| `mvn` usa Java equivocado | Configura `JAVA_HOME` al JDK 17 o usa el JRE 17 en Eclipse. |
| Error de tamaño de archivo | Configura `spring.servlet.multipart.max-file-size` o reduce el archivo (máx. 20 MB por defecto). |
| Los correos no se envían | Configura `MAIL_USERNAME`/`MAIL_PASSWORD` o ignóralo en local; el login y las APIs funcionan. |

---

## 12. Checklist mínimo para probar localmente

- [ ] JDK 17 y Maven configurados.
- [ ] Proyecto importado como Maven y actualizado.
- [ ] Lombok instalado en Eclipse.
- [ ] Base `VACACIONES_DEV` creada.
- [ ] `application-local.properties` con DB y `JWT_SECRET`.
- [ ] Perfil `local` activo.
- [ ] Aplicación arranca y Flyway aplica V1–V9.
- [ ] Seed local ejecutado.
- [ ] Login `admin / Admin1234`.
- [ ] `/api/v1/employees/me`, `/api/v1/vacations/balance`, `/api/v1/calendar/days` responden.
- [ ] El usuario nuevo ve el recorrido inicial y puede repetirlo con «Ver recorrido».
- [ ] Carga/descarga de documento funciona.
- [ ] `mvn clean verify` en verde (193 tests).
