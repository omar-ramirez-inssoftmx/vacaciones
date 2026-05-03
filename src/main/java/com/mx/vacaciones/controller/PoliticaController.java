package com.mx.vacaciones.controller;

import com.mx.vacaciones.model.Politica;
import com.mx.vacaciones.service.PoliticaService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Optional;

@Controller
@RequestMapping("/admin/politicas")
public class PoliticaController {

    private final PoliticaService politicaService;

    public PoliticaController(PoliticaService politicaService) {
        this.politicaService = politicaService;
    }

    // Lista todas las políticas
    @GetMapping
    public String listar(Model model) {
        model.addAttribute("politicas", politicaService.listarTodas());
        return "admin/politicas";
    }
    @GetMapping("/descargar/{id}")
    public ResponseEntity<byte[]> descargar(@PathVariable Integer id) {
        Optional<Politica> politica = politicaService.buscarPorId(id);

        if (politica.isPresent()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"politica.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(politica.get().getDocumentoPdf());
        }

        return ResponseEntity.notFound().build();
    }
    // Guarda nueva política
    @PostMapping("/guardar")
    public String guardar(@RequestParam("nombre") String nombre,
                          @RequestParam("archivo") MultipartFile archivo,
                          RedirectAttributes redirectAttributes) throws IOException {
        if (!archivo.isEmpty()) {
            politicaService.guardar(nombre, archivo);
            redirectAttributes.addFlashAttribute("guardado", true);
            redirectAttributes.addFlashAttribute("nombreGuardado", nombre);
        }
        return "redirect:/admin/politicas";
    }
    // Sirve el PDF para previsualizar en el navegador
    @GetMapping("/ver/{id}")
    public ResponseEntity<?> verPdf(@PathVariable Integer id) {

        Optional<Politica> politica = politicaService.buscarPorId(id);

        if (politica.isEmpty()) {
            System.out.println("NO EXISTE");
            return ResponseEntity.notFound().build();
        }

        byte[] pdf = politica.get().getDocumentoPdf();

        if (pdf == null) {
            System.out.println("PDF NULL");
            return ResponseEntity.badRequest().body("PDF NULL");
        }

        System.out.println("PDF OK");
        System.out.println("Tamaño: " + pdf.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=politica.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    // Elimina una política
    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Integer id) {
        politicaService.eliminar(id);
        return "redirect:/admin/politicas";
    }
}

