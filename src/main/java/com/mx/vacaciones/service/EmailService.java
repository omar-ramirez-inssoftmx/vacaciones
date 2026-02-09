package com.mx.vacaciones.service;

import java.util.List;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.UserRepository;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    public EmailService(JavaMailSender mailSender, UserRepository userRepository) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
    }

    public void sendVacationRequestEmail(
            String to,
            String username,
            String startDate,
            String endDate,
            int days
    ) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);

        // IMPORTANTE: usa un remitente válido/registrado en Brevo (sender)
        msg.setFrom("no-reply@inssoftmx.com");

        msg.setSubject("Solicitud de vacaciones registrada");
        msg.setText(
            "Hola " + username + ",\n\n" +
            "Tu solicitud de vacaciones fue registrada correctamente.\n\n" +
            "Inicio: " + startDate + "\n" +
            "Fin: " + endDate + "\n" +
            "Días solicitados: " + days + "\n\n" +
            "Estatus: PENDIENTE\n\n" +
            "Sistema de Vacaciones"
        );

        mailSender.send(msg);
    }
    
    public void notifyAdminsVacationRequest(VacationRequest request) {

        List<User> admins = userRepository.findByRoleAndEnabledTrue(Role.ROLE_ADMIN);

        if (admins.isEmpty()) {
            return; // no admins, no mail
        }

        for (User admin : admins) {
            sendMailToAdmin(admin.getEmail(), request);
        }
    }

    private void sendMailToAdmin(String to, VacationRequest request) {

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setFrom("no-reply@inssoftmx.com");
        mail.setSubject("📩 Nueva solicitud de vacaciones");

        mail.setText(
            "Se ha registrado una nueva solicitud de vacaciones.\n\n" +
            "Usuario: " + request.getUser().getUsername() + "\n" +
            "Correo: " + request.getUser().getEmail() + "\n" +
            "Fecha inicio: " + request.getStartDate() + "\n" +
            "Fecha fin: " + request.getEndDate() + "\n" +
            "Días solicitados: " + request.getDays() + "\n\n" +
            "Ingresa al sistema para revisarla."
        );

        mailSender.send(mail);
    }
    
    public void notifyUserDecision(VacationRequest request) {

        if (request.getUser() == null || request.getUser().getEmail() == null) {
            return;
        }

        String to = request.getUser().getEmail();

        String status = request.getStatus(); // APPROVED / REJECTED / PENDING
        String subject;
        String body;

        if ("APPROVED".equals(status)) {
            subject = "✅ Vacaciones aprobadas";
            body =
                "Tu solicitud de vacaciones fue APROBADA.\n\n" +
                "Fecha inicio: " + request.getStartDate() + "\n" +
                "Fecha fin: " + request.getEndDate() + "\n" +
                "Días: " + request.getDays() + "\n\n" +
                "Puedes consultar el estatus en el sistema.";
        } else if ("REJECTED".equals(status)) {
            subject = "❌ Vacaciones rechazadas";
            body =
                "Tu solicitud de vacaciones fue RECHAZADA.\n\n" +
                "Fecha inicio: " + request.getStartDate() + "\n" +
                "Fecha fin: " + request.getEndDate() + "\n" +
                "Días: " + request.getDays() + "\n\n" +
                "Puedes consultar el estatus en el sistema.";
        } else {
            return; // no notificar si sigue PENDING
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setFrom("no-reply@inssoftmx.com");
        mail.setSubject(subject);
        mail.setText(body);

        mailSender.send(mail);
    }

}
