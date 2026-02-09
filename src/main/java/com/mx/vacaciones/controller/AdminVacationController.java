package com.mx.vacaciones.controller;

import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.VacationRepository;
import com.mx.vacaciones.service.EmailService;

import jakarta.servlet.http.HttpServletResponse;
@Controller
@RequestMapping("/admin")
public class AdminVacationController {

	private final VacationRepository vacationRepository;
	private final EmailService emailService;

	public AdminVacationController(VacationRepository vacationRepository, EmailService emailService) {
		this.vacationRepository = vacationRepository;
		this.emailService = emailService;

	}

	// 1) Ver solicitudes pendientes
	@GetMapping("/requests")
	public String viewRequests(Model model) {
		List<VacationRequest> pending = vacationRepository.findByStatusOrderByIdDesc("PENDIENTE");
		model.addAttribute("requests", pending);
		return "admin/requests";
	}

	// 2) Aprobar
	@PostMapping("/requests/{id}/approve")
	public String approve(@PathVariable Long id, Authentication auth) {
		VacationRequest r = vacationRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

		r.setStatus("APPROVED");
		r.setDecidedBy(auth.getName());
		r.setDecidedAt(LocalDateTime.now());
		VacationRequest saved = vacationRepository.save(r);

		emailService.notifyUserDecision(saved);

		return "redirect:/admin/requests";
	}

	// 3) Rechazar
	@PostMapping("/requests/{id}/reject")
	public String reject(@PathVariable Long id) {
		VacationRequest r = vacationRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

		r.setStatus("REJECTED");
		VacationRequest saved = vacationRepository.save(r);

		emailService.notifyUserDecision(saved);

		return "redirect:/admin/requests";
	}

	@GetMapping("/requests/history")
	public String history(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, Model model) {
		List<VacationRequest> requests = vacationRepository
				.findByStatusInOrderByIdDesc(List.of("APPROVED", "REJECTED"));

		model.addAttribute("requests", requests);
		model.addAttribute("status", status);
		model.addAttribute("q", q);
		model.addAttribute("from", from);
		model.addAttribute("to", to);
		return "admin/admin_requests_history";
	}

	@GetMapping("/calendar")
	public String calendarView() {
		return "admin/calendar";
	}

	@GetMapping("/calendar/events")
	@ResponseBody
	public List<Map<String, Object>> calendarEvents() {

		// Decide qué mostrar: normalmente APPROVED y PENDING
		List<String> statuses = List.of("APPROVED", "PENDING");

		List<VacationRequest> list = vacationRepository.findForCalendar(statuses);

		return list.stream().map(v -> {
			Map<String, Object> e = new HashMap<>();

			e.put("id", v.getId());
			e.put("title", v.getUsername() + " (" + v.getStatus() + ")");
			e.put("start", v.getStartDate().toString());
			e.put("end", v.getEndDate().plusDays(1).toString()); // 👈 end exclusivo

			// Colores por estatus (FullCalendar los entiende)
			if ("APPROVED".equalsIgnoreCase(v.getStatus())) {
				e.put("color", "#198754"); // verde bootstrap
			} else if ("PENDING".equalsIgnoreCase(v.getStatus())) {
				e.put("color", "#ffc107"); // amarillo bootstrap
				e.put("textColor", "#000");
			}

			// info extra para click
			e.put("username", v.getUsername());
			e.put("status", v.getStatus());
			e.put("days", v.getDays());

			return e;
		}).toList();
	}

	@GetMapping("/calendar/export.pdf")
	public void exportCalendarPdf(@RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			HttpServletResponse response) throws Exception {

		List<String> statuses = List.of("PENDING", "APPROVED", "PENDIENTE", "APROBADA");
		List<VacationRequest> list = vacationRepository.findForCalendarRange(statuses, from, to);

		response.setContentType("application/pdf");
		response.setHeader("Content-Disposition",
				"inline; filename=calendario-ausencias-" + from + "_a_" + to + ".pdf");

		Document doc = new Document(PageSize.LETTER.rotate(), 36, 36, 36, 36);
		PdfWriter.getInstance(doc, response.getOutputStream());
		doc.open();

		Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
		Font subFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
		Font headFont = new Font(Font.HELVETICA, 11, Font.BOLD);
		Font cellFont = new Font(Font.HELVETICA, 11, Font.NORMAL);

		Paragraph title = new Paragraph("Calendario de Ausencias (Admin)", titleFont);
		title.setAlignment(Element.ALIGN_CENTER);
		doc.add(title);

		doc.add(new Paragraph("Rango: " + from + " a " + to, subFont));
		doc.add(new Paragraph("Total registros: " + list.size(), subFont));
		doc.add(Chunk.NEWLINE);

		PdfPTable table = new PdfPTable(new float[] { 3f, 3f, 2f, 2f, 4f });
		table.setWidthPercentage(100);

		// Header
		addHeader(table, "Inicio", headFont);
		addHeader(table, "Fin", headFont);
		addHeader(table, "Días", headFont);
		addHeader(table, "Estatus", headFont);
		addHeader(table, "Usuario", headFont);

		// Rows
		for (VacationRequest v : list) {
			addCell(table, v.getStartDate().toString(), cellFont);
			addCell(table, v.getEndDate().toString(), cellFont);
			addCell(table, String.valueOf(v.getDays()), cellFont);
			addCell(table, String.valueOf(v.getStatus()), cellFont);
			addCell(table, String.valueOf(v.getUsername()), cellFont);
		}

		doc.add(table);
		doc.close();
	}

	private void addHeader(PdfPTable t, String text, Font f) {
		PdfPCell c = new PdfPCell(new Phrase(text, f));
		c.setBackgroundColor(new Color(230, 230, 230));
		c.setPadding(6);
		t.addCell(c);
	}

	private void addCell(PdfPTable t, String text, Font f) {
		PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, f));
		c.setPadding(6);
		t.addCell(c);
	}
}
