package br.com.rerono.worker;

import br.com.rerono.codec.Base64Handler;
import br.com.rerono.config.AppConfig;
import br.com.rerono.model.Pedido;
import br.com.rerono.model.ResultadoPardini;
import br.com.rerono.mv2000.Mv2000Integrator;
import br.com.rerono.persistence.PedidoRepository;
import br.com.rerono.soap.HpwsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class IntegracaoWorker {

    private static final Logger logger = LoggerFactory.getLogger(IntegracaoWorker.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");

    private final HpwsClient hpwsClient;
    private final PedidoRepository pedidoRepository;
    private final Mv2000Integrator mv2000Integrator;
    private final ExecutorService executorService;
    private final int batchSize;

    private final AtomicInteger processados = new AtomicInteger(0);
    private final AtomicInteger erros = new AtomicInteger(0);

    private final int tpDocLaudo;
    private final int tpDocGrafico;

    public IntegracaoWorker() {
        AppConfig config = AppConfig.getInstance();

        this.hpwsClient = new HpwsClient();
        this.pedidoRepository = new PedidoRepository();
        this.mv2000Integrator = new Mv2000Integrator();
        this.batchSize = config.getWorkerBatchSize();

        this.tpDocLaudo = config.getMv2000TipoDocumentoLaudo();
        this.tpDocGrafico = config.getMv2000TipoDocumentoGrafico();

        int poolSize = config.getWorkerThreadPoolSize();
        this.executorService = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "IntegracaoWorker-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });

        logger.info("Worker inicializado: poolSize={}, batchSize={}", poolSize, batchSize);
    }

    public int executarCiclo() {
        logger.info("Iniciando ciclo de processamento...");
        processados.set(0);
        erros.set(0);

        try {
            List<Pedido> pedidos = pedidoRepository.buscarPendentes(batchSize);

            if (pedidos.isEmpty()) {
                logger.info("Nenhum pedido pendente encontrado");
                return 0;
            }

            logger.info("Encontrados {} pedidos para processar", pedidos.size());

            List<Future<Boolean>> futures = new java.util.ArrayList<>();

            for (Pedido pedido : pedidos) {
                Future<Boolean> future = executorService.submit(() -> processarPedido(pedido));
                futures.add(future);
            }

            for (Future<Boolean> future : futures) {
                try {
                    future.get(5, TimeUnit.MINUTES);
                } catch (TimeoutException e) {
                    logger.error("Timeout no processamento de pedido");
                    future.cancel(true);
                } catch (Exception e) {
                    logger.error("Erro ao aguardar processamento: {}", e.getMessage());
                }
            }

            logger.info("Ciclo concluído: {} processados, {} erros",
                    processados.get(), erros.get());

            return processados.get();

        } catch (Exception e) {
            logger.error("Erro no ciclo de processamento: {}", e.getMessage(), e);
            return 0;
        }
    }

    private boolean processarPedido(Pedido pedido) {
        String chavePardini = pedido.getChavePardini();
        logger.debug("Processando pedido: {}", chavePardini);

        try {
            pedidoRepository.marcarProcessando(pedido.getIdPedido());

            if (!mv2000Integrator.atendimentoExiste(pedido.getCdAtendimento())) {
                throw new Exception("Atendimento " + pedido.getCdAtendimento() + " não existe no MV2000");
            }

            // Mantive PDF=1 (pedido completo) como estava no projeto
            ResultadoPardini resultado = hpwsClient.getResultadoPedido(
                    pedido.getAnoCodPedApoio(),
                    pedido.getCodPedApoio(),
                    "",
                    1
            );

            if (!resultado.isSucesso()) {
                throw new Exception("Pardini retornou erro: " + resultado.getMensagemErro());
            }

            Long cdArquivoPdf = null;
            Long cdArquivoGrafico = null;

            if (resultado.temPdf()) {
                String hashPdf = resultado.getHashPdf();

                if (!pedidoRepository.existeHash(hashPdf, "PDF")) {
                    String descricao = String.format(
                            "Laudo Hermes Pardini - Pedido %s [HASH:%s]",
                            chavePardini, hashPdf
                    );
                    String nomeArquivo = String.format("LAUDO_%s.PDF", chavePardini);

                    cdArquivoPdf = mv2000Integrator.anexarDocumento(
                            resultado.getPdfBytes(),
                            "PDF",
                            pedido.getCdAtendimento(),
                            pedido.getCdPaciente(),
                            descricao,
                            nomeArquivo,
                            tpDocLaudo
                    );

                    logger.info("PDF anexado: {} -> CD_ARQUIVO_DOCUMENTO={}", chavePardini, cdArquivoPdf);
                } else {
                    logger.info("PDF já processado anteriormente (hash duplicado): {}", hashPdf);
                }
            }

            if (resultado.temGrafico()) {
                String hashGrafico = resultado.getHashGrafico();

                if (!pedidoRepository.existeHash(hashGrafico, "GRAFICO")) {
                    String tipoImagem = Base64Handler.detectFileType(resultado.getGraficoBytes());
                    String descricao = String.format(
                            "Gráfico Eletroforese - Pedido %s [HASH:%s]",
                            chavePardini, hashGrafico
                    );
                    String nomeArquivo = String.format("GRAFICO_%s.%s", chavePardini, tipoImagem);

                    cdArquivoGrafico = mv2000Integrator.anexarDocumento(
                            resultado.getGraficoBytes(),
                            tipoImagem,
                            pedido.getCdAtendimento(),
                            pedido.getCdPaciente(),
                            descricao,
                            nomeArquivo,
                            tpDocGrafico
                    );

                    logger.info("Gráfico anexado: {} -> CD_ARQUIVO_DOCUMENTO={}", chavePardini, cdArquivoGrafico);
                } else {
                    logger.info("Gráfico já processado anteriormente (hash duplicado): {}", hashGrafico);
                }
            }

            pedidoRepository.marcarProcessado(
                    pedido.getIdPedido(),
                    cdArquivoPdf,
                    cdArquivoGrafico,
                    resultado.getHashPdf(),
                    resultado.getHashGrafico()
            );

            auditLogger.info("SUCESSO|{}|{}|{}|PDF={}|GRAFICO={}",
                    pedido.getIdPedido(), chavePardini, pedido.getCdAtendimento(),
                    cdArquivoPdf, cdArquivoGrafico);

            processados.incrementAndGet();
            return true;

        } catch (Exception e) {
            logger.error("Erro ao processar pedido {}: {}", chavePardini, e.getMessage(), e);

            try {
                pedidoRepository.marcarErro(pedido.getIdPedido(), e.getMessage());
            } catch (Exception ex) {
                logger.error("Erro ao marcar pedido com erro: {}", ex.getMessage());
            }

            auditLogger.info("ERRO|{}|{}|{}|{}",
                    pedido.getIdPedido(), chavePardini, pedido.getCdAtendimento(), e.getMessage());

            erros.incrementAndGet();
            return false;
        }
    }

    public void shutdown() {
        logger.info("Encerrando worker...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Worker encerrado");
    }

    public int getProcessados() { return processados.get(); }
    public int getErros() { return erros.get(); }
}
