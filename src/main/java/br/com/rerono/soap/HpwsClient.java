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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente SOAP "na unha" para Hermes Pardini (HPWS.XMLServer)
 * - getResultadoPedido -> baixa PDF/Gráfico por pedido
 * - getResultado      -> baixa XML por período (retorna string XML)
 *
 * IMPORTANTE:
 * O método getResultadoPorPeriodo(...) gera um XML "template" de período.
 * Se o Pardini exigir outro layout exato, basta ajustar buildXmlPeriodo(...).
 */
public class HpwsClient {

    private static final Logger logger = LoggerFactory.getLogger(HpwsClient.class);

    // Timestamp p/ salvar arquivos
    private static final DateTimeFormatter TS_FILE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    // Sugestão de formato de período (ajuste se o Pardini exigir diferente)
    private static final DateTimeFormatter DT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String endpoint;
    private final String login;
    private final String passwd;
    private final int connectTimeout;
    private final int readTimeout;

    private final String soapActionGetResultadoPedido;
    private final String soapActionGetResultado;

    /**
     * Diretório onde vamos salvar artefatos (XML/PDF/PNG).
     * Pode ser sobrescrito por:
     * - java prop: -DPARDINI_OUTPUT_DIR=...
     * - env: PARDINI_OUTPUT_DIR
     *
     * Default: C:\projetos\rerono-pardini-api\pdf
     */
    private final Path outputDir;

    public HpwsClient() {
        AppConfig config = AppConfig.getInstance();
        this.endpoint = config.getPardiniEndpoint();
        this.login = config.getPardiniLogin();
        this.passwd = config.getPardiniPasswd();
        this.connectTimeout = config.getPardiniConnectTimeout();
        this.readTimeout = config.getPardiniReadTimeout();

        this.soapActionGetResultadoPedido = config.getPardiniSoapActionGetResultadoPedido();
        this.soapActionGetResultado = config.getPardiniSoapActionGetResultado();

        String out = System.getProperty("PARDINI_OUTPUT_DIR");
        if (out == null || out.isBlank()) out = System.getenv("PARDINI_OUTPUT_DIR");
        if (out == null || out.isBlank()) out = "C:\\projetos\\rerono-pardini-api\\pdf";
        this.outputDir = Path.of(out);
    }

    public HpwsClient(String endpoint, String login, String passwd) {
        this.endpoint = endpoint;
        this.login = login;
        this.passwd = passwd;
        this.connectTimeout = 30000;
        this.readTimeout = 60000;

        this.soapActionGetResultadoPedido =
                "http://hermespardini.com.br/b2b/apoio/schemas/HPWS.XMLServer.getResultadoPedido";
        this.soapActionGetResultado =
                "http://hermespardini.com.br/b2b/apoio/schemas/HPWS.XMLServer.getResultado";

        String out = System.getProperty("PARDINI_OUTPUT_DIR");
        if (out == null || out.isBlank()) out = System.getenv("PARDINI_OUTPUT_DIR");
        if (out == null || out.isBlank()) out = "C:\\projetos\\rerono-pardini-api\\pdf";
        this.outputDir = Path.of(out);
    }

    // =========================================================
    // getResultadoPedido (PDF/Gráfico por pedido)
    // =========================================================

    public ResultadoPardini getResultadoPedido(int anoCodPedApoio, String codPedApoio, int incluirPdf) {
        return getResultadoPedido(anoCodPedApoio, codPedApoio, "", incluirPdf);
    }

    public ResultadoPardini getResultadoPedido(int anoCodPedApoio, String codPedApoio,
                                              String codExmApoio, int incluirPdf) {
        ResultadoPardini resultado = new ResultadoPardini();
        resultado.setAnoCodPedApoio(anoCodPedApoio);
        resultado.setCodPedApoio(codPedApoio);

        long t0 = System.currentTimeMillis();
        String stamp = LocalDateTime.now().format(TS_FILE);

        try {
            validarConfigBasica();

            String soapRequest = buildSoapRequestGetResultadoPedido(anoCodPedApoio, codPedApoio, codExmApoio, incluirPdf);

            // NUNCA logar request completo (tem senha)
            logger.debug("Request SOAP getResultadoPedido {}-{} (PDF={})", anoCodPedApoio, codPedApoio, incluirPdf);

            String soapResponse = sendSoapRequest(soapRequest, soapActionGetResultadoPedido);
            resultado.setXmlOriginal(soapResponse);

            // Salvar XML sempre (mesmo fault)
            saveXml("getResultadoPedido", anoCodPedApoio + "-" + codPedApoio, stamp, soapResponse);

            parseResponseGetResultadoPedido(soapResponse, resultado);

            if (resultado.isSucesso()) {
                logger.info(
                        "Pedido {}-{} OK. PDFs: {} ({} bytes) | Gráficos: {} ({}ms)",
                        anoCodPedApoio, codPedApoio,
                        resultado.getTotalPdfs(), resultado.getTamanhoTotalPdfs(),
                        resultado.getTotalGraficos(),
                        (System.currentTimeMillis() - t0)
                );
            }

            // Salvar arquivos (PDFs/Gráficos)
            saveArtifactsGetResultadoPedido(anoCodPedApoio, codPedApoio, stamp, resultado);

        } catch (Exception e) {
            logger.error("Erro getResultadoPedido {}-{}: {}", anoCodPedApoio, codPedApoio, e.getMessage(), e);
            resultado.setSucesso(false);
            resultado.setMensagemErro(e.getMessage());
        }

        return resultado;
    }

    // =========================================================
    // getResultado (XML por período)
    // =========================================================

    /**
     * Chama getResultado passando um XML livre (conforme documentação Pardini).
     * Retorna a resposta como String (XML).
     */
    public String getResultado(String xmlPayload) {
        String stamp = LocalDateTime.now().format(TS_FILE);
        try {
            validarConfigBasica();

            String soapRequest = buildSoapRequestGetResultado(xmlPayload);

            // Não logar payload completo (pode conter dados sensíveis)
            logger.debug("Request SOAP getResultado (XML payload len={})", xmlPayload != null ? xmlPayload.length() : 0);

            String soapResponse = sendSoapRequest(soapRequest, soapActionGetResultado);

            saveXml("getResultado", "periodo", stamp, soapResponse);
            return soapResponse;

        } catch (Exception e) {
            logger.error("Erro getResultado: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * ✅ Compatibilidade com o IntegracaoWorker (assinatura esperada).
     * O parâmetro "grafico" entra no XML template (se o Pardini exigir).
     */
    public String getResultadoPeriodo(LocalDateTime start, LocalDateTime end, int grafico) {
        String xml = buildXmlPeriodo(start, end, grafico);

        // Log seguro só para diagnosticar "root inválida" (sem vazar conteúdo sensível)
        String root = "desconhecido";
        if (xml != null) {
            String t = xml.trim();
            int lt = t.indexOf('<');
            int gt = t.indexOf('>');
            if (lt >= 0 && gt > lt) {
                String inside = t.substring(lt + 1, gt).trim(); // ex: GetResultado ...attrs
                root = inside.split("\\s+")[0].replace("/", "");
            }
        }
        logger.info("getResultadoPeriodo: root do payload enviado = {}", root);

        return getResultado(xml);
    }

    /**
     * Conveniência: busca resultados em uma janela (start..end) montando um XML "template".
     * Ajuste o buildXmlPeriodo(...) se o Pardini exigir outro layout.
     */
    public String getResultadoPorPeriodo(LocalDateTime start, LocalDateTime end) {
        String xml = buildXmlPeriodo(start, end);
        return getResultado(xml);
    }

    /**
     * Conveniência: últimos 24h até agora (para rodar 1x por hora).
     */
    public String getResultadoUltimas24h() {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(24);
        return getResultadoPorPeriodo(start, end);
    }

    /**
     * TEMPLATE do XML do getResultado por período.
     */
    private String buildXmlPeriodo(LocalDateTime start, LocalDateTime end) {
        return buildXmlPeriodo(start, end, 0);
    }

    /**
     * ✅ AJUSTADO: TEMPLATE do XML do getResultado por período (com "grafico").
     *
     * Antes você mandava:
     *   <Parametros>...</Parametros>
     * E o Pardini respondeu:
     *   "Tag root do XML não é válida"
     *
     * Então agora enviamos um ROOT "wrapper" e tags padronizadas.
     * Se ainda assim o Pardini reclamar do root, você troca SOMENTE o nome <GetResultado> pelo root correto.
     */
    private String buildXmlPeriodo(LocalDateTime start, LocalDateTime end, int grafico) {
        String dataInicial = start.format(DT_DATE);
        String dataFinal = end.format(DT_DATE);
        String horaInicial = start.format(DT_TIME);
        String horaFinal = end.format(DT_TIME);

        return """
                <GetResultado>
                  <Parametros>
                    <DataInicial>%s</DataInicial>
                    <DataFinal>%s</DataFinal>
                    <HoraInicial>%s</HoraInicial>
                    <HoraFinal>%s</HoraFinal>
                    <Grafico>%d</Grafico>
                  </Parametros>
                </GetResultado>
                """.formatted(
                escapeXml(dataInicial),
                escapeXml(dataFinal),
                escapeXml(horaInicial),
                escapeXml(horaFinal),
                grafico
        );
    }

    // =========================================================
    // SOAP builders
    // =========================================================

    private String buildSoapRequestGetResultadoPedido(int anoCodPedApoio, String codPedApoio,
                                                      String codExmApoio, int incluirPdf) {

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
        sb.append("      <CodExmApoio xsi:type=\"xsd:string\">").append(escapeXml(codExmApoio != null ? codExmApoio : "")).append("</CodExmApoio>\n");
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

    private String buildSoapRequestGetResultado(String xmlPayload) {
        // getResultado: inputs (login, passwd, XML)
        // Usa estilo RPC/encoded igual ao WSDL.
        String safeXml = (xmlPayload == null) ? "" : xmlPayload;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<soapenv:Envelope ");
        sb.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
        sb.append("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" ");
        sb.append("xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" ");
        sb.append("xmlns:sch=\"http://hermespardini.com.br/b2b/apoio/schemas\">\n");
        sb.append("  <soapenv:Header/>\n");
        sb.append("  <soapenv:Body>\n");
        sb.append("    <sch:getResultado soapenv:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n");
        sb.append("      <login xsi:type=\"xsd:string\">").append(escapeXml(login)).append("</login>\n");
        sb.append("      <passwd xsi:type=\"xsd:string\">").append(escapeXml(passwd)).append("</passwd>\n");
        // XML costuma vir como string com tags dentro -> melhor colocar em CDATA
        sb.append("      <XML xsi:type=\"xsd:string\"><![CDATA[").append(safeXml).append("]]></XML>\n");
        sb.append("    </sch:getResultado>\n");
        sb.append("  </soapenv:Body>\n");
        sb.append("</soapenv:Envelope>");

        return sb.toString();
    }

    // =========================================================
    // HTTP send
    // =========================================================

    private String sendSoapRequest(String soapRequest, String soapAction) throws IOException {
        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);

            connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
            connection.setRequestProperty("SOAPAction", soapAction);

            byte[] requestBytes = soapRequest.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(requestBytes.length));

            try (OutputStream os = connection.getOutputStream()) {
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

    // =========================================================
    // Parse / extract (getResultadoPedido)
    // =========================================================

    private void parseResponseGetResultadoPedido(String xmlResponse, ResultadoPardini resultado) {
        try {
            // SOAP Fault?
            if (xmlResponse.contains("<SOAP-ENV:Fault>") || xmlResponse.contains("<soap:Fault>")) {
                String faultString = extractTagContent(xmlResponse, "faultstring");
                String detail = extractTagContent(xmlResponse, "info");
                String errorMsg = (faultString != null && !faultString.isBlank()) ? faultString : "SOAP Fault";
                if (detail != null && !detail.isBlank()) {
                    errorMsg += ": " + detail;
                }
                resultado.setSucesso(false);
                resultado.setMensagemErro(errorMsg);
                logger.warn("SOAP Fault recebido: {}", errorMsg);
                return;
            }

            // PDFs: pode haver múltiplos <PDF>
            List<String> pdfTags = extractAllTagContents(xmlResponse, "PDF");
            int pdfValidos = 0;
            for (String pdfBase64 : pdfTags) {
                if (pdfBase64 == null || pdfBase64.isBlank()) continue;

                byte[] pdfBytes = Base64Handler.decode(pdfBase64);
                if (pdfBytes == null || pdfBytes.length == 0) continue;

                if (!Base64Handler.isPdf(pdfBytes)) {
                    logger.warn("Conteúdo de uma tag PDF não parece PDF válido (len={})", pdfBytes.length);
                }

                String hash = Base64Handler.calculateSha256(pdfBytes);
                resultado.addPdf(pdfBytes, hash);
                pdfValidos++;
            }

            // Gráficos: pode haver múltiplos <Grafico>
            List<String> grafTags = extractAllTagContents(xmlResponse, "Grafico");
            int grafValidos = 0;
            for (String graficoBase64 : grafTags) {
                if (graficoBase64 == null || graficoBase64.isBlank()) continue;

                byte[] graficoBytes = Base64Handler.decode(graficoBase64);
                if (graficoBytes == null || graficoBytes.length == 0) continue;

                String hashG = Base64Handler.calculateSha256(graficoBytes);
                resultado.addGrafico(graficoBytes, hashG);
                grafValidos++;
            }

            String codigoRetorno = extractTagContent(xmlResponse, "CodigoRetorno");
            resultado.setCodigoRetorno(codigoRetorno);

            String mensagemErro = extractTagContent(xmlResponse, "MensagemErro");
            if (mensagemErro != null && !mensagemErro.isEmpty()) {
                resultado.setMensagemErro(mensagemErro);
            }

            boolean sucesso = (pdfValidos > 0) || (grafValidos > 0) ||
                    (mensagemErro == null || mensagemErro.isEmpty());

            resultado.setSucesso(sucesso);

        } catch (Exception e) {
            logger.error("Erro ao parsear resposta SOAP: {}", e.getMessage(), e);
            resultado.setSucesso(false);
            resultado.setMensagemErro("Erro no parse: " + e.getMessage());
        }
    }

    private String extractTagContent(String xml, String tagName) {
        List<String> all = extractAllTagContents(xml, tagName);
        return all.isEmpty() ? null : all.get(0);
    }

    private List<String> extractAllTagContents(String xml, String tagName) {
        List<String> out = new ArrayList<>();
        if (xml == null || xml.isBlank() || tagName == null || tagName.isBlank()) return out;

        String xmlLower = xml.toLowerCase();
        String tagLower = tagName.toLowerCase();

        int from = 0;
        while (true) {
            int startTag = xmlLower.indexOf("<" + tagLower + ">", from);
            int startTagAttr = xmlLower.indexOf("<" + tagLower + " ", from);
            if (startTag == -1 || (startTagAttr != -1 && startTagAttr < startTag)) {
                startTag = startTagAttr;
            }
            if (startTag == -1) break;

            int contentStart = xml.indexOf(">", startTag);
            if (contentStart == -1) break;
            contentStart++;

            int endTag = xmlLower.indexOf("</" + tagLower + ">", contentStart);
            if (endTag == -1) break;

            out.add(xml.substring(contentStart, endTag).trim());
            from = endTag + tagLower.length() + 3;
        }
        return out;
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // =========================================================
    // Teste leve
    // =========================================================

    public boolean testarConexao() {
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

            return code >= 200 && code < 300;
        } catch (Exception e) {
            logger.error("Falha ao testar conexão Pardini (WSDL): {}", e.getMessage());
            return false;
        }
    }

    // =========================================================
    // Salvamento de artefatos
    // =========================================================

    private void ensureOutputDir() {
        try {
            Files.createDirectories(outputDir);
        } catch (Exception e) {
            logger.warn("Não foi possível criar diretório de saída {}: {}", outputDir, e.getMessage());
        }
    }

    private void saveXml(String operacao, String chave, String stamp, String xml) {
        ensureOutputDir();
        try {
            String name = String.format("pardini-%s-%s-%s.xml", operacao, chave, stamp);
            Path p = outputDir.resolve(name);
            Files.writeString(p, xml, StandardCharsets.UTF_8);
            logger.info("XML salvo em: {}", p.toAbsolutePath());
        } catch (Exception e) {
            logger.warn("Falha ao salvar XML: {}", e.getMessage());
        }
    }

    private void saveArtifactsGetResultadoPedido(int ano, String pedido, String stamp, ResultadoPardini resultado) {
        ensureOutputDir();
        if (resultado == null) return;

        // PDFs (todos)
        try {
            int salvos = 0;
            List<byte[]> pdfs = resultado.getPdfs();
            for (int i = 0; i < pdfs.size(); i++) {
                byte[] bytes = pdfs.get(i);
                if (bytes == null || bytes.length == 0) continue;

                String name = String.format("pardini-%d-%s-%s-pdf%02d.pdf", ano, pedido, stamp, i);
                Path p = outputDir.resolve(name);
                Files.write(p, bytes);
                salvos++;
            }
            if (salvos > 0) {
                logger.info("PDFs salvos: {} | Pasta: {}", salvos, outputDir.toAbsolutePath());
            }
        } catch (Exception e) {
            logger.warn("Falha ao salvar PDFs: {}", e.getMessage());
        }

        // Gráficos (todos)
        try {
            int salvos = 0;
            List<byte[]> graficos = resultado.getGraficos();
            for (int i = 0; i < graficos.size(); i++) {
                byte[] g = graficos.get(i);
                if (g == null || g.length == 0) continue;

                String ext = "bin";
                if (g.length >= 4 && (g[0] & 0xFF) == 0x89 && g[1] == 0x50 && g[2] == 0x4E && g[3] == 0x47) {
                    ext = "png";
                } else if (g.length >= 3 && (g[0] & 0xFF) == 0xFF && (g[1] & 0xFF) == 0xD8 && (g[2] & 0xFF) == 0xFF) {
                    ext = "jpg";
                }

                String name = String.format("pardini-%d-%s-%s-grafico%02d.%s", ano, pedido, stamp, i, ext);
                Path p = outputDir.resolve(name);
                Files.write(p, g);
                salvos++;
            }
            if (salvos > 0) {
                logger.info("Gráficos salvos: {} | Pasta: {}", salvos, outputDir.toAbsolutePath());
            }
        } catch (Exception e) {
            logger.warn("Falha ao salvar gráficos: {}", e.getMessage());
        }
    }

    // =========================================================
    // Helpers
    // =========================================================

    private void validarConfigBasica() {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("pardini.soap.endpoint não configurado");
        }
        if (login == null || login.isBlank()) {
            throw new IllegalStateException("pardini.soap.login não configurado");
        }
        if (passwd == null || passwd.isBlank()) {
            throw new IllegalStateException("pardini.soap.passwd não configurado");
        }
        if (soapActionGetResultadoPedido == null || soapActionGetResultadoPedido.isBlank()) {
            throw new IllegalStateException("pardini.soap.action.getResultadoPedido não configurado");
        }
        if (soapActionGetResultado == null || soapActionGetResultado.isBlank()) {
            throw new IllegalStateException("pardini.soap.action.getResultado não configurado");
        }
    }
}
