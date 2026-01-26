package br.com.rerono.persistence;

import br.com.rerono.config.AppConfig;
import br.com.rerono.config.DatabaseConfig;
import br.com.rerono.model.Pedido;
import br.com.rerono.model.Pedido.StatusPedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PedidoRepository {

    private static final Logger logger = LoggerFactory.getLogger(PedidoRepository.class);
    private final DatabaseConfig dbConfig;
    private final int maxTentativas;

    public PedidoRepository() {
        this.dbConfig = DatabaseConfig.getInstance();
        this.maxTentativas = AppConfig.getInstance().getWorkerMaxTentativas();
    }

    public Long inserir(Pedido pedido) throws SQLException {
        String sql = """
            INSERT INTO RERONO_PEDIDO (
                ANO_COD_PED_APOIO, COD_PED_APOIO, CD_ATENDIMENTO, CD_PACIENTE,
                STATUS, TENTATIVAS, DT_CRIACAO
            ) VALUES (?, ?, ?, ?, ?, ?, SYSTIMESTAMP)
            """;

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"ID_PEDIDO"})) {

            ps.setInt(1, pedido.getAnoCodPedApoio());
            ps.setString(2, pedido.getCodPedApoio());
            ps.setLong(3, pedido.getCdAtendimento());
            if (pedido.getCdPaciente() != null) {
                ps.setLong(4, pedido.getCdPaciente());
            } else {
                ps.setNull(4, Types.NUMERIC);
            }
            ps.setString(5, StatusPedido.PENDENTE.name());
            ps.setInt(6, 0);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    pedido.setIdPedido(id);
                    logger.info("Pedido inserido: ID={}, Pardini={}", id, pedido.getChavePardini());
                    return id;
                }
            }
        }

        return null;
    }

    public List<Pedido> buscarPendentes(int limite) throws SQLException {
        String sql = """
            SELECT ID_PEDIDO, ANO_COD_PED_APOIO, COD_PED_APOIO, CD_ATENDIMENTO,
                   CD_PACIENTE, STATUS, TENTATIVAS, DT_CRIACAO, DT_PROCESSAMENTO,
                   CD_ARQUIVO_PDF, CD_ARQUIVO_GRAFICO, HASH_PDF, HASH_GRAFICO, ERRO_MSG
            FROM RERONO_PEDIDO
            WHERE STATUS IN ('PENDENTE', 'ERRO')
              AND TENTATIVAS < ?
            ORDER BY DT_CRIACAO ASC
            FETCH FIRST ? ROWS ONLY
            FOR UPDATE SKIP LOCKED
            """;

        List<Pedido> pedidos = new ArrayList<>();

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, maxTentativas);
            ps.setInt(2, limite);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pedidos.add(mapearPedido(rs));
                }
            }
        }

        logger.debug("Encontrados {} pedidos pendentes", pedidos.size());
        return pedidos;
    }

    public Optional<Pedido> buscarPorId(Long idPedido) throws SQLException {
        String sql = """
            SELECT ID_PEDIDO, ANO_COD_PED_APOIO, COD_PED_APOIO, CD_ATENDIMENTO,
                   CD_PACIENTE, STATUS, TENTATIVAS, DT_CRIACAO, DT_PROCESSAMENTO,
                   CD_ARQUIVO_PDF, CD_ARQUIVO_GRAFICO, HASH_PDF, HASH_GRAFICO, ERRO_MSG
            FROM RERONO_PEDIDO
            WHERE ID_PEDIDO = ?
            """;

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, idPedido);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearPedido(rs));
                }
            }
        }

        return Optional.empty();
    }

    public Optional<Pedido> buscarPorChavePardini(int anoCodPedApoio, String codPedApoio) throws SQLException {
        String sql = """
            SELECT ID_PEDIDO, ANO_COD_PED_APOIO, COD_PED_APOIO, CD_ATENDIMENTO,
                   CD_PACIENTE, STATUS, TENTATIVAS, DT_CRIACAO, DT_PROCESSAMENTO,
                   CD_ARQUIVO_PDF, CD_ARQUIVO_GRAFICO, HASH_PDF, HASH_GRAFICO, ERRO_MSG
            FROM RERONO_PEDIDO
            WHERE ANO_COD_PED_APOIO = ? AND COD_PED_APOIO = ?
            """;

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, anoCodPedApoio);
            ps.setString(2, codPedApoio);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearPedido(rs));
                }
            }
        }

        return Optional.empty();
    }

    public void marcarProcessando(Long idPedido) throws SQLException {
        String sql = """
            UPDATE RERONO_PEDIDO
            SET STATUS = 'PROCESSANDO',
                TENTATIVAS = TENTATIVAS + 1
            WHERE ID_PEDIDO = ?
            """;

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, idPedido);
            ps.executeUpdate();
        }
    }

    public void marcarProcessado(Long idPedido, Long cdArquivoPdf, Long cdArquivoGrafico,
                                 String hashPdf, String hashGrafico) throws SQLException {
        String sql = """
            UPDATE RERONO_PEDIDO
            SET STATUS = 'PROCESSADO',
                DT_PROCESSAMENTO = SYSTIMESTAMP,
                CD_ARQUIVO_PDF = ?,
                CD_ARQUIVO_GRAFICO = ?,
                HASH_PDF = ?,
                HASH_GRAFICO = ?,
                ERRO_MSG = NULL
            WHERE ID_PEDIDO = ?
            """;

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (cdArquivoPdf != null) {
                ps.setLong(1, cdArquivoPdf);
            } else {
                ps.setNull(1, Types.NUMERIC);
            }

            if (cdArquivoGrafico != null) {
                ps.setLong(2, cdArquivoGrafico);
            } else {
                ps.setNull(2, Types.NUMERIC);
            }

            ps.setString(3, hashPdf);
            ps.setString(4, hashGrafico);
            ps.setLong(5, idPedido);

            ps.executeUpdate();
            logger.info("Pedido {} marcado como PROCESSADO", idPedido);
        }
    }

    public void marcarErro(Long idPedido, String mensagemErro) throws SQLException {
        String sql = """
            UPDATE RERONO_PEDIDO
            SET STATUS = 'ERRO',
                ERRO_MSG = ?
            WHERE ID_PEDIDO = ?
            """;

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String erro = mensagemErro != null && mensagemErro.length() > 4000
                    ? mensagemErro.substring(0, 4000)
                    : mensagemErro;

            ps.setString(1, erro);
            ps.setLong(2, idPedido);

            ps.executeUpdate();
            logger.warn("Pedido {} marcado como ERRO: {}", idPedido, erro);
        }
    }

    // ✅ NOVO: usado quando ainda não existe mapeamento CodPedLab -> CodPedApoio
    public void marcarPendenteNovamente(Long idPedido, String mensagem) throws SQLException {
        String sql = """
            UPDATE RERONO_PEDIDO
            SET STATUS = 'PENDENTE',
                ERRO_MSG = ?,
                DT_PROCESSAMENTO = NULL
            WHERE ID_PEDIDO = ?
            """;

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String msg = mensagem != null && mensagem.length() > 4000
                    ? mensagem.substring(0, 4000)
                    : mensagem;

            ps.setString(1, msg);
            ps.setLong(2, idPedido);

            ps.executeUpdate();
        }
    }

    // ✅ NOVO: salva o CodPedApoio resolvido no controle (RERONO_PEDIDO)
    public void atualizarCodPedApoio(Long idPedido, String codPedApoio) throws SQLException {
        String sql = """
            UPDATE RERONO_PEDIDO
            SET COD_PED_APOIO = ?,
                ERRO_MSG = NULL
            WHERE ID_PEDIDO = ?
            """;

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, codPedApoio);
            ps.setLong(2, idPedido);
            ps.executeUpdate();
        }
    }

    public boolean existeHash(String hash, String tipo) throws SQLException {
        if (hash == null || hash.isEmpty()) {
            return false;
        }

        String coluna = "PDF".equalsIgnoreCase(tipo) ? "HASH_PDF" : "HASH_GRAFICO";
        String sql = "SELECT 1 FROM RERONO_PEDIDO WHERE " + coluna + " = ? AND STATUS = 'PROCESSADO' FETCH FIRST 1 ROWS ONLY";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, hash);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int contarPorStatus(StatusPedido status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM RERONO_PEDIDO WHERE STATUS = ?";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    private Pedido mapearPedido(ResultSet rs) throws SQLException {
        Pedido pedido = new Pedido();
        pedido.setIdPedido(rs.getLong("ID_PEDIDO"));
        pedido.setAnoCodPedApoio(rs.getInt("ANO_COD_PED_APOIO"));
        pedido.setCodPedApoio(rs.getString("COD_PED_APOIO"));
        pedido.setCdAtendimento(rs.getLong("CD_ATENDIMENTO"));

        long cdPaciente = rs.getLong("CD_PACIENTE");
        if (!rs.wasNull()) {
            pedido.setCdPaciente(cdPaciente);
        }

        pedido.setStatus(StatusPedido.valueOf(rs.getString("STATUS")));
        pedido.setTentativas(rs.getInt("TENTATIVAS"));

        Timestamp dtCriacao = rs.getTimestamp("DT_CRIACAO");
        if (dtCriacao != null) {
            pedido.setDtCriacao(dtCriacao.toLocalDateTime());
        }

        Timestamp dtProc = rs.getTimestamp("DT_PROCESSAMENTO");
        if (dtProc != null) {
            pedido.setDtProcessamento(dtProc.toLocalDateTime());
        }

        long cdArqPdf = rs.getLong("CD_ARQUIVO_PDF");
        if (!rs.wasNull()) {
            pedido.setCdArquivoPdf(cdArqPdf);
        }

        long cdArqGrafico = rs.getLong("CD_ARQUIVO_GRAFICO");
        if (!rs.wasNull()) {
            pedido.setCdArquivoGrafico(cdArqGrafico);
        }

        pedido.setHashPdf(rs.getString("HASH_PDF"));
        pedido.setHashGrafico(rs.getString("HASH_GRAFICO"));
        pedido.setErroMsg(rs.getString("ERRO_MSG"));

        return pedido;
    }
}
