package br.com.rerono.model;

import java.util.ArrayList;
import java.util.List;

public class ResultadoPardini {

    private String codPedApoio;
    private Integer anoCodPedApoio;

    // múltiplos PDFs/gráficos (PDF=2 pode retornar vários)
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

    // ======= compatibilidade / campos do pedido =======

    public String getCodPedApoio() { return codPedApoio; }
    public void setCodPedApoio(String codPedApoio) { this.codPedApoio = codPedApoio; }

    public Integer getAnoCodPedApoio() { return anoCodPedApoio; }
    public void setAnoCodPedApoio(Integer anoCodPedApoio) { this.anoCodPedApoio = anoCodPedApoio; }

    public String getXmlOriginal() { return xmlOriginal; }
    public void setXmlOriginal(String xmlOriginal) { this.xmlOriginal = xmlOriginal; }

    public boolean isSucesso() { return sucesso; }
    public void setSucesso(boolean sucesso) { this.sucesso = sucesso; }

    public String getMensagemErro() { return mensagemErro; }
    public void setMensagemErro(String mensagemErro) { this.mensagemErro = mensagemErro; }

    public String getCodigoRetorno() { return codigoRetorno; }
    public void setCodigoRetorno(String codigoRetorno) { this.codigoRetorno = codigoRetorno; }

    // ======= PDFs =======

    public void addPdf(byte[] bytes, String hash) {
        if (bytes == null || bytes.length == 0) return;
        pdfs.add(bytes);
        hashesPdf.add(hash);
    }

    public List<byte[]> getPdfs() { return pdfs; }

    public int getTotalPdfs() { return pdfs.size(); }

    public long getTamanhoTotalPdfs() {
        long sum = 0;
        for (byte[] b : pdfs) sum += (b == null ? 0 : b.length);
        return sum;
    }

    public boolean temPdf() { return !pdfs.isEmpty(); }

    // “primeiro PDF” (caso precise compatibilidade em outros pontos)
    public byte[] getPdfBytes() { return pdfs.isEmpty() ? null : pdfs.get(0); }
    public String getHashPdf() { return hashesPdf.isEmpty() ? null : hashesPdf.get(0); }

    // ======= Gráficos =======

    public void addGrafico(byte[] bytes, String hash) {
        if (bytes == null || bytes.length == 0) return;
        graficos.add(bytes);
        hashesGraficos.add(hash);
    }

    public List<byte[]> getGraficos() { return graficos; }

    public int getTotalGraficos() { return graficos.size(); }

    public boolean temGrafico() { return !graficos.isEmpty(); }

    public byte[] getGraficoBytes() { return graficos.isEmpty() ? null : graficos.get(0); }
    public String getHashGrafico() { return hashesGraficos.isEmpty() ? null : hashesGraficos.get(0); }

    public int getTamanhoPdf() { return getPdfBytes() != null ? getPdfBytes().length : 0; }
    public int getTamanhoGrafico() { return getGraficoBytes() != null ? getGraficoBytes().length : 0; }
}
