package com.mx.vacaciones.controller;

import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.VacationRepository;
import com.mx.vacaciones.service.EmailService;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Controlador de administración de solicitudes de vacaciones.
 *
 * <p>
 * Responsabilidades principales:
 * </p>
 * <ul>
 *     <li>Consultar solicitudes pendientes</li>
 *     <li>Aprobar o rechazar solicitudes</li>
 *     <li>Consultar historial filtrado</li>
 *     <li>Mostrar calendario administrativo</li>
 *     <li>Exponer eventos para FullCalendar</li>
 *     <li>Exportar calendario en PDF</li>
 * </ul>
 */
@Controller
@RequestMapping("/admin")
public class AdminVacationController {

    /**
     * Ruta local del logo para el PDF.
     *
     * <p>
     * Si el archivo no existe, el PDF se genera sin logo y no falla.
     * </p>
     */
    private final VacationRepository vacationRepository;
    private final EmailService emailService;

    /**
     * Constructor principal del controller.
     *
     * @param vacationRepository repositorio de solicitudes de vacaciones
     * @param emailService servicio de correos
     */
    public AdminVacationController(VacationRepository vacationRepository, EmailService emailService) {
        this.vacationRepository = vacationRepository;
        this.emailService = emailService;
    }

    /**
     * Muestra las solicitudes pendientes por revisar.
     *
     * @param model modelo de la vista
     * @return vista de solicitudes pendientes
     */
    @GetMapping("/requests")
    public String viewRequests(Model model) {

        /*
         * Se usa PENDING porque es el valor persistido en base de datos.
         */
        List<VacationRequest> pending = vacationRepository.findByStatusOrderByIdDesc("PENDING");
        model.addAttribute("requests", pending);

        return "admin/requests";
    }

    /**
     * Aprueba una solicitud de vacaciones.
     *
     * @param id id de la solicitud
     * @param auth usuario autenticado que toma la decisión
     * @return redirección al listado de solicitudes
     */
    @PostMapping("/requests/{id}/approve")
    public String approve(@PathVariable Long id, Authentication auth) {

        VacationRequest request = vacationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        request.setStatus("APPROVED");
        request.setDecidedBy(auth.getName());
        request.setDecidedAt(LocalDateTime.now());

        VacationRequest saved = vacationRepository.save(request);

        /*
         * Notifica al usuario que su solicitud fue aprobada.
         */
        emailService.notifyUserDecision(saved);

        return "redirect:/admin/requests";
    }

    /**
     * Rechaza una solicitud de vacaciones.
     *
     * @param id id de la solicitud
     * @param auth usuario autenticado que toma la decisión
     * @return redirección al listado de solicitudes
     */
    @PostMapping("/requests/{id}/reject")
    public String reject(@PathVariable Long id, Authentication auth) {

        VacationRequest request = vacationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        request.setStatus("REJECTED");
        request.setDecidedBy(auth.getName());
        request.setDecidedAt(LocalDateTime.now());

        VacationRequest saved = vacationRepository.save(request);

        /*
         * Notifica al usuario que su solicitud fue rechazada.
         */
        emailService.notifyUserDecision(saved);

        return "redirect:/admin/requests";
    }

    /**
     * Muestra historial de solicitudes aprobadas / rechazadas con filtros.
     *
     * @param status estatus opcional
     * @param q texto de búsqueda opcional
     * @param from fecha inicial opcional
     * @param to fecha final opcional
     * @param model modelo para la vista
     * @return vista de historial administrativo
     */
    @GetMapping("/requests/history")
    public String history(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate to,
            Model model
    ) {

        List<VacationRequest> requests = vacationRepository.findHistoryFiltered(
                status,
                q,
                from,
                to
        );

        model.addAttribute("requests", requests);
        model.addAttribute("status", status);
        model.addAttribute("q", q);
        model.addAttribute("from", from);
        model.addAttribute("to", to);

        return "admin/admin_requests_history";
    }

    /**
     * Muestra la vista del calendario administrativo.
     *
     * @return vista de calendario
     */
    @GetMapping("/calendar")
    public String calendarView() {
        return "admin/calendar";
    }

    /**
     * Exponer eventos para FullCalendar en formato JSON.
     *
     * <p>
     * Se incluyen solicitudes:
     * </p>
     * <ul>
     *     <li>APPROVED</li>
     *     <li>PENDING</li>
     * </ul>
     *
     * @return lista de eventos para el calendario
     */
    @GetMapping("/calendar/events")
    @ResponseBody
    public List<Map<String, Object>> calendarEvents() {

        List<String> statuses = List.of("APPROVED", "PENDING");
        List<VacationRequest> list = vacationRepository.findForCalendar(statuses);

        return list.stream().map(v -> {
            Map<String, Object> event = new HashMap<>();

            event.put("id", v.getId());
            event.put("title", v.getUsername() + " (" + v.getStatus() + ")");
            event.put("start", v.getStartDate().toString());
            event.put("end", v.getEndDate().plusDays(1).toString()); // end exclusivo para FullCalendar

            /*
             * Colores visuales para FullCalendar.
             */
            if ("APPROVED".equalsIgnoreCase(v.getStatus())) {
                event.put("color", "#198754");
            } else if ("PENDING".equalsIgnoreCase(v.getStatus())) {
                event.put("color", "#ffc107");
                event.put("textColor", "#000");
            }

            /*
             * Datos extra para modales y detalle.
             */
            event.put("username", v.getUsername());
            event.put("status", v.getStatus());
            event.put("days", v.getDays());
            event.put("endDate", v.getEndDate() != null ? v.getEndDate().toString() : null);

            return event;
        }).toList();
    }

    /**
     * Exporta el calendario administrativo en PDF.
     *
     * <p>
     * El reporte incluye:
     * </p>
     * <ul>
     *     <li>Logo de la empresa si existe</li>
     *     <li>Título del reporte</li>
     *     <li>Rango consultado</li>
     *     <li>Total de registros</li>
     *     <li>Fecha de generación</li>
     *     <li>Tabla con detalle de ausencias</li>
     * </ul>
     *
     * @param from fecha inicial
     * @param to fecha final
     * @param response respuesta HTTP
     * @throws Exception en caso de error de generación
     */
    @GetMapping("/calendar/vacaciones.pdf")
    public void exportCalendarPdf(
            @RequestParam("from")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletResponse response
    ) throws Exception {

        /*
         * Se incluyen solicitudes aprobadas y pendientes
         * para construir el reporte del calendario administrativo.
         */
        List<String> statuses = List.of("PENDING", "APPROVED");
        List<VacationRequest> list = vacationRepository.findForCalendarRange(statuses, from, to);

        /*
         * Configuración de la respuesta HTTP para mostrar el PDF en navegador.
         */
        response.setContentType("application/pdf");
        response.setHeader(
                "Content-Disposition",
                "inline; filename=calendario-ausencias-" + from + "_a_" + to + ".pdf"
        );

        /*
         * Documento horizontal para dar mejor espacio a la tabla.
         */
        Document document = new Document(PageSize.LETTER.rotate(), 32, 32, 30, 30);
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        /*
         * Tipografías del reporte.
         */
        Font companyFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(37, 99, 235));
        Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(15, 23, 42));
        Font subtitleFont = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(100, 116, 139));
        Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
        Font cellFont = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(30, 41, 59));
        Font footerFont = new Font(Font.HELVETICA, 8, Font.ITALIC, new Color(148, 163, 184));

        /*
         * =========================
         * HEADER: LOGO + TITULOS
         * =========================
         *
         * Se construye una tabla de 2 columnas:
         * - izquierda: logo
         * - derecha: nombre de empresa, título y metadatos
         *
         * Si el logo no existe, no rompe el PDF.
         */
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[] { 1.2f, 8f });
        headerTable.setSpacingAfter(10f);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setPadding(0f);

        try {
            Image logo = Image.getInstance("src/main/resources/static/images/logo.png");
            logo.scaleToFit(58, 58);
            logoCell.addElement(logo);
        } catch (Exception e) {
            /*
             * Si no se encuentra el logo, el PDF se sigue generando normalmente.
             */
            logoCell.addElement(new Paragraph(""));
        }

        headerTable.addCell(logoCell);

        PdfPCell textCell = new PdfPCell();
        textCell.setBorder(Rectangle.NO_BORDER);
        textCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        textCell.setPaddingLeft(6f);
        textCell.setPaddingTop(2f);
        textCell.setPaddingBottom(2f);

        Paragraph company = new Paragraph("INSSOFT", companyFont);
        company.setSpacingAfter(3f);

        Paragraph title = new Paragraph("Calendario de Ausencias", titleFont);
        title.setSpacingAfter(5f);

        Paragraph reportInfo = new Paragraph(
                "Rango consultado: " + formatDate(from) + " al " + formatDate(to),
                subtitleFont
        );
        reportInfo.setSpacingAfter(2f);

        Paragraph reportMeta = new Paragraph(
                "Total de registros: " + list.size()
                        + "  |  Fecha de generación: " + formatDate(LocalDate.now()),
                subtitleFont
        );

        textCell.addElement(company);
        textCell.addElement(title);
        textCell.addElement(reportInfo);
        textCell.addElement(reportMeta);

        headerTable.addCell(textCell);

        document.add(headerTable);

        /*
         * =========================
         * TABLA PRINCIPAL
         * =========================
         */
        PdfPTable table = new PdfPTable(new float[] { 2.7f, 2.7f, 1.4f, 2.2f, 3.8f });
        table.setWidthPercentage(100);
        table.setSpacingBefore(2f);

        /*
         * Encabezados de tabla.
         */
        PdfPCell h1 = new PdfPCell(new Phrase("Inicio", headerFont));
        h1.setBackgroundColor(new Color(30, 64, 175));
        h1.setHorizontalAlignment(Element.ALIGN_CENTER);
        h1.setVerticalAlignment(Element.ALIGN_MIDDLE);
        h1.setPadding(9f);
        h1.setBorder(Rectangle.NO_BORDER);
        table.addCell(h1);

        PdfPCell h2 = new PdfPCell(new Phrase("Fin", headerFont));
        h2.setBackgroundColor(new Color(30, 64, 175));
        h2.setHorizontalAlignment(Element.ALIGN_CENTER);
        h2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        h2.setPadding(9f);
        h2.setBorder(Rectangle.NO_BORDER);
        table.addCell(h2);

        PdfPCell h3 = new PdfPCell(new Phrase("Días", headerFont));
        h3.setBackgroundColor(new Color(30, 64, 175));
        h3.setHorizontalAlignment(Element.ALIGN_CENTER);
        h3.setVerticalAlignment(Element.ALIGN_MIDDLE);
        h3.setPadding(9f);
        h3.setBorder(Rectangle.NO_BORDER);
        table.addCell(h3);

        PdfPCell h4 = new PdfPCell(new Phrase("Estatus", headerFont));
        h4.setBackgroundColor(new Color(30, 64, 175));
        h4.setHorizontalAlignment(Element.ALIGN_CENTER);
        h4.setVerticalAlignment(Element.ALIGN_MIDDLE);
        h4.setPadding(9f);
        h4.setBorder(Rectangle.NO_BORDER);
        table.addCell(h4);

        PdfPCell h5 = new PdfPCell(new Phrase("Usuario", headerFont));
        h5.setBackgroundColor(new Color(30, 64, 175));
        h5.setHorizontalAlignment(Element.ALIGN_CENTER);
        h5.setVerticalAlignment(Element.ALIGN_MIDDLE);
        h5.setPadding(9f);
        h5.setBorder(Rectangle.NO_BORDER);
        table.addCell(h5);

        /*
         * Filas alternadas para mejorar lectura.
         */
        boolean alternate = false;

        for (VacationRequest request : list) {
            Color rowBackground = alternate ? new Color(250, 250, 252) : Color.WHITE;
            Color borderColor = new Color(226, 232, 240);

            PdfPCell c1 = new PdfPCell(new Phrase(formatDate(request.getStartDate()), cellFont));
            c1.setBackgroundColor(rowBackground);
            c1.setBorderColor(borderColor);
            c1.setHorizontalAlignment(Element.ALIGN_CENTER);
            c1.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c1.setPadding(8f);
            table.addCell(c1);

            PdfPCell c2 = new PdfPCell(new Phrase(formatDate(request.getEndDate()), cellFont));
            c2.setBackgroundColor(rowBackground);
            c2.setBorderColor(borderColor);
            c2.setHorizontalAlignment(Element.ALIGN_CENTER);
            c2.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c2.setPadding(8f);
            table.addCell(c2);

            PdfPCell c3 = new PdfPCell(new Phrase(String.valueOf(request.getDays()), cellFont));
            c3.setBackgroundColor(rowBackground);
            c3.setBorderColor(borderColor);
            c3.setHorizontalAlignment(Element.ALIGN_CENTER);
            c3.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c3.setPadding(8f);
            table.addCell(c3);

            /*
             * Estatus con color visual más agradable.
             */
            String safeStatus = request.getStatus() == null ? "" : request.getStatus().toUpperCase();
            String labelStatus = safeStatus;

            if ("APPROVED".equalsIgnoreCase(safeStatus)) {
                labelStatus = "APROBADA";
            } else if ("PENDING".equalsIgnoreCase(safeStatus)) {
                labelStatus = "PENDIENTE";
            } else if ("REJECTED".equalsIgnoreCase(safeStatus)) {
                labelStatus = "RECHAZADA";
            }

            PdfPCell statusCell = new PdfPCell(new Phrase(labelStatus, cellFont));
            statusCell.setBorderColor(borderColor);
            statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            statusCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            statusCell.setPadding(8f);

            if ("APPROVED".equalsIgnoreCase(safeStatus)) {
                statusCell.setBackgroundColor(new Color(220, 252, 231));
            } else if ("PENDING".equalsIgnoreCase(safeStatus)) {
                statusCell.setBackgroundColor(new Color(254, 243, 199));
            } else if ("REJECTED".equalsIgnoreCase(safeStatus)) {
                statusCell.setBackgroundColor(new Color(254, 226, 226));
            } else {
                statusCell.setBackgroundColor(rowBackground);
            }

            table.addCell(statusCell);

            PdfPCell c5 = new PdfPCell(new Phrase(
                    request.getUsername() == null ? "" : request.getUsername(),
                    cellFont
            ));
            c5.setBackgroundColor(rowBackground);
            c5.setBorderColor(borderColor);
            c5.setHorizontalAlignment(Element.ALIGN_LEFT);
            c5.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c5.setPadding(8f);
            table.addCell(c5);

            alternate = !alternate;
        }

        document.add(table);

        /*
         * =========================
         * FOOTER
         * =========================
         */
        document.add(Chunk.NEWLINE);

        Paragraph footer = new Paragraph(
                "INSSOFT · Soluciones de TI para Negocios",
                footerFont
        );
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);

        document.close();
    }
    

    /**
     * Formatea fechas en dd/MM/yyyy.
     *
     * @param date fecha
     * @return fecha formateada o vacío si es null
     */
    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}