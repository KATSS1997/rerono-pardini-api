package br.com.rerono;

import br.com.rerono.config.AppConfig;
import br.com.rerono.config.DatabaseConfig;
import br.com.rerono.model.ResultadoPardini;
import br.com.rerono.scheduler.JobScheduler;
import br.com.rerono.soap.HpwsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classe principal da aplicação Rerono Pardini API.
 * Integração entre Hermes Pardini (HPWS.XMLServer) e MV2000.
 */
public class Application {
    
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    
    private static JobScheduler scheduler;
    
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("  RERONO PARDINI API - Iniciando...");
        logger.info("========================================");
        
        try {
            // Carregar configurações
            AppConfig config = AppConfig.getInstance();
            logger.info("Configurações carregadas");
            
            // Verificar argumentos
            if (args.length > 0) {
                processarArgumentos(args);
                return;
            }
            
            // Validar pré-requisitos
            validarConexoes();
            
            // Registrar shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Recebido sinal de shutdown...");
                shutdown();
            }));
            
            // Iniciar scheduler
            scheduler = new JobScheduler();
            scheduler.iniciar();
            
            logger.info("========================================");
            logger.info("  Aplicação iniciada com sucesso!");
            logger.info("  Pressione Ctrl+C para encerrar");
            logger.info("========================================");
            
            // Manter aplicação rodando
            Thread.currentThread().join();
            
        } catch (Exception e) {
            logger.error("Erro fatal ao iniciar aplicação: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
    
    /**
     * Processa argumentos de linha de comando.
     */
    private static void processarArgumentos(String[] args) {
        String comando = args[0].toLowerCase();
        
        switch (comando) {
            case "--help":
            case "-h":
                exibirAjuda();
                break;
                
            case "--test-db":
                testarConexaoDB();
                break;
                
            case "--test-soap":
                testarConexaoSOAP();
                break;
                
            case "--test-getresultadopedido":
                testarGetResultadoPedido(args);
                break;
                
            case "--run-once":
                executarUmaVez();
                break;
                
            case "--version":
            case "-v":
                logger.info("Rerono Pardini API v1.0.0-SNAPSHOT");
                break;
                
            default:
                logger.error("Comando desconhecido: {}", comando);
                exibirAjuda();
                System.exit(1);
        }
    }
    
    private static void exibirAjuda() {
        System.out.println("""
            
            Rerono Pardini API - Integração Hermes Pardini / MV2000
            
            Uso: java -jar rerono-pardini-api.jar [opção]
            
            Opções:
              (sem opções)    Inicia o scheduler em modo contínuo
              --help, -h      Exibe esta ajuda
              --version, -v   Exibe a versão
              --test-db       Testa conexão com Oracle
              --test-soap     Testa conexão com Hermes Pardini (WSDL)
              --test-getResultadoPedido <ano> <codPedido> <pdf>
                              Testa chamada SOAP getResultadoPedido
                              Exemplo: --test-getResultadoPedido 2026 1419652 0
              --run-once      Executa um ciclo e encerra
            
            Variáveis de ambiente:
              PARDINI_PASSWD  Senha do Hermes Pardini
              ORACLE_PASSWD   Senha do Oracle
              ORACLE_URL      URL de conexão Oracle (sobrescreve properties)
            
            """);
    }
    
    private static void validarConexoes() {
        logger.info("Validando conexões...");
        
        // Testar Oracle
        try {
            DatabaseConfig.getInstance().getConnection().close();
            logger.info("✓ Conexão Oracle OK");
        } catch (Exception e) {
            logger.error("✗ Falha na conexão Oracle: {}", e.getMessage());
            throw new RuntimeException("Não foi possível conectar ao Oracle", e);
        }
        
        // Testar SOAP (opcional, não bloqueia)
        try {
            HpwsClient client = new HpwsClient();
            if (client.testarConexao()) {
                logger.info("✓ Conexão Hermes Pardini OK");
            } else {
                logger.warn("⚠ Conexão Hermes Pardini não verificada (pode estar indisponível)");
            }
        } catch (Exception e) {
            logger.warn("⚠ Não foi possível testar conexão Pardini: {}", e.getMessage());
        }
    }
    
    private static void testarConexaoDB() {
        logger.info("Testando conexão com Oracle...");
        try {
            var conn = DatabaseConfig.getInstance().getConnection();
            var stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT SYSDATE FROM DUAL");
            if (rs.next()) {
                logger.info("✓ Conexão OK - Data do servidor: {}", rs.getString(1));
            }
            rs.close();
            stmt.close();
            conn.close();
            
            // Mostrar estatísticas do pool
            logger.info("Pool: {}", DatabaseConfig.getInstance().getPoolStats());
            
        } catch (Exception e) {
            logger.error("✗ Falha: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
    
    private static void testarConexaoSOAP() {
        logger.info("Testando conexão com Hermes Pardini...");
        try {
            HpwsClient client = new HpwsClient();
            if (client.testarConexao()) {
                logger.info("✓ Endpoint acessível");
            } else {
                logger.error("✗ Endpoint não acessível");
                System.exit(1);
            }
        } catch (Exception e) {
            logger.error("✗ Falha: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
    
    /**
     * Testa a chamada SOAP getResultadoPedido com parâmetros da linha de comando.
     * Uso: --test-getResultadoPedido <ano> <codPedido> <pdf>
     * Exemplo: --test-getResultadoPedido 2026 1419652 0
     */
    private static void testarGetResultadoPedido(String[] args) {
        if (args.length < 4) {
            logger.error("Uso: --test-getResultadoPedido <ano> <codPedido> <pdf>");
            logger.error("Exemplo: --test-getResultadoPedido 2026 1419652 0");
            logger.error("  <ano>       = Ano do código do pedido (ex: 2026)");
            logger.error("  <codPedido> = Código do pedido no Pardini (ex: 1419652)");
            logger.error("  <pdf>       = 0 = sem PDF, 1 = com PDF");
            System.exit(1);
            return;
        }
        
        try {
            int anoCodPedApoio = Integer.parseInt(args[1]);
            String codPedApoio = args[2];
            int incluirPdf = Integer.parseInt(args[3]);
            
            logger.info("========================================");
            logger.info("  TESTE: getResultadoPedido");
            logger.info("========================================");
            logger.info("Parâmetros:");
            logger.info("  anoCodPedApoio = {}", anoCodPedApoio);
            logger.info("  codPedApoio    = {}", codPedApoio);
            logger.info("  incluirPdf     = {}", incluirPdf);
            logger.info("----------------------------------------");
            
            HpwsClient client = new HpwsClient();
            
            logger.info("Executando chamada SOAP...");
            long inicio = System.currentTimeMillis();
            
            ResultadoPardini resultado = client.getResultadoPedido(anoCodPedApoio, codPedApoio, incluirPdf);
            
            long duracao = System.currentTimeMillis() - inicio;
            
            logger.info("----------------------------------------");
            logger.info("Resultado ({}ms):", duracao);
            logger.info("  Sucesso: {}", resultado.isSucesso());
            
            if (resultado.getMensagemErro() != null && !resultado.getMensagemErro().isEmpty()) {
                logger.warn("  Mensagem Erro: {}", resultado.getMensagemErro());
            }
            
            if (resultado.getCodigoRetorno() != null) {
                logger.info("  Código Retorno: {}", resultado.getCodigoRetorno());
            }
            
            if (resultado.temPdf()) {
                logger.info("  PDF: {} bytes (hash: {})", 
                    resultado.getTamanhoPdf(), 
                    resultado.getHashPdf());
            } else {
                logger.info("  PDF: não retornado");
            }
            
            if (resultado.temGrafico()) {
                logger.info("  Gráfico: {} bytes (hash: {})", 
                    resultado.getTamanhoGrafico(), 
                    resultado.getHashGrafico());
            } else {
                logger.info("  Gráfico: não retornado");
            }
            
            // Mostrar XML se DEBUG habilitado ou se houve erro
            if (!resultado.isSucesso() && resultado.getXmlOriginal() != null) {
                logger.info("----------------------------------------");
                logger.info("XML Response (primeiros 2000 chars):");
                String xml = resultado.getXmlOriginal();
                if (xml.length() > 2000) {
                    xml = xml.substring(0, 2000) + "...";
                }
                logger.info("{}", xml);
            }
            
            logger.info("========================================");
            
            if (!resultado.isSucesso()) {
                System.exit(1);
            }
            
        } catch (NumberFormatException e) {
            logger.error("Parâmetros inválidos. <ano> e <pdf> devem ser números inteiros.");
            logger.error("Exemplo: --test-getResultadoPedido 2026 1419652 0");
            System.exit(1);
        } catch (Exception e) {
            logger.error("Erro ao testar getResultadoPedido: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
    
    private static void executarUmaVez() {
        logger.info("Executando ciclo único...");
        try {
            validarConexoes();
            
            var worker = new br.com.rerono.worker.IntegracaoWorker();
            int processados = worker.executarCiclo();
            
            logger.info("Ciclo concluído: {} pedidos processados", processados);
            worker.shutdown();
            
        } catch (Exception e) {
            logger.error("Erro: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
    
    private static void shutdown() {
        logger.info("Encerrando aplicação...");
        
        if (scheduler != null) {
            scheduler.parar();
        }
        
        DatabaseConfig.getInstance().shutdown();
        
        logger.info("Aplicação encerrada.");
    }
}