package br.com.rerono.model;

import java.time.LocalDateTime;

public class Pedido {

    private Long idPedido;
    private Integer anoCodPedApoio;
    private String codPedApoio;

    private String codPedLab; // <- NOVO

    private Long cdAtendimento;
    private Long cdPaciente;
    private StatusPedido status;
    private Integer tentativas;
    private LocalDateTime dtCriacao;
    private LocalDateTime dtProcessamento;
    private Long cdArquivoPdf;
    private Long cdArquivoGrafico;
    private String hashPdf;
    private String hashGrafico;
    private String erroMsg;

    public enum StatusPedido {
        PENDENTE,
        PROCESSANDO,
        PROCESSADO,
        ERRO,
        IGNORADO
    }

    public Pedido() {
        this.status = StatusPedido.PENDENTE;
        this.tentativas = 0;
        this.dtCriacao = LocalDateTime.now();
    }

    public Pedido(Integer anoCodPedApoio, String codPedApoio, Long cdAtendimento, Long cdPaciente) {
        this();
        this.anoCodPedApoio = anoCodPedApoio;
        this.codPedApoio = codPedApoio;
        this.cdAtendimento = cdAtendimento;
        this.cdPaciente = cdPaciente;
    }

    public Long getIdPedido() {
        return idPedido;
    }

    public void setIdPedido(Long idPedido) {
        this.idPedido = idPedido;
    }

    public Integer getAnoCodPedApoio() {
        return anoCodPedApoio;
    }

    public void setAnoCodPedApoio(Integer anoCodPedApoio) {
        this.anoCodPedApoio = anoCodPedApoio;
    }

    public String getCodPedApoio() {
        return codPedApoio;
    }

    public void setCodPedApoio(String codPedApoio) {
        this.codPedApoio = codPedApoio;
    }

    // ===== NOVO =====
    public String getCodPedLab() {
        return codPedLab;
    }

    public void setCodPedLab(String codPedLab) {
        this.codPedLab = codPedLab;
    }
    // ===============

    public Long getCdAtendimento() {
        return cdAtendimento;
    }

    public void setCdAtendimento(Long cdAtendimento) {
        this.cdAtendimento = cdAtendimento;
    }

    public Long getCdPaciente() {
        return cdPaciente;
    }

    public void setCdPaciente(Long cdPaciente) {
        this.cdPaciente = cdPaciente;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public void setStatus(StatusPedido status) {
        this.status = status;
    }

    public Integer getTentativas() {
        return tentativas;
    }

    public void setTentativas(Integer tentativas) {
        this.tentativas = tentativas;
    }

    public void incrementarTentativas() {
        this.tentativas++;
    }

    public LocalDateTime getDtCriacao() {
        return dtCriacao;
    }

    public void setDtCriacao(LocalDateTime dtCriacao) {
        this.dtCriacao = dtCriacao;
    }

    public LocalDateTime getDtProcessamento() {
        return dtProcessamento;
    }

    public void setDtProcessamento(LocalDateTime dtProcessamento) {
        this.dtProcessamento = dtProcessamento;
    }

    public Long getCdArquivoPdf() {
        return cdArquivoPdf;
    }

    public void setCdArquivoPdf(Long cdArquivoPdf) {
        this.cdArquivoPdf = cdArquivoPdf;
    }

    public Long getCdArquivoGrafico() {
        return cdArquivoGrafico;
    }

    public void setCdArquivoGrafico(Long cdArquivoGrafico) {
        this.cdArquivoGrafico = cdArquivoGrafico;
    }

    public String getHashPdf() {
        return hashPdf;
    }

    public void setHashPdf(String hashPdf) {
        this.hashPdf = hashPdf;
    }

    public String getHashGrafico() {
        return hashGrafico;
    }

    public void setHashGrafico(String hashGrafico) {
        this.hashGrafico = hashGrafico;
    }

    public String getErroMsg() {
        return erroMsg;
    }

    public void setErroMsg(String erroMsg) {
        this.erroMsg = erroMsg;
    }

    public String getChavePardini() {
        return anoCodPedApoio + "-" + codPedApoio;
    }

    @Override
    public String toString() {
        return String.format("Pedido[id=%d, pardini=%s, atend=%d, status=%s, tentativas=%d]",
                idPedido, getChavePardini(), cdAtendimento, status, tentativas);
    }
}
