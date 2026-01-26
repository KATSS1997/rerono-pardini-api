package br.com.rerono.persistence;

import br.com.rerono.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PardiniMapaRepository {

    private static final Logger logger = LoggerFactory.getLogger(PardiniMapaRepository.class);

    private final DatabaseConfig dbConfig;

    public PardiniMapaRepository() {
        this.dbConfig = DatabaseConfig.getInstance();
    }

    public static class MapeamentoPardini {
        private final String codPedLab;
        private final Integer anoCodPedApoio;
        private final String codPedApoio;

        public MapeamentoPardini(String codPedLab, Integer anoCodPedApoio, String codPedApoio) {
            this.codPedLab = codPedLab;
            this.anoCodPedApoio = anoCodPedApoio;
            this.codPedApoio = codPedApoio;
        }

        public String getCodPedLab() {
            return codPedLab;
        }

        public Integer getAnoCodPedApoio() {
            return anoCodPedApoio;
        }

        public String getCodPedApoio() {
            return codPedApoio;
        }

        @Override
        public String toString() {
            return "MapeamentoPardini{" +
                    "codPedLab='" + codPedLab + '\'' +
                    ", anoCodPedApoio=" + anoCodPedApoio +
                    ", codPedApoio='" + codPedApoio + '\'' +
                    '}';
        }
    }

    /**
     * Extrai (CodPedLab/CD_PED_LAB) + CodPedApoio (+ AnoCodPedApoio se existir) e faz UPSERT.
     */
    public int atualizarMapaDeXml(String xml) throws SQLException {
        if (xml == null || xml.isBlank()) return 0;

        List<MapeamentoPardini> pares = extrairMapeamentos(xml);
        if (pares.isEmpty()) {
            logger.info("Nenhum mapeamento encontrado no XML do getResultado");
            return 0;
        }

        String mergeSql = """
            MERGE INTO RERONO_PARDINI_MAPA t
            USING (
                SELECT ? AS COD_PED_LAB, ? AS ANO_COD_PED_APOIO, ? AS COD_PED_APOIO FROM DUAL
            ) s
            ON (t.COD_PED_LAB = s.COD_PED_LAB)
            WHEN MATCHED THEN UPDATE
                SET t.ANO_COD_PED_APOIO = s.ANO_COD_PED_APOIO,
                    t.COD_PED_APOIO = s.COD_PED_APOIO,
                    t.DT_ATUALIZACAO = SYSTIMESTAMP
            WHEN NOT MATCHED THEN INSERT (COD_PED_LAB, ANO_COD_PED_APOIO, COD_PED_APOIO, DT_ATUALIZACAO)
                VALUES (s.COD_PED_LAB, s.ANO_COD_PED_APOIO, s.COD_PED_APOIO, SYSTIMESTAMP)
            """;

        int total = 0;

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(mergeSql)) {

            for (MapeamentoPardini p : pares) {
                ps.setString(1, p.getCodPedLab());
                if (p.getAnoCodPedApoio() != null) ps.setInt(2, p.getAnoCodPedApoio());
                else ps.setNull(2, Types.NUMERIC);
                ps.setString(3, p.getCodPedApoio());
                ps.addBatch();
            }

            int[] results = ps.executeBatch();
            for (int r : results) {
                if (r == Statement.SUCCESS_NO_INFO) total += 1;
                else if (r > 0) total += r;
            }
        }

        return total;
    }

    /**
     * Busca mapeamento completo por CodPedLab.
     */
    public MapeamentoPardini buscarPorCodPedLab(String codPedLab) throws SQLException {
        if (codPedLab == null || codPedLab.isBlank()) return null;

        String sql = """
            SELECT COD_PED_LAB, ANO_COD_PED_APOIO, COD_PED_APOIO
            FROM RERONO_PARDINI_MAPA
            WHERE COD_PED_LAB = ?
            ORDER BY DT_ATUALIZACAO DESC
            FETCH FIRST 1 ROWS ONLY
            """;

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, codPedLab.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String lab = rs.getString("COD_PED_LAB");
                    int ano = rs.getInt("ANO_COD_PED_APOIO");
                    Integer anoObj = rs.wasNull() ? null : ano;
                    String apoio = rs.getString("COD_PED_APOIO");
                    return new MapeamentoPardini(lab, anoObj, apoio);
                }
            }
        }

        return null;
    }

    // =========================================================
    // Parsing do XML do getResultado (robusto / tolerante)
    // =========================================================

    private List<MapeamentoPardini> extrairMapeamentos(String xml) {
        String s = xml;

        // Aceita CodPedLab ou CD_PED_LAB
        String tagLab1 = "CodPedLab";
        String tagLab2 = "CD_PED_LAB";
        String tagApoio = "CodPedApoio";
        String tagAno = "AnoCodPedApoio";

        List<MapeamentoPardini> out = new ArrayList<>();

        // Heurística: pega blocos que tenham CodPedApoio e algum CodPedLab
        // e tenta também capturar AnoCodPedApoio se estiver no mesmo bloco.
        Pattern bloco = Pattern.compile(
                "(<[^>]+>.*?</[^>]+>)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        // Melhor: direto por regex multi-tag (ordens possíveis)
        List<Pattern> patterns = List.of(
                Pattern.compile(
                        "<\\s*(?:" + tagLab1 + "|" + tagLab2 + ")\\s*>(.*?)<\\s*/\\s*(?:" + tagLab1 + "|" + tagLab2 + ")\\s*>.*?" +
                        "<\\s*" + tagApoio + "\\s*>(.*?)<\\s*/\\s*" + tagApoio + "\\s*>.*?" +
                        "(?:<\\s*" + tagAno + "\\s*>(.*?)<\\s*/\\s*" + tagAno + "\\s*>)?",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ),
                Pattern.compile(
                        "<\\s*" + tagApoio + "\\s*>(.*?)<\\s*/\\s*" + tagApoio + "\\s*>.*?" +
                        "<\\s*(?:" + tagLab1 + "|" + tagLab2 + ")\\s*>(.*?)<\\s*/\\s*(?:" + tagLab1 + "|" + tagLab2 + ")\\s*>.*?" +
                        "(?:<\\s*" + tagAno + "\\s*>(.*?)<\\s*/\\s*" + tagAno + "\\s*>)?",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                )
        );

        for (Pattern p : patterns) {
            Matcher m = p.matcher(s);
            while (m.find()) {
                // Depende do pattern: normaliza posições
                String codPedLab;
                String codPedApoio;
                String anoStr;

                if (m.groupCount() >= 2 && m.group(1) != null && m.group(2) != null) {
                    // pattern 1: lab, apoio, ano?
                    if (p.pattern().contains("(?:" + tagLab1)) {
                        codPedLab = limpar(m.group(1));
                        codPedApoio = limpar(m.group(2));
                        anoStr = (m.groupCount() >= 3) ? m.group(3) : null;
                    } else {
                        codPedLab = limpar(m.group(2));
                        codPedApoio = limpar(m.group(1));
                        anoStr = (m.groupCount() >= 3) ? m.group(3) : null;
                    }
                } else {
                    continue;
                }

                Integer ano = parseIntSafe(limpar(anoStr));

                if (!isBlank(codPedLab) && !isBlank(codPedApoio)) {
                    out.add(new MapeamentoPardini(codPedLab, ano, codPedApoio));
                }
            }
        }

        // Dedup por codPedLab (mantém o último)
        Map<String, MapeamentoPardini> dedup = new LinkedHashMap<>();
        for (MapeamentoPardini mp : out) {
            dedup.put(mp.getCodPedLab(), mp);
        }

        List<MapeamentoPardini> finalList = new ArrayList<>(dedup.values());
        logger.info("Mapeamentos extraídos do XML: {}", finalList.size());
        return finalList;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String limpar(String v) {
        if (v == null) return null;
        String s = v.trim();
        s = s.replace("&lt;", "<")
             .replace("&gt;", ">")
             .replace("&quot;", "\"")
             .replace("&apos;", "'")
             .replace("&amp;", "&");
        return s.trim();
    }

    private Integer parseIntSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
