package com.mx.vacaciones.controller;

import com.mx.vacaciones.model.Politica;
import com.mx.vacaciones.service.PoliticaService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
@RequestMapping("/vacations/politicas")
public class ColaboradorPoliticaController {

    private final PoliticaService politicaService;

    public ColaboradorPoliticaController(PoliticaService politicaService) {
        this.politicaService = politicaService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("politicas", politicaService.listarTodas());
        return "vacations/politicas";
    }

    @GetMapping("/ver/{id}")
    public ResponseEntity<byte[]> verPdf(@PathVariable Integer id) {
        Optional<Politica> politica = politicaService.buscarPorId(id);
        if (politica.isPresent() && politica.get().getDocumentoPdf() != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"politica.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(politica.get().getDocumentoPdf());
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/descargar/{id}")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Integer id) {
        Optional<Politica> politica = politicaService.buscarPorId(id);
        if (politica.isPresent() && politica.get().getDocumentoPdf() != null) {
            String nombre = politica.get().getNombrePolitica()
                    .replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ _-]", "") + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(politica.get().getDocumentoPdf());
        }
        return ResponseEntity.notFound().build();
    }
}