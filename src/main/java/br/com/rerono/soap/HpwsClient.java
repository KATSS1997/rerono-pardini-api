package br.com.rerono.soap;

import br.com.rerono.codec.Base64Handler;
import br.com.rerono.config.AppConfig;
import br.com.rerono.model.ResultadoPardini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HpwsClient {

    private static final Logger logger = LoggerFactory.getLogger(HpwsClient.class);

    private final String endpoint;
    private final String login;
    private final String passwd;
    private final int connectTimeout;
    private final int readTimeout;
    private final String soapActionGetResultadoPedido;

    public HpwsClient() {
        AppConfig config = AppConfig.getInstance();
        this.endpoint = config.getPardiniEndpoint();
        this.login = config.getPardiniLogin();
        this.passwd = config.getPardiniPasswd();
        this.connectTimeout = config.getPardiniConnectTimeout();
        this.readTimeout = config.getPardiniReadTimeout();
        this.soapActionGetResultadoPedido = config.getPardiniSoapActionGetResultadoPedido();
    }

    public HpwsClient(String endpoint, String login, String passwd) {
        this.endpoint = endpoint;
        this.login = login;
        this.passwd = passwd;
        this.connectTimeout = 30000;
        this.readTimeout = 60000;
        this.soapActionGetResultadoPedido =
                "http://hermespardini.com.br/b2b/apoio/schemas/HPWS.XMLServer.getResultadoPedido";
    }

    public ResultadoPardini getResultadoPedido(int anoCodPedApoio, String codPedApoio, int incluirPdf) {
        ResultadoPardini resultado = new ResultadoPardini();
        resultado.setAnoCodPedApoio(anoCodPedApoio);
        resultado.setCodPedApoio(codPedApoio);

        try {
            String soapRequest = buildSoapRequest(anoCodPedApoio, codPedApoio, incluirPdf);
            logger.debug("Request SOAP para pedido {}-{}", anoCodPedApoio, codPedApoio);

            String soapResponse = sendSoapRequest(soapRequest);
            resultado.setXmlOriginal(soapResponse);

            parseResponse(soapResponse, resultado);

            if (resultado.isSucesso()) {
                logger.info("Pedido {}-{} obtido com sucesso. PDF: {} bytes, Gráfico: {} bytes",
                        anoCodPedApoio, codPedApoio,
                        resultado.getTamanhoPdf(),
                        resultado.getTamanhoGrafico());
            }

        } catch (Exception e) {
            logger.error("Erro ao consultar pedido {}-{}: {}",
                    anoCodPedApoio, codPedApoio, e.getMessage(), e);
            resultado.setSucesso(false);
            resultado.setMensagemErro(e.getMessage());
        }

        return resultado;
    }

    private String buildSoapRequest(int anoCodPedApoio, String codPedApoio, int incluirPdf) {
        // OBS: o WSDL que você colou inclui UnidadeNoValor (boolean). Vamos mandar false.
        boolean unidadeNoValor = false;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<soapenv:Envelope ");
        sb.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
        sb.append("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" ");
        sb.append("xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" ");
        sb.append("xmlns:sch=\"http://hermespardini.com.br/b2b/apoio/schemas\">\n");
        sb.append("  <soapenv:Header/>\n");
        sb.append("  <soapenv:Body>\n");
        sb.append("    <sch:getResultadoPedido soapenv:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n");
        sb.append("      <login xsi:type=\"xsd:string\">").append(escapeXml(login)).append("</login>\n");
        sb.append("      <passwd xsi:type=\"xsd:string\">").append(escapeXml(passwd)).append("</passwd>\n");
        sb.append("      <anoCodPedApoio xsi:type=\"xsd:long\">").append(anoCodPedApoio).append("</anoCodPedApoio>\n");
        sb.append("      <CodPedApoio xsi:type=\"xsd:string\">").append(escapeXml(codPedApoio)).append("</CodPedApoio>\n");
        sb.append("      <CodExmApoio xsi:type=\"xsd:string\"></CodExmApoio>\n");
        sb.append("      <PDF xsi:type=\"xsd:long\">").append(incluirPdf).append("</PDF>\n");
        sb.append("      <versaoResultado xsi:type=\"xsd:long\">1</versaoResultado>\n");
        sb.append("      <papelTimbrado xsi:type=\"xsd:boolean\">false</papelTimbrado>\n");
        sb.append("      <valorReferencia xsi:type=\"xsd:long\">0</valorReferencia>\n");
        sb.append("      <UnidadeNoValor xsi:type=\"xsd:boolean\">").append(unidadeNoValor).append("</UnidadeNoValor>\n");
        sb.append("    </sch:getResultadoPedido>\n");
        sb.append("  </soapenv:Body>\n");
        sb.append("</soapenv:Envelope>");

        return sb.toString();
    }

    private String sendSoapRequest(String soapRequest) throws IOException {
        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);

            connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
            // SOAPAction correta (WSDL)
            connection.setRequestProperty("SOAPAction", soapActionGetResultadoPedido);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] requestBytes = soapRequest.getBytes(StandardCharsets.UTF_8);
                os.write(requestBytes);
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            logger.debug("HTTP Response Code: {}", responseCode);

            InputStream inputStream;
            if (responseCode >= 200 && responseCode < 300) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
                if (inputStream == null) {
                    throw new IOException("HTTP Error: " + responseCode + " - " + connection.getResponseMessage());
                }
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
                return response.toString();
            }

        } finally {
            connection.disconnect();
        }
    }

    private void parseResponse(String xmlResponse, ResultadoPardini resultado) {
        try {
            String pdfBase64 = extractTagContent(xmlResponse, "PDF");
            if (pdfBase64 != null && !pdfBase64.isEmpty()) {
                byte[] pdfBytes = Base64Handler.decode(pdfBase64);
                if (pdfBytes.length > 0) {
                    resultado.setPdfBytes(pdfBytes);
                    resultado.setHashPdf(Base64Handler.calculateSha256(pdfBytes));

                    if (!Base64Handler.isPdf(pdfBytes)) {
                        logger.warn("Conteúdo da tag PDF não parece ser um PDF válido");
                    }
                }
            }

            String graficoBase64 = extractTagContent(xmlResponse, "Grafico");
            if (graficoBase64 != null && !graficoBase64.isEmpty()) {
                byte[] graficoBytes = Base64Handler.decode(graficoBase64);
                if (graficoBytes.length > 0) {
                    resultado.setGraficoBytes(graficoBytes);
                    resultado.setHashGrafico(Base64Handler.calculateSha256(graficoBytes));
                }
            }

            String codigoRetorno = extractTagContent(xmlResponse, "CodigoRetorno");
            resultado.setCodigoRetorno(codigoRetorno);

            String mensagemErro = extractTagContent(xmlResponse, "MensagemErro");
            if (mensagemErro != null && !mensagemErro.isEmpty()) {
                resultado.setMensagemErro(mensagemErro);
            }

            resultado.setSucesso(resultado.temPdf() ||
                    (mensagemErro == null || mensagemErro.isEmpty()));

        } catch (Exception e) {
            logger.error("Erro ao parsear resposta SOAP: {}", e.getMessage(), e);
            resultado.setSucesso(false);
            resultado.setMensagemErro("Erro no parse: " + e.getMessage());
        }
    }

    private String extractTagContent(String xml, String tagName) {
        String xmlLower = xml.toLowerCase();
        String tagLower = tagName.toLowerCase();

        int startTag = xmlLower.indexOf("<" + tagLower + ">");
        if (startTag == -1) {
            startTag = xmlLower.indexOf("<" + tagLower + " ");
        }

        if (startTag == -1) {
            return null;
        }

        int contentStart = xml.indexOf(">", startTag) + 1;
        int endTag = xmlLower.indexOf("</" + tagLower + ">", contentStart);

        if (endTag == -1) {
            return null;
        }

        return xml.substring(contentStart, endTag).trim();
    }

    private String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public boolean testarConexao() {
        // Melhor teste: baixar WSDL
        String wsdlUrl = endpoint.contains("?") ? (endpoint + "&WSDL") : (endpoint + "?WSDL");

        try {
            URL url = new URL(wsdlUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.connect();
            int code = connection.getResponseCode();
            connection.disconnect();

            // 200 OK = acessível
            return code >= 200 && code < 300;
        } catch (Exception e) {
            logger.error("Falha ao testar conexão com Pardini (WSDL): {}", e.getMessage());
            return false;
        }
    }
}
