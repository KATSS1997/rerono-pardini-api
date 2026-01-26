package br.com.rerono.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final String CONFIG_FILE = "application.properties";
    private static AppConfig instance;
    private final Properties properties;

    private AppConfig() {
        this.properties = new Properties();

        // 1) Carrega application.properties (defaults)
        loadProperties();

        // 2) Aplica overrides do .env (se existir) e depois do ambiente do SO
        // Ordem final de prioridade:
        // ENV do SO > .env > application.properties
        loadDotenvOverrides();
        loadEnvironmentOverrides();

        logger.info("Configurações finais carregadas (properties + .env + env do SO)");
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                logger.warn("Arquivo {} não encontrado no classpath", CONFIG_FILE);
                return;
            }
            properties.load(input);
            logger.info("Configurações carregadas de {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Erro ao carregar configurações: {}", e.getMessage(), e);
        }
    }

    private void loadDotenvOverrides() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();

            // Pardini
            setIfPresent(dotenv.get("PARDINI_ENDPOINT"), "pardini.soap.endpoint");
            setIfPresent(dotenv.get("PARDINI_LOGIN"), "pardini.soap.login");
            setIfPresent(dotenv.get("PARDINI_PASSWD"), "pardini.soap.passwd");

            // ✅ Opcional: permitir override de SOAPAction via .env
            setIfPresent(dotenv.get("PARDINI_SOAP_ACTION_GET_RESULTADO_PEDIDO"), "pardini.soap.action.getResultadoPedido");
            setIfPresent(dotenv.get("PARDINI_SOAP_ACTION_GET_RESULTADO"), "pardini.soap.action.getResultado");

            // Oracle
            setIfPresent(dotenv.get("ORACLE_URL"), "oracle.jdbc.url");
            setIfPresent(dotenv.get("ORACLE_USERNAME"), "oracle.jdbc.username");
            setIfPresent(dotenv.get("ORACLE_PASSWD"), "oracle.jdbc.password");

        } catch (Exception e) {
            // Se der erro, não derruba a aplicação (só loga)
            logger.warn("Falha ao carregar .env: {}", e.getMessage());
        }
    }

    private void loadEnvironmentOverrides() {
        // Pardini
        setIfPresent(System.getenv("PARDINI_ENDPOINT"), "pardini.soap.endpoint");
        setIfPresent(System.getenv("PARDINI_LOGIN"), "pardini.soap.login");
        setIfPresent(System.getenv("PARDINI_PASSWD"), "pardini.soap.passwd");

        // ✅ Opcional: permitir override de SOAPAction via ENV do SO
        setIfPresent(System.getenv("PARDINI_SOAP_ACTION_GET_RESULTADO_PEDIDO"), "pardini.soap.action.getResultadoPedido");
        setIfPresent(System.getenv("PARDINI_SOAP_ACTION_GET_RESULTADO"), "pardini.soap.action.getResultado");

        // Oracle
        setIfPresent(System.getenv("ORACLE_URL"), "oracle.jdbc.url");
        setIfPresent(System.getenv("ORACLE_USERNAME"), "oracle.jdbc.username");
        setIfPresent(System.getenv("ORACLE_PASSWD"), "oracle.jdbc.password");
    }

    private void setIfPresent(String value, String propertyKey) {
        if (value != null && !value.trim().isEmpty()) {
            properties.setProperty(propertyKey, value.trim());
        }
    }

    // ===== PARDINI =====
    public String getPardiniEndpoint() {
        return properties.getProperty("pardini.soap.endpoint");
    }

    public String getPardiniLogin() {
        return properties.getProperty("pardini.soap.login");
    }

    public String getPardiniPasswd() {
        return properties.getProperty("pardini.soap.passwd");
    }

    public String getPardiniSoapActionGetResultadoPedido() {
        return properties.getProperty(
                "pardini.soap.action.getResultadoPedido",
                "http://hermespardini.com.br/b2b/apoio/schemas/HPWS.XMLServer.getResultadoPedido"
        );
    }

    // ✅ NOVO: usado pelo HpwsClient.getResultado(...)
    public String getPardiniSoapActionGetResultado() {
        return properties.getProperty(
                "pardini.soap.action.getResultado",
                "http://hermespardini.com.br/b2b/apoio/schemas/HPWS.XMLServer.getResultado"
        );
    }

    public int getPardiniConnectTimeout() {
        return Integer.parseInt(properties.getProperty("pardini.soap.timeout.connect", "30000"));
    }

    public int getPardiniReadTimeout() {
        return Integer.parseInt(properties.getProperty("pardini.soap.timeout.read", "60000"));
    }

    // ===== ORACLE =====
    public String getOracleUrl() {
        return properties.getProperty("oracle.jdbc.url");
    }

    public String getOracleUsername() {
        return properties.getProperty("oracle.jdbc.username");
    }

    public String getOraclePassword() {
        return properties.getProperty("oracle.jdbc.password");
    }

    public String getOracleDriver() {
        return properties.getProperty("oracle.jdbc.driver", "oracle.jdbc.OracleDriver");
    }

    // ===== HIKARI =====
    public int getHikariPoolSize() {
        return Integer.parseInt(properties.getProperty("hikari.pool.size", "10"));
    }

    public int getHikariMinIdle() {
        return Integer.parseInt(properties.getProperty("hikari.pool.min-idle", "5"));
    }

    public long getHikariMaxLifetime() {
        return Long.parseLong(properties.getProperty("hikari.pool.max-lifetime", "1800000"));
    }

    public long getHikariConnectionTimeout() {
        return Long.parseLong(properties.getProperty("hikari.pool.connection-timeout", "30000"));
    }

    // ===== MV2000 =====
    public int getMv2000TipoDocumentoLaudo() {
        return Integer.parseInt(properties.getProperty("mv2000.tipo.documento.laudo", "841"));
    }

    public int getMv2000TipoDocumentoGrafico() {
        return Integer.parseInt(properties.getProperty("mv2000.tipo.documento.grafico", "841"));
    }

    public String getMv2000UsuarioIntegracao() {
        return properties.getProperty("mv2000.usuario.integracao", "RERONO_API");
    }

    public String getMv2000OrigemDocumento() {
        return properties.getProperty("mv2000.origem.documento", "HERMES PARDINI - HPWS");
    }

    // ===== WORKER/SCHEDULER =====
    public int getSchedulerIntervalMinutes() {
        return Integer.parseInt(properties.getProperty("scheduler.interval.minutes", "5"));
    }

    public int getWorkerMaxTentativas() {
        return Integer.parseInt(properties.getProperty("worker.max.tentativas", "3"));
    }

    public int getWorkerThreadPoolSize() {
        return Integer.parseInt(properties.getProperty("worker.thread.pool.size", "5"));
    }

    public int getWorkerBatchSize() {
        return Integer.parseInt(properties.getProperty("worker.batch.size", "50"));
    }

    // ===== GET GENÉRICO =====
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
