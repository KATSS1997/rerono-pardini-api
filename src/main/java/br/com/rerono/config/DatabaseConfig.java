package br.com.rerono.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private static DatabaseConfig instance;
    private HikariDataSource dataSource;
    
    private DatabaseConfig() {
        initializeDataSource();
    }
    
    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }
    
    private void initializeDataSource() {
        AppConfig config = AppConfig.getInstance();
        
        try {
            HikariConfig hikariConfig = new HikariConfig();
            
            hikariConfig.setJdbcUrl(config.getOracleUrl());
            hikariConfig.setUsername(config.getOracleUsername());
            hikariConfig.setPassword(config.getOraclePassword());
            hikariConfig.setDriverClassName(config.getOracleDriver());
            
            hikariConfig.setMaximumPoolSize(config.getHikariPoolSize());
            hikariConfig.setMinimumIdle(config.getHikariMinIdle());
            hikariConfig.setMaxLifetime(config.getHikariMaxLifetime());
            hikariConfig.setConnectionTimeout(config.getHikariConnectionTimeout());
            hikariConfig.setIdleTimeout(600000);
            
            hikariConfig.setPoolName("ReronoPardinPool");
            
            hikariConfig.addDataSourceProperty("oracle.jdbc.timezoneAsRegion", "false");
            hikariConfig.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", "10000");
            hikariConfig.addDataSourceProperty("oracle.net.READ_TIMEOUT", "60000");
            
            hikariConfig.setConnectionTestQuery("SELECT 1 FROM DUAL");
            hikariConfig.setValidationTimeout(5000);
            hikariConfig.setLeakDetectionThreshold(60000);
            
            this.dataSource = new HikariDataSource(hikariConfig);
            
            try (Connection conn = dataSource.getConnection()) {
                logger.info("Pool de conexões Oracle inicializado com sucesso");
            }
            
        } catch (Exception e) {
            logger.error("Erro ao inicializar pool de conexões: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao inicializar conexão com banco de dados", e);
        }
    }
    
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource não está disponível");
        }
        return dataSource.getConnection();
    }
    
    public DataSource getDataSource() {
        return dataSource;
    }
    
    public String getPoolStats() {
        if (dataSource == null) {
            return "Pool não inicializado";
        }
        return String.format(
            "Conexões: ativas=%d, idle=%d, aguardando=%d, total=%d",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection(),
            dataSource.getHikariPoolMXBean().getTotalConnections()
        );
    }
    
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Fechando pool de conexões...");
            dataSource.close();
            logger.info("Pool de conexões encerrado");
        }
    }
    
    public boolean isActive() {
        return dataSource != null && !dataSource.isClosed();
    }
}