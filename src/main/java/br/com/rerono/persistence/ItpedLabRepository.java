package br.com.rerono.persistence;

import br.com.rerono.config.DatabaseConfig;
import br.com.rerono.model.PedidoLabPendente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ItpedLabRepository {

    private static final Logger logger = LoggerFactory.getLogger(ItpedLabRepository.class);

    private final DatabaseConfig dbConfig;

    public ItpedLabRepository() {
        this.dbConfig = DatabaseConfig.getInstance();
    }

    /**
     * Busca CD_PED_LAB na ITPED_LAB com SN_ASSINADO='N'
     * Regras:
     * - Só precisa do CD_PED_LAB
     * - Dedup (DISTINCT) para evitar processar repetido
     * - A data pode ser tratada como SYSDATE do lado da aplicação (LocalDateTime.now()) no getResultado.
     */
    public List<PedidoLabPendente> buscarPendentesAssinatura(int limite) throws SQLException {

        String sql = """
            SELECT DISTINCT
                i.CD_PED_LAB,
                i.SN_ASSINADO
            FROM ITPED_LAB i
            WHERE i.SN_ASSINADO = 'N'
              AND i.CD_PED_LAB IS NOT NULL
            ORDER BY i.CD_PED_LAB
            FETCH FIRST ? ROWS ONLY
            """;

        List<PedidoLabPendente> out = new ArrayList<>();

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limite);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cdPedLab = rs.getString("CD_PED_LAB");
                    String snAssinado = rs.getString("SN_ASSINADO");
                    out.add(new PedidoLabPendente(cdPedLab, snAssinado));
                }
            }
        }

        logger.info("ITPED_LAB pendentes (SN_ASSINADO='N'): {}", out.size());
        return out;
    }
}
