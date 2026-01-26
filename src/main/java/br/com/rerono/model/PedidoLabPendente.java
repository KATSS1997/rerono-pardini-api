package br.com.rerono.model;

public class PedidoLabPendente {

    private Long cdAtendimento;
    private String cdPedLab;     // ITPED_LAB.CD_PED_LAB
    private String snAssinado;   // ITPED_LAB.SN_ASSINADO

    public PedidoLabPendente() {}

    public PedidoLabPendente(Long cdAtendimento, String cdPedLab, String snAssinado) {
        this.cdAtendimento = cdAtendimento;
        this.cdPedLab = cdPedLab;
        this.snAssinado = snAssinado;
    }

    public Long getCdAtendimento() {
        return cdAtendimento;
    }

    public void setCdAtendimento(Long cdAtendimento) {
        this.cdAtendimento = cdAtendimento;
    }

    public String getCdPedLab() {
        return cdPedLab;
    }

    public void setCdPedLab(String cdPedLab) {
        this.cdPedLab = cdPedLab;
    }

    public String getSnAssinado() {
        return snAssinado;
    }

    public void setSnAssinado(String snAssinado) {
        this.snAssinado = snAssinado;
    }

    @Override
    public String toString() {
        return "PedidoLabPendente{" +
                "cdAtendimento=" + cdAtendimento +
                ", cdPedLab='" + cdPedLab + '\'' +
                ", snAssinado='" + snAssinado + '\'' +
                '}';
    }
}
