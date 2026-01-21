package br.com.rerono.mv2000;

import br.com.rerono.config.AppConfig;
import br.com.rerono.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class Mv2000Integrator {
    
    private static final Logger logger = LoggerFactory.getLogger(Mv2000Integrator.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    
    private final DatabaseConfig dbConfig;
    private final AppConfig appConfig;
    
    public Mv2000Integrator() {
        this.dbConfig = DatabaseConfig.getInstance();
        this.appConfig = AppConfig.getInstance();
    }
    
    public Long anexarDocumento(byte[] conteudo, String extensao, 
                                 Long cdAtendimento, Long cdPaciente,
                                 String descricao, String nomeArquivo) throws SQLException {
        
        if (conteudo == null || conteudo.length == 0) {
            throw new IllegalArgumentException("Conteúdo do documento não pode ser vazio");
        }
        
        Connection conn = null;
        Long cdArquivoDocumento = null;
        
        try {
            conn = dbConfig.getConnection();
            conn.setAutoCommit(false);
            
            cdArquivoDocumento = inserirArquivoDocumento(conn, conteudo, extensao, nomeArquivo);
            
            inserirArquivoAtendimento(conn, cdArquivoDocumento, cdAtendimento, cdPaciente, descricao);
            
            conn.commit();
            
            auditLogger.info("ANEXAR|{}|{}|{}|{}|{} bytes|{}",
                cdArquivoDocumento, cdAtendimento, cdPaciente, extensao, conteudo.length, nomeArquivo);
            
            logger.info("Documento anexado: CD_ARQUIVO_DOCUMENTO={}, Atendimento={}, {} bytes",
                cdArquivoDocumento, cdAtendimento, conteudo.length);
            
            return cdArquivoDocumento;
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                    logger.error("Rollback executado devido a erro: {}", e.getMessage());
                } catch (SQLException ex) {
                    logger.error("Erro no rollback: {}", ex.getMessage());
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Erro ao fechar conexão: {}", e.getMessage());
                }
            }
        }
    }
    
    private Long inserirArquivoDocumento(Connection conn, byte[] conteudo, 
                                          String extensao, String nomeArquivo) throws SQLException {
        
        Long proximoId = obterProximoIdArquivoDocumento(conn);
        
        String sql = """
            INSERT INTO ARQUIVO_DOCUMENTO (
                CD_ARQUIVO_DOCUMENTO,
                LO_ARQUIVO_DOCUMENTO,
                TP_EXTENSAO,
                DS_AUTOR,
                DS_ORIGEM,
                DT_DOCUMENTO,
                DS_NOME_ARQUIVO
            ) VALUES (?, ?, ?, ?, ?, SYSDATE, ?)
            """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, proximoId);
            ps.setBytes(2, conteudo);
            ps.setString(3, extensao.toUpperCase());
            ps.setString(4, appConfig.getMv2000UsuarioIntegracao());
            ps.setString(5, appConfig.getMv2000OrigemDocumento());
            ps.setString(6, nomeArquivo);
            
            ps.executeUpdate();
            
            logger.debug("Inserido ARQUIVO_DOCUMENTO: ID={}, Extensão={}", proximoId, extensao);
            return proximoId;
        }
    }
    
    private Long obterProximoIdArquivoDocumento(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT SEQ_ARQUIVO_DOCUMENTO.NEXTVAL FROM DUAL")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.debug("Sequência não encontrada, usando MAX+1");
        }
        
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT NVL(MAX(CD_ARQUIVO_DOCUMENTO), 0) + 1 FROM ARQUIVO_DOCUMENTO")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        
        throw new SQLException("Não foi possível obter ID para ARQUIVO_DOCUMENTO");
    }
    
    private void inserirArquivoAtendimento(Connection conn, Long cdArquivoDocumento,
                                            Long cdAtendimento, Long cdPaciente,
                                            String descricao) throws SQLException {
        
        Long proximoId = obterProximoIdArquivoAtendimento(conn);
        
        String sql = """
            INSERT INTO ARQUIVO_ATENDIMENTO (
                CD_ARQUIVO_ATENDIMENTO,
                CD_ARQUIVO_DOCUMENTO,
                CD_ATENDIMENTO,
                CD_PACIENTE,
                CD_TIPO_DOCUMENTO,
                DH_CRIACAO,
                NM_USUARIO,
                DS_DESCRICAO
            ) VALUES (?, ?, ?, ?, ?, SYSDATE, ?, ?)
            """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, proximoId);
            ps.setLong(2, cdArquivoDocumento);
            ps.setLong(3, cdAtendimento);
            
            if (cdPaciente != null) {
                ps.setLong(4, cdPaciente);
            } else {
                ps.setNull(4, Types.NUMERIC);
            }
            
            ps.setInt(5, appConfig.getMv2000TipoDocumentoLaudo());
            ps.setString(6, appConfig.getMv2000UsuarioIntegracao());
            ps.setString(7, descricao);
            
            ps.executeUpdate();
            
            logger.debug("Inserido ARQUIVO_ATENDIMENTO: ID={}, Atendimento={}", proximoId, cdAtendimento);
        }
    }
    
    private Long obterProximoIdArquivoAtendimento(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT SEQ_ARQUIVO_ATENDIMENTO.NEXTVAL FROM DUAL")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.debug("Sequência ARQUIVO_ATENDIMENTO não encontrada, usando MAX+1");
        }
        
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT NVL(MAX(CD_ARQUIVO_ATENDIMENTO), 0) + 1 FROM ARQUIVO_ATENDIMENTO")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        
        throw new SQLException("Não foi possível obter ID para ARQUIVO_ATENDIMENTO");
    }
    
    public boolean atendimentoExiste(Long cdAtendimento) throws SQLException {
        String sql = "SELECT 1 FROM ATENDIME WHERE CD_ATENDIMENTO = ?";
        
        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, cdAtendimento);
            
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    public Long obterPacienteDoAtendimento(Long cdAtendimento) throws SQLException {
        String sql = "SELECT CD_PACIENTE FROM ATENDIME WHERE CD_ATENDIMENTO = ?";
        
        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, cdAtendimento);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        
        return null;
    }
}