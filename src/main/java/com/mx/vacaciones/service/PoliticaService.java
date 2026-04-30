package com.mx.vacaciones.service;

import com.mx.vacaciones.model.Politica;
import com.mx.vacaciones.repository.PoliticaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class PoliticaService {

    private final PoliticaRepository politicaRepository;

    public PoliticaService(PoliticaRepository politicaRepository) {
        this.politicaRepository = politicaRepository;
    }

    public List<Politica> listarTodas() {
        return politicaRepository.findAll();
    }

    public void guardar(String nombre, MultipartFile archivo) throws IOException {
        Politica politica = new Politica();
        politica.setNombrePolitica(nombre);
        politica.setDocumentoPdf(archivo.getBytes());
        politicaRepository.save(politica);
    }

    public Optional<Politica> buscarPorId(Integer id) {
        return politicaRepository.findById(id);
    }

    public void eliminar(Integer id) {
        politicaRepository.deleteById(id);
    }
}