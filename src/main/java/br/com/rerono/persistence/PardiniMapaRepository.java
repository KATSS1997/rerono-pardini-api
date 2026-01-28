package br.com.rerono.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PardiniMapaRepository {

    private static final Logger log = LoggerFactory.getLogger(PardiniMapaRepository.class);

    // tabela fixada no schema DBAMV conforme solicitado
    private static final String TABELA = "DBAMV.RERONO_PARDINI_MAPA";

    /**
     * Mantém compatibilidade com o IntegracaoWorker:
     * - construtor sem parâmetros
     */
    public PardiniMapaRepository() {
        // nada aqui; config vem de env/props
    }

    /**
     * Tipo interno que o IntegracaoWorker importa:
     * import br.com.rerono.persistence.PardiniMapaRepository.MapeamentoPardini;
     */
    public static class MapeamentoPardini {
        private final String codPedLab;
        private final String codPedApoio;

        public MapeamentoPardini(String codPedLab, String codPedApoio) {
            this.codPedLab = codPedLab;
            this.codPedApoio = codPedApoio;
        }

        public String getCodPedLab() {
            return codPedLab;
        }

        public String getCodPedApoio() {
            return codPedApoio;
        }
    }

    /**
     * Atualiza o mapa CodPedLab -> CodPedApoio a partir do XML salvo pelo HpwsClient
     * OU a partir do XML em string (quando o worker passa a resposta SOAP inteira).
     *
     * Mantém assinatura esperada pelo worker:
     * atualizarMapaDeXml(String caminhoArquivoXml)
     *
     * @return quantidade de upserts realizados
     */
    public int atualizarMapaDeXml(String caminhoArquivoXml) {

        if (isBlank(caminhoArquivoXml)) {
            log.warn("atualizarMapaDeXml chamado com valor vazio/nulo");
            return 0;
        }

        String trimmed = caminhoArquivoXml.trim();

        // 1) Se veio um XML/SOAP inteiro (fault ou envelope), NÃO é caminho de arquivo.
        if (trimmed.startsWith("<")) {

            // Se for SOAP Fault, só loga e ignora (não tem como extrair Pedido dali)
            if (trimmed.contains("<SOAP-ENV:Fault") || trimmed.contains("<soap:Fault")
                    || trimmed.toLowerCase().contains("<faultstring")) {

                String trecho = trimmed.substring(0, Math.min(300, trimmed.length()));
                log.warn("SOAP Fault recebido no getResultado. Ignorando atualização do mapa. Trecho={}", trecho);
                return 0;
            }

            // Se não for fault, tenta parsear como XML e procurar tags <Pedido> (mesmo dentro do SOAP)
            try {
                Document doc = parseXmlFromString(trimmed);
                return atualizarMapaAPartirDoDocument(doc);
            } catch (Exception e) {
                String trecho = trimmed.substring(0, Math.min(300, trimmed.length()));
                log.error("Não foi possível parsear XML recebido como string (trecho={}).", trecho, e);
                return 0;
            }
        }

        // 2) Caso normal: é um caminho de arquivo
        File xml = new File(trimmed);
        if (!xml.exists() || !xml.isFile()) {
            log.warn("Arquivo XML não encontrado para atualizar mapa: {}", trimmed);
            return 0;
        }

        try {
            Document doc = parseXmlFromFile(xml);
            return atualizarMapaAPartirDoDocument(doc);
        } catch (Exception e) {
            log.error("Erro ao processar XML do getResultado para atualizar mapa. Arquivo={}", trimmed, e);
            return 0;
        }
    }

    /**
     * Busca o CodPedApoio pelo CodPedLab.
     * Mantém compatibilidade com o worker.
     */
    public MapeamentoPardini buscarPorCodPedLab(String codPedLab) throws SQLException {

        String sql =
                "SELECT COD_PED_LAB, COD_PED_APOIO " +
                "FROM " + TABELA + " " +
                "WHERE COD_PED_LAB = ? " +
                "ORDER BY DT_ATUALIZACAO DESC " +
                "FETCH FIRST 1 ROWS ONLY";

        try (Connection conn = abrirConexao();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, codPedLab);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String cod = rs.getString("COD_PED_LAB");
                    String apoio = rs.getString("COD_PED_APOIO");
                    return new MapeamentoPardini(cod, apoio);
                }
            }
        }

        return null;
    }

    // ======================
    // Implementação interna
    // ======================

    private int atualizarMapaAPartirDoDocument(Document doc) throws SQLException {
        if (doc == null) return 0;

        doc.getDocumentElement().normalize();

        NodeList pedidos = doc.getElementsByTagName("Pedido");

        int totalPares = 0;
        int upserts = 0;

        for (int i = 0; i < pedidos.getLength(); i++) {
            Element pedido = (Element) pedidos.item(i);

            String codPedApoio = getText(pedido, "CodPedApoio");
            String codPedLab = getText(pedido, "CodPedLab");

            if (isBlank(codPedLab) || isBlank(codPedApoio)) {
                continue;
            }

            totalPares++;
            upsertMapa(codPedLab.trim(), codPedApoio.trim());
            upserts++;
        }

        log.info("Mapeamentos extraídos do XML (CodPedLab -> CodPedApoio): {}", totalPares);
        if (totalPares == 0) {
            log.info("Nenhum par CodPedLab/CodPedApoio encontrado no XML do getResultado");
        }

        return upserts;
    }

    private Document parseXmlFromFile(File xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(xml);
    }

    private Document parseXmlFromString(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        DocumentBuilder db = dbf.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(xml));
        return db.parse(is);
    }

    private void upsertMapa(String codPedLab, String codPedApoio) throws SQLException {

        String sql =
                "MERGE INTO " + TABELA + " t " +
                "USING (SELECT ? AS COD_PED_LAB, ? AS COD_PED_APOIO FROM dual) s " +
                "ON (t.COD_PED_LAB = s.COD_PED_LAB) " +
                "WHEN MATCHED THEN UPDATE SET " +
                "  t.COD_PED_APOIO = s.COD_PED_APOIO, " +
                "  t.DT_ATUALIZACAO = SYSTIMESTAMP " +
                "WHEN NOT MATCHED THEN INSERT " +
                "  (COD_PED_LAB, COD_PED_APOIO, DT_ATUALIZACAO) " +
                "VALUES " +
                "  (s.COD_PED_LAB, s.COD_PED_APOIO, SYSTIMESTAMP)";

        try (Connection conn = abrirConexao();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, codPedLab);
            ps.setString(2, codPedApoio);

            ps.executeUpdate();
        }
    }

    private Connection abrirConexao() throws SQLException {
        String url = getCfg("ORACLE_URL", "oracle.url", "DB_URL", "db.url");
        String user = getCfg("ORACLE_USER", "oracle.user", "DB_USER", "db.user");
        String pass = getCfg("ORACLE_PASSWORD", "oracle.password", "DB_PASSWORD", "db.password");

        if (isBlank(url) || isBlank(user) || pass == null) {
            throw new SQLException(
                    "Config Oracle não encontrada. Defina ORACLE_URL/ORACLE_USER/ORACLE_PASSWORD " +
                    "(ou oracle.url/oracle.user/oracle.password via -D)."
            );
        }

        return DriverManager.getConnection(url, user, pass);
    }

    private static String getCfg(String envKey, String propKey, String envKey2, String propKey2) {
        // prioridade: env -> system property -> env alternativo -> system property alternativo
        String v = System.getenv(envKey);
        if (!isBlank(v)) return v;

        v = System.getProperty(propKey);
        if (!isBlank(v)) return v;

        v = System.getenv(envKey2);
        if (!isBlank(v)) return v;

        v = System.getProperty(propKey2);
        if (!isBlank(v)) return v;

        return null;
    }

    private static String getText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list == null || list.getLength() == 0) return null;
        String v = list.item(0).getTextContent();
        return v != null ? v.trim() : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
