package br.com.rerono.config;

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
        loadProperties();
        loadEnvironmentOverrides();
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
    
    private void loadEnvironmentOverrides() {
        String pardiniPasswd = System.getenv("PARDINI_PASSWD");
        if (pardiniPasswd != null && !pardiniPasswd.isEmpty()) {
            properties.setProperty("pardini.soap.passwd", pardiniPasswd);
        }
        
        String oraclePasswd = System.getenv("ORACLE_PASSWD");
        if (oraclePasswd != null && !oraclePasswd.isEmpty()) {
            properties.setProperty("oracle.jdbc.password", oraclePasswd);
        }
        
        String oracleUrl = System.getenv("ORACLE_URL");
        if (oracleUrl != null && !oracleUrl.isEmpty()) {
            properties.setProperty("oracle.jdbc.url", oracleUrl);
        }
    }
    
    public String getPardiniEndpoint() {
        return properties.getProperty("pardini.soap.endpoint");
    }
    
    public String getPardiniLogin() {
        return properties.getProperty("pardini.soap.login");
    }
    
    public String getPardiniPasswd() {
        return properties.getProperty("pardini.soap.passwd");
    }
    
    public int getPardiniConnectTimeout() {
        return Integer.parseInt(properties.getProperty("pardini.soap.timeout.connect", "30000"));
    }
    
    public int getPardiniReadTimeout() {
        return Integer.parseInt(properties.getProperty("pardini.soap.timeout.read", "60000"));
    }
    
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
    
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
