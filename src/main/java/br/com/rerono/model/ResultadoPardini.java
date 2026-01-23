package br.com.rerono.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResultadoPardini {

    private String codPedApoio;
    private Integer anoCodPedApoio;

    // Compatibilidade (primeiro PDF/Gráfico)
    private byte[] pdfBytes;
    private byte[] graficoBytes;
    private String hashPdf;
    private String hashGrafico;

    // Novo: múltiplos PDFs/Gráficos
    private final List<byte[]> pdfs = new ArrayList<>();
    private final List<String> hashesPdf = new ArrayList<>();
    private final List<byte[]> graficos = new ArrayList<>();
    private final List<String> hashesGraficos = new ArrayList<>();

    private String xmlOriginal;
    private boolean sucesso;
    private String mensagemErro;
    private String codigoRetorno;

    public ResultadoPardini() {
        this.sucesso = false;
    }

    public String getCodPedApoio() {
        return codPedApoio;
    }

    public void setCodPedApoio(String codPedApoio) {
        this.codPedApoio = codPedApoio;
    }

    public Integer getAnoCodPedApoio() {
        return anoCodPedApoio;
    }

    public void setAnoCodPedApoio(Integer anoCodPedApoio) {
        this.anoCodPedApoio = anoCodPedApoio;
    }

    /**
     * Compatibilidade: retorna o PDF “principal” (primeiro PDF válido).
     */
    public byte[] getPdfBytes() {
        if (pdfBytes != null && pdfBytes.length > 0) return pdfBytes;
        if (!pdfs.isEmpty()) return pdfs.get(0);
        return null;
    }

    /**
     * Compatibilidade: define o PDF principal e também injeta como primeiro PDF na lista (se estiver vazia).
     */
    public void setPdfBytes(byte[] pdfBytes) {
        this.pdfBytes = pdfBytes;
        if (pdfBytes != null && pdfBytes.length > 0 && pdfs.isEmpty()) {
            pdfs.add(pdfBytes);
            // hash pode ser setado depois; não inventamos aqui
            hashesPdf.add(this.hashPdf);
        }
    }

    public byte[] getGraficoBytes() {
        if (graficoBytes != null && graficoBytes.length > 0) return graficoBytes;
        if (!graficos.isEmpty()) return graficos.get(0);
        return null;
    }

    public void setGraficoBytes(byte[] graficoBytes) {
        this.graficoBytes = graficoBytes;
        if (graficoBytes != null && graficoBytes.length > 0 && graficos.isEmpty()) {
            graficos.add(graficoBytes);
            hashesGraficos.add(this.hashGrafico);
        }
    }

    public String getHashPdf() {
        if (hashPdf != null && !hashPdf.isBlank()) return hashPdf;
        if (!hashesPdf.isEmpty() && hashesPdf.get(0) != null) return hashesPdf.get(0);
        return null;
    }

    public void setHashPdf(String hashPdf) {
        this.hashPdf = hashPdf;
        if (!hashesPdf.isEmpty()) {
            hashesPdf.set(0, hashPdf);
        }
    }

    public String getHashGrafico() {
        if (hashGrafico != null && !hashGrafico.isBlank()) return hashGrafico;
        if (!hashesGraficos.isEmpty() && hashesGraficos.get(0) != null) return hashesGraficos.get(0);
        return null;
    }

    public void setHashGrafico(String hashGrafico) {
        this.hashGrafico = hashGrafico;
        if (!hashesGraficos.isEmpty()) {
            hashesGraficos.set(0, hashGrafico);
        }
    }

    public String getXmlOriginal() {
        return xmlOriginal;
    }

    public void setXmlOriginal(String xmlOriginal) {
        this.xmlOriginal = xmlOriginal;
    }

    public boolean isSucesso() {
        return sucesso;
    }

    public void setSucesso(boolean sucesso) {
        this.sucesso = sucesso;
    }

    public String getMensagemErro() {
        return mensagemErro;
    }

    public void setMensagemErro(String mensagemErro) {
        this.mensagemErro = mensagemErro;
    }

    public String getCodigoRetorno() {
        return codigoRetorno;
    }

    public void setCodigoRetorno(String codigoRetorno) {
        this.codigoRetorno = codigoRetorno;
    }

    // =========================
    // Novo: múltiplos PDFs
    // =========================

    public void addPdf(byte[] bytes, String sha256) {
        if (bytes == null || bytes.length == 0) return;
        pdfs.add(bytes);
        hashesPdf.add(sha256);

        // Preenche compatibilidade se ainda não tiver “principal”
        if (this.pdfBytes == null || this.pdfBytes.length == 0) {
            this.pdfBytes = bytes;
            this.hashPdf = sha256;
        }
    }

    public List<byte[]> getPdfs() {
        return Collections.unmodifiableList(pdfs);
    }

    public List<String> getHashesPdf() {
        return Collections.unmodifiableList(hashesPdf);
    }

    public int getTotalPdfs() {
        return pdfs.size();
    }

    public int getTamanhoTotalPdfs() {
        int sum = 0;
        for (byte[] b : pdfs) sum += (b != null ? b.length : 0);
        return sum;
    }

    // =========================
    // Novo: múltiplos Gráficos
    // =========================

    public void addGrafico(byte[] bytes, String sha256) {
        if (bytes == null || bytes.length == 0) return;
        graficos.add(bytes);
        hashesGraficos.add(sha256);

        if (this.graficoBytes == null || this.graficoBytes.length == 0) {
            this.graficoBytes = bytes;
            this.hashGrafico = sha256;
        }
    }

    public List<byte[]> getGraficos() {
        return Collections.unmodifiableList(graficos);
    }

    public List<String> getHashesGraficos() {
        return Collections.unmodifiableList(hashesGraficos);
    }

    public int getTotalGraficos() {
        return graficos.size();
    }

    public int getTamanhoTotalGraficos() {
        int sum = 0;
        for (byte[] b : graficos) sum += (b != null ? b.length : 0);
        return sum;
    }

    // =========================
    // Helpers atuais (mantidos)
    // =========================

    public boolean temPdf() {
        return (getPdfBytes() != null && getPdfBytes().length > 0) || !pdfs.isEmpty();
    }

    public boolean temGrafico() {
        return (getGraficoBytes() != null && getGraficoBytes().length > 0) || !graficos.isEmpty();
    }

    public int getTamanhoPdf() {
        byte[] b = getPdfBytes();
        return b != null ? b.length : 0;
    }

    public int getTamanhoGrafico() {
        byte[] b = getGraficoBytes();
        return b != null ? b.length : 0;
    }
}
