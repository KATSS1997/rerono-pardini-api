package br.com.rerono.model;

public class PedidoLabPendente {

    private String cdPedLab;     // ITPED_LAB.CD_PED_LAB
    private String snAssinado;   // ITPED_LAB.SN_ASSINADO

    public PedidoLabPendente() {}

    public PedidoLabPendente(String cdPedLab, String snAssinado) {
        this.cdPedLab = cdPedLab;
        this.snAssinado = snAssinado;
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
                "cdPedLab='" + cdPedLab + '\'' +
                ", snAssinado='" + snAssinado + '\'' +
                '}';
    }
}
