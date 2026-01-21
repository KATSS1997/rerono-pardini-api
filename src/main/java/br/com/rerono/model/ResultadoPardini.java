package br.com.rerono.model;

public class ResultadoPardini {
    
    private String codPedApoio;
    private Integer anoCodPedApoio;
    private byte[] pdfBytes;
    private byte[] graficoBytes;
    private String hashPdf;
    private String hashGrafico;
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
    
    public byte[] getPdfBytes() {
        return pdfBytes;
    }
    
    public void setPdfBytes(byte[] pdfBytes) {
        this.pdfBytes = pdfBytes;
    }
    
    public byte[] getGraficoBytes() {
        return graficoBytes;
    }
    
    public void setGraficoBytes(byte[] graficoBytes) {
        this.graficoBytes = graficoBytes;
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
    
    public boolean temPdf() {
        return pdfBytes != null && pdfBytes.length > 0;
    }
    
    public boolean temGrafico() {
        return graficoBytes != null && graficoBytes.length > 0;
    }
    
    public int getTamanhoPdf() {
        return pdfBytes != null ? pdfBytes.length : 0;
    }
    
    public int getTamanhoGrafico() {
        return graficoBytes != null ? graficoBytes.length : 0;
    }
}