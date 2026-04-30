package com.mx.vacaciones.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tb_politicas_empresa")
public class Politica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idpolitica;

    @Column(name = "nombre_politica", nullable = false, length = 255)
    private String nombrePolitica;

    @Lob
    @Column(name = "documento_pdf")
    private byte[] documentoPdf;

    // Getters y Setters
    public Integer getIdpolitica() { return idpolitica; }
    public void setIdpolitica(Integer idpolitica) { this.idpolitica = idpolitica; }

    public String getNombrePolitica() { return nombrePolitica; }
    public void setNombrePolitica(String nombrePolitica) { this.nombrePolitica = nombrePolitica; }

    public byte[] getDocumentoPdf() { return documentoPdf; }
    public void setDocumentoPdf(byte[] documentoPdf) { this.documentoPdf = documentoPdf; }
}