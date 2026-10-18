package com.inssoft.people.onboarding.service;

import com.inssoft.people.onboarding.dto.OnboardingResponse;
import com.inssoft.people.onboarding.dto.TourStep;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class OnboardingService {

    private final UserRepository userRepository;

    public OnboardingService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public OnboardingResponse statusFor(User user) {
        return new OnboardingResponse(
                Boolean.TRUE.equals(user.getOnboardingCompleted()),
                stepsFor(user));
    }

    @Transactional
    public void complete(User user) {
        user.setOnboardingCompleted(true);
        userRepository.save(user);
    }

    public List<TourStep> stepsFor(User user) {
        Role role = user.getRole() == null ? Role.ROLE_COLABORADOR : user.getRole();
        List<TourStep> steps = new ArrayList<>();

        steps.add(new TourStep(
                "[data-tour=\"home-hero\"]",
                "Tu panel principal",
                "Desde aquí ves tu resumen y accedes a las funciones disponibles según tu rol."));

        steps.add(new TourStep(
                "a[href=\"/profile\"]",
                "Mi perfil",
                "Consulta tus datos personales y laborales, y mantén actualizados tus contactos de emergencia."));

        steps.add(new TourStep(
                "a[href=\"/documents\"]",
                "Mis documentos",
                "Descarga los documentos de tu expediente que Recursos Humanos haya compartido contigo."));

        steps.add(new TourStep(
                "a[href=\"/payroll\"]",
                "Mis recibos de nómina",
                "Consulta y descarga los recibos PDF publicados para ti."));

        steps.add(new TourStep(
                "a[href=\"/communications\"]",
                "Comunicados",
                "Revisa los avisos y noticias internas publicados por Recursos Humanos."));

        steps.add(new TourStep(
                "a[href=\"/vacations/request\"]",
                "Solicitar vacaciones",
                "Captura un rango de fechas; el sistema calcula los días hábiles con el calendario de tu empresa y valida tu saldo."));

        steps.add(new TourStep(
                "a[href=\"/vacations/status\"]",
                "Consultar tus solicitudes",
                "Revisa el estatus de tus solicitudes: pendientes, aprobadas, rechazadas o canceladas."));

        if (role == Role.ROLE_ADMIN) {
            steps.add(new TourStep(
                    "a[href=\"/admin/requests\"]",
                    "Revisar solicitudes",
                    "Aprueba o rechaza solicitudes pendientes de administración."));
            steps.add(new TourStep(
                    "a[href=\"/admin/employees\"]",
                    "Empleados",
                    "Consulta el directorio, los expedientes laborales y registra bajas."));
            steps.add(new TourStep(
                    "a[href=\"/admin/documents\"]",
                    "Documentos de empleados",
                    "Carga y descarga documentos del expediente de cada colaborador."));
            steps.add(new TourStep(
                    "a[href=\"/admin/payroll\"]",
                    "Nómina",
                    "Crea periodos y publica los recibos PDF de los empleados."));
            steps.add(new TourStep(
                    "a[href=\"/admin/communications\"]",
                    "Comunicados",
                    "Publica avisos internos para todo el equipo."));
            steps.add(new TourStep(
                    "a[href=\"/admin/users\"]",
                    "Administración de usuarios",
                    "Crea usuarios, actualiza sus datos, cambia roles y restablece contraseñas."));
            steps.add(new TourStep(
                    "a[href=\"/admin/requests/history\"]",
                    "Historial",
                    "Consulta solicitudes aprobadas y rechazadas con filtros."));
            steps.add(new TourStep(
                    "a[href=\"/admin/calendar\"]",
                    "Calendario",
                    "Visualiza las ausencias del equipo y exporta el calendario."));
            steps.add(new TourStep(
                    "a[href=\"/admin/politicas\"]",
                    "Políticas de la empresa",
                    "Administra los documentos y reglamentos internos."));
            steps.add(new TourStep(
                    "a[href=\"/admin/dashboard\"]",
                    "Dashboard KPI",
                    "Consulta métricas de vacaciones, solicitudes pendientes y ausencias del día."));
        } else {
            steps.add(new TourStep(
                    "a[href=\"/vacations/politicas\"]",
                    "Políticas de la empresa",
                    "Consulta los documentos y reglamentos internos que publica Recursos Humanos."));

            if (role == Role.ROLE_LIDER) {
                steps.add(new TourStep(
                        "a[href=\"/leader/team\"]",
                        "Mi equipo",
                        "Consulta a los colaboradores que reportan directamente contigo."));
                steps.add(new TourStep(
                        "a[href=\"/leader/requests\"]",
                        "Revisar solicitudes de tu equipo",
                        "Aprueba o rechaza las solicitudes que te fueron asignadas como líder."));
                steps.add(new TourStep(
                        "a[href=\"/admin/requests/history\"]",
                        "Historial",
                        "Consulta el historial de solicitudes aprobadas y rechazadas."));
            }
        }

        steps.add(new TourStep(
                null,
                "¿Quieres verlo de nuevo?",
                "Puedes repetir este recorrido en cualquier momento con el botón «Ver recorrido» de la pantalla de inicio."));

        return steps;
    }
}
