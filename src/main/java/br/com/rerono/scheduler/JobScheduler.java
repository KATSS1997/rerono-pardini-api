package br.com.rerono.scheduler;

import br.com.rerono.config.AppConfig;
import br.com.rerono.worker.IntegracaoWorker;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agendador de tarefas usando Quartz.
 * Executa o worker de integração em intervalos configuráveis.
 */
public class JobScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(JobScheduler.class);
    
    private Scheduler scheduler;
    private IntegracaoWorker worker;
    
    public void iniciar() throws SchedulerException {
        AppConfig config = AppConfig.getInstance();
        int intervaloMinutos = config.getSchedulerIntervalMinutes();
        
        logger.info("Inicializando scheduler com intervalo de {} minutos", intervaloMinutos);
        
        // Criar worker
        worker = new IntegracaoWorker();
        
        // Configurar Quartz
        scheduler = StdSchedulerFactory.getDefaultScheduler();
        
        // Definir job
        JobDetail job = JobBuilder.newJob(IntegracaoJob.class)
            .withIdentity("integracaoJob", "rerono")
            .build();
        
        // Passar worker para o job
        job.getJobDataMap().put("worker", worker);
        
        // Definir trigger (execução periódica)
        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("integracaoTrigger", "rerono")
            .startNow()
            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMinutes(intervaloMinutos)
                .repeatForever())
            .build();
        
        // Agendar
        scheduler.scheduleJob(job, trigger);
        scheduler.start();
        
        logger.info("Scheduler iniciado. Próxima execução em {} minutos", intervaloMinutos);
    }
    
    /**
     * Executa o worker imediatamente (fora do agendamento).
     */
    public void executarAgora() {
        if (worker != null) {
            logger.info("Executando worker sob demanda...");
            worker.executarCiclo();
        } else {
            logger.warn("Worker não inicializado");
        }
    }
    
    /**
     * Para o scheduler de forma graciosa.
     */
    public void parar() {
        try {
            if (scheduler != null && !scheduler.isShutdown()) {
                logger.info("Parando scheduler...");
                scheduler.shutdown(true); // true = aguardar jobs em execução
            }
            if (worker != null) {
                worker.shutdown();
            }
            logger.info("Scheduler parado com sucesso");
        } catch (SchedulerException e) {
            logger.error("Erro ao parar scheduler: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Verifica se o scheduler está em execução.
     */
    public boolean isRunning() {
        try {
            return scheduler != null && scheduler.isStarted() && !scheduler.isShutdown();
        } catch (SchedulerException e) {
            return false;
        }
    }
    
    /**
     * Job do Quartz que executa o worker.
     */
    public static class IntegracaoJob implements Job {
        
        private static final Logger jobLogger = LoggerFactory.getLogger(IntegracaoJob.class);
        
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            IntegracaoWorker worker = (IntegracaoWorker) context.getJobDetail()
                .getJobDataMap().get("worker");
            
            if (worker == null) {
                jobLogger.error("Worker não encontrado no contexto do job");
                return;
            }
            
            jobLogger.info("=== Iniciando execução agendada ===");
            long inicio = System.currentTimeMillis();
            
            try {
                int processados = worker.executarCiclo();
                long duracao = System.currentTimeMillis() - inicio;
                
                jobLogger.info("=== Execução concluída: {} pedidos em {}ms ===", 
                    processados, duracao);
                
            } catch (Exception e) {
                jobLogger.error("Erro na execução do job: {}", e.getMessage(), e);
                throw new JobExecutionException(e);
            }
        }
    }
}