package com.mx.vacaciones.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.UserRepository;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Servicio encargado del envío de correos electrónicos del sistema de vacaciones.
 *
 * <p>
 * Este servicio centraliza todas las notificaciones por correo:
 * </p>
 * <ul>
 *   <li>Confirmación de solicitud de vacaciones registrada.</li>
 *   <li>Notificación a administradores sobre nuevas solicitudes.</li>
 *   <li>Notificación al usuario cuando su solicitud es aprobada o rechazada.</li>
 *   <li>Envío de credenciales de acceso a usuarios nuevos.</li>
 *   <li>Envío de nueva contraseña temporal por restablecimiento.</li>
 * </ul>
 *
 * <p>
 * Todos los correos se envían en formato HTML para permitir estilos visuales mejorados.
 * </p>
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /**
     * Remitente configurado para todos los correos del sistema.
     */
    private static final String FROM_EMAIL = "no-reply@inssoftmx.com";
    @Value("${app.system.url}")
    private String systemUrl;
    /**
     * Nombre del sistema mostrado en correos.
     */
    private static final String SYSTEM_NAME = "Sistema de Vacaciones";

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    /**
     * Constructor principal del servicio.
     *
     * @param mailSender componente de Spring para envío de correos
     * @param userRepository repositorio de usuarios
     */
    public EmailService(JavaMailSender mailSender, UserRepository userRepository) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
    }

    /**
     * Envía correo al usuario confirmando que su solicitud de vacaciones
     * fue registrada correctamente.
     *
     * @param to correo del destinatario
     * @param username nombre del usuario
     * @param startDate fecha de inicio
     * @param endDate fecha de fin
     * @param days número de días solicitados
     */
    public void sendVacationRequestEmail(
            String to,
            String name,
            String username,
            String startDate,
            String endDate,
            int days
    ) {
        String subject = "Solicitud de vacaciones registrada";

        String content =
            "<p>Hola <strong>" + safe(name) + "</strong>,</p>" +
            "<p>Tu solicitud de vacaciones fue registrada correctamente.</p>" +
            buildInfoTable(new String[][]{
                {"Inicio", safe(startDate)},
                {"Fin", safe(endDate)},
                {"Días solicitados", String.valueOf(days)},
                {"Estatus", "PENDIENTE"}
            }) +
            "<p>Tu solicitud será revisada por un administrador.</p>";

        sendHtmlEmail(to, subject, buildBaseTemplate("Solicitud registrada", content));
    }

    /**
     * Notifica a todos los administradores activos que se ha registrado
     * una nueva solicitud de vacaciones.
     *
     * @param request solicitud de vacaciones registrada
     */
    public void notifyAdminsVacationRequest(VacationRequest request) {
        List<User> admins = userRepository.findByRoleAndEnabledTrue(Role.ROLE_ADMIN);

        if (admins.isEmpty()) {
            return;
        }

        for (User admin : admins) {
            sendMailToAdmin(admin.getEmail(), request);
        }
    }

    /**
     * Envía correo individual a un administrador con el detalle
     * de la nueva solicitud de vacaciones.
     *
     * @param to correo del administrador
     * @param request solicitud registrada
     */
    private void sendMailToAdmin(String to, VacationRequest request) {
        if (request == null || request.getUser() == null) {
            return;
        }

        String subject = "Nueva solicitud de vacaciones";

        String content =
            "<p>Se ha registrado una nueva solicitud de vacaciones en el sistema.</p>" +
            buildInfoTable(new String[][]{
                {"Nombre Solicitante", safe(request.getUser().getName())},
                {"Usuario", safe(request.getUser().getUsername())},
                {"Correo", safe(request.getUser().getEmail())},
                {"Fecha inicio", String.valueOf(request.getStartDate())},
                {"Fecha fin", String.valueOf(request.getEndDate())},
                {"Días solicitados", String.valueOf(request.getDays())}
            }) +
            "<p>Ingresa al sistema para revisarla.</p>";

        sendHtmlEmail(to, subject, buildBaseTemplate("Nueva solicitud recibida", content));
    }

    /**
     * Notifica al usuario cuando su solicitud fue aprobada o rechazada.
     *
     * <p>
     * No se envía correo cuando el estado sigue siendo PENDING.
     * </p>
     *
     * @param request solicitud actualizada
     */
    public void notifyUserDecision(VacationRequest request) {
        if (request == null || request.getUser() == null || request.getUser().getEmail() == null) {
            return;
        }

        String to = request.getUser().getEmail();
        String status = request.getStatus();
        String subject;
        String title;
        String intro;

        if ("APPROVED".equals(status)) {
            subject = "Vacaciones aprobadas";
            title = "Solicitud aprobada";
            intro = "<p>Tu solicitud de vacaciones fue <strong style='color:#15803d;'>APROBADA</strong>.</p>";
        } else if ("REJECTED".equals(status)) {
            subject = "Vacaciones rechazadas";
            title = "Solicitud rechazada";
            intro = "<p>Tu solicitud de vacaciones fue <strong style='color:#b91c1c;'>RECHAZADA</strong>.</p>";
        } else {
            return;
        }

        String content =
            intro +
            buildInfoTable(new String[][]{
                {"Fecha inicio", String.valueOf(request.getStartDate())},
                {"Fecha fin", String.valueOf(request.getEndDate())},
                {"Días", String.valueOf(request.getDays())},
                {"Estatus", safe(status)}
            }) +
            "<p>Puedes consultar el detalle en el sistema.</p>";

        sendHtmlEmail(to, subject, buildBaseTemplate(title, content));
    }

    /**
     * Envía un correo de bienvenida a un usuario nuevo con sus credenciales
     * de acceso y la URL del sistema.
     *
     * @param name nombre de la persona
     * @param to correo del usuario
     * @param username nombre de usuario
     * @param rawPassword contraseña temporal o inicial en texto plano
     * @param systemUrl URL de acceso al sistema
     */
    public void sendNewUserCredentialsEmail(
            String name,
            String to,
            String username,
            String rawPassword,
            String systemUrl
    ) {
        if (to == null || to.trim().isEmpty()) {
            return;
        }

        String subject = "Bienvenido al Sistema de Vacaciones";

        String content =
            "<p>Hola <strong>" + safe(name) + "</strong>,</p>" +
            "<p>Tu cuenta ha sido creada correctamente. A continuación encontrarás tus datos de acceso:</p>" +
            buildInfoTable(new String[][]{
                {"Usuario", safe(username)},
                {"Contraseña Temporal", safe(rawPassword)},
                {"URL del sistema", "<a href='" + safe(systemUrl) + "' target='_blank'>" + safe(systemUrl) + "</a>"}
            }) +
            buildHighlightBox(
                "Recomendación de seguridad",
                "Por seguridad, cambia tu contraseña después de iniciar sesión por primera vez."
            ) +
            "<p>Ya puedes ingresar al sistema con la información anterior.</p>";

        sendHtmlEmail(to, subject, buildBaseTemplate("Bienvenido", content));
    }

    /**
     * Envía un correo al usuario cuando un administrador restablece su contraseña.
     *
     * <p>
     * La contraseña enviada debe ser temporal, por lo que el usuario deberá
     * cambiarla al iniciar sesión.
     * </p>
     *
     * @param name nombre de la persona
     * @param to correo del usuario
     * @param username nombre de usuario
     * @param rawPassword nueva contraseña temporal en texto plano
     * @param systemUrl URL del sistema
     */
    public void sendResetPasswordEmail(
            String name,
            String to,
            String username,
            String rawPassword,
            String systemUrl
    ) {
        if (to == null || to.trim().isEmpty()) {
            log.warn("No se puede enviar correo de reset password porque el destinatario es nulo o vacío");
            return;
        }

        String subject = "Restablecimiento de contraseña - " + SYSTEM_NAME;

        String content =
            "<p>Hola <strong>" + safe(name) + "</strong>,</p>" +
            "<p>Un administrador restableció tu contraseña de acceso al sistema.</p>" +
            "<p>Te compartimos tu nueva contraseña temporal:</p>" +
            buildInfoTable(new String[][]{
                {"Usuario", safe(username)},
                {"Contraseña temporal", safe(rawPassword)},
                {"URL del sistema", "<a href='" + safe(systemUrl) + "' target='_blank'>" + safe(systemUrl) + "</a>"}
            }) +
            buildHighlightBox(
                "Acción requerida",
                "Debes cambiar esta contraseña al iniciar sesión para poder continuar usando el sistema."
            ) +
            "<p>Si no reconoces este cambio, contacta al administrador del sistema.</p>";

        sendHtmlEmail(to, subject, buildBaseTemplate("Contraseña restablecida", content));
    }

    /**
     * Envía un correo HTML.
     *
     * @param to correo destino
     * @param subject asunto
     * @param htmlBody cuerpo HTML
     */
    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                message,
                false,
                StandardCharsets.UTF_8.name()
            );

            helper.setTo(to);
            helper.setFrom(FROM_EMAIL);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Correo enviado correctamente a: {}", to);
        } catch (MessagingException e) {
            log.error("Error al enviar correo a: {}", to, e);
            throw new RuntimeException("Error al enviar correo a " + to, e);
        }
    }

    /**
     * Construye la plantilla base HTML para todos los correos del sistema.
     *
     * @param title título principal del correo
     * @param content contenido específico del mensaje
     * @return HTML completo
     */
    private String buildBaseTemplate(String title, String content) {
        return "<!DOCTYPE html>" +
            "<html lang='es'>" +
            "<head>" +
            "<meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
            "<title>" + safe(title) + "</title>" +
            "</head>" +
            "<body style='margin:0; padding:0; background-color:#f4f6f8; font-family:Arial, Helvetica, sans-serif; color:#1f2937;'>" +

            "<table width='100%' style='background-color:#f4f6f8; padding:24px 0;'>" +
            "<tr><td align='center'>" +

            "<table width='700' style='max-width:700px; background:#ffffff; border-radius:14px; overflow:hidden; box-shadow:0 4px 14px rgba(0,0,0,0.08);'>" +

            "<tr>" +
            "<td style='background:linear-gradient(135deg, #0f172a, #1d4ed8); padding:24px 32px;'>" +
            "<h1 style='margin:0; font-size:24px; color:#ffffff;'>" + SYSTEM_NAME + "</h1>" +
            "<p style='margin:8px 0 0; font-size:14px; color:#dbeafe;'>Notificación automática del sistema</p>" +
            "</td>" +
            "</tr>" +

            "<tr>" +
            "<td style='padding:32px;'>" +
            "<h2 style='margin:0 0 20px; font-size:22px; color:#111827;'>" + safe(title) + "</h2>" +
            "<div style='font-size:15px; line-height:1.7; color:#374151;'>" +
            content +
            "</div>" +
            "</td>" +
            "</tr>" +

            "<tr>" +
            "<td style='padding:20px 32px; background:#f9fafb; border-top:1px solid #e5e7eb; text-align:center;'>" +

            "<div style='margin-bottom:12px;'>" +
            "<a href='" + safe(systemUrl) + "' target='_blank' " +
            "style='display:inline-block; padding:10px 20px; background:#1d4ed8; color:#ffffff; text-decoration:none; border-radius:8px; font-size:14px; font-weight:bold;'>" +
            "Ir al sistema" +
            "</a>" +
            "</div>" +

            "<p style='margin:0; font-size:12px; color:#6b7280;'>" +
            "También puedes acceder desde: <br>" +
            "<a href='" + safe(systemUrl) + "' style='color:#1d4ed8;'>" + safe(systemUrl) + "</a>" +
            "</p>" +

            "<p style='margin-top:10px; font-size:12px; color:#9ca3af;'>" +
            "Este correo fue generado automáticamente por el " + SYSTEM_NAME + ". No responder." +
            "</p>" +
            "<p style='margin:0; font-size:12px; color:#6b7280;'>" +
            "Este correo fue generado automáticamente por el " + SYSTEM_NAME + ". No responder." +
            "</p>" +
            "</td>" +
            "</tr>" +

            "</table>" +
            "</td></tr></table>" +
            "</body></html>";
    }

    /**
     * Construye una tabla visual para mostrar información en pares clave/valor.
     *
     * @param rows filas de información
     * @return HTML de la tabla
     */
    private String buildInfoTable(String[][] rows) {
        StringBuilder html = new StringBuilder();

        html.append("<table width='100%' style='border-collapse:collapse; margin:20px 0; border:1px solid #e5e7eb;'>");

        for (String[] row : rows) {
            html.append("<tr>");

            html.append("<td style='padding:12px; background:#f9fafb; border-bottom:1px solid #e5e7eb; font-weight:bold;'>")
                .append(row[0])
                .append("</td>");

            html.append("<td style='padding:12px; border-bottom:1px solid #e5e7eb;'>")
                .append(row[1])
                .append("</td>");

            html.append("</tr>");
        }

        html.append("</table>");

        return html.toString();
    }

    /**
     * Construye una caja destacada para avisos o recomendaciones.
     *
     * @param title título de la caja
     * @param message mensaje destacado
     * @return HTML del bloque resaltado
     */
    private String buildHighlightBox(String title, String message) {
        return "<div style='margin:20px 0; padding:16px; background:#eff6ff; border-left:5px solid #2563eb;'>" +
            "<p style='margin:0 0 8px; font-weight:bold; color:#1d4ed8;'>" + safe(title) + "</p>" +
            "<p style='margin:0;'>" + safe(message) + "</p>" +
            "</div>";
    }

    /**
     * Sanitiza valores nulos para evitar textos "null" en el correo.
     *
     * @param value valor original
     * @return cadena segura
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}