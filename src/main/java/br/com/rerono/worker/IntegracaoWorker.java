package br.com.rerono.worker;

import br.com.rerono.codec.Base64Handler;
import br.com.rerono.config.AppConfig;
import br.com.rerono.model.PedidoLabPendente;
import br.com.rerono.model.ResultadoPardini;
import br.com.rerono.mv2000.Mv2000Integrator;
import br.com.rerono.persistence.ItpedLabRepository;
import br.com.rerono.persistence.PardiniMapaRepository;
import br.com.rerono.persistence.PardiniMapaRepository.MapeamentoPardini;
import br.com.rerono.soap.HpwsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class IntegracaoWorker {

    private static final Logger logger = LoggerFactory.getLogger(IntegracaoWorker.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");

    private final HpwsClient hpwsClient;
    private final PardiniMapaRepository mapaRepository;
    private final ItpedLabRepository itpedLabRepository;
    private final Mv2000Integrator mv2000Integrator;

    private final ExecutorService executorService;
    private final int batchSize;

    private final AtomicInteger processados = new AtomicInteger(0);
    private final AtomicInteger erros = new AtomicInteger(0);

    private final int tpDocLaudo;
    private final int tpDocGrafico;

    // Janela do getResultado (ex.: 24h)
    private final int janelaHoras;

    public IntegracaoWorker() {
        AppConfig config = AppConfig.getInstance();

        this.hpwsClient = new HpwsClient();
        this.mapaRepository = new PardiniMapaRepository();
        this.itpedLabRepository = new ItpedLabRepository();
        this.mv2000Integrator = new Mv2000Integrator();

        this.batchSize = config.getWorkerBatchSize();
        this.tpDocLaudo = config.getMv2000TipoDocumentoLaudo();
        this.tpDocGrafico = config.getMv2000TipoDocumentoGrafico();
        this.janelaHoras = Integer.parseInt(config.getProperty("pardini.getResultado.window.hours", "24"));

        int poolSize = config.getWorkerThreadPoolSize();
        this.executorService = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "IntegracaoWorker-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });

        logger.info("Worker inicializado: poolSize={}, batchSize={}, janelaGetResultado={}h",
                poolSize, batchSize, janelaHoras);
    }

    public int executarCiclo() {
        logger.info("Iniciando ciclo de processamento (fonte: ITPED_LAB SN_ASSINADO='N')...");
        processados.set(0);
        erros.set(0);

        try {
            // 1) Atualiza o mapa CodPedLab -> (Ano, CodPedApoio) via getResultado (período)
            atualizarMapaPardini();

            // 2) Busca no MV2000 ITPED_LAB os pendentes (SN_ASSINADO='N')
            List<PedidoLabPendente> pendentes = itpedLabRepository.buscarPendentesAssinatura(batchSize);

            if (pendentes.isEmpty()) {
                logger.info("Nenhum ITPED_LAB pendente (SN_ASSINADO='N')");
                return 0;
            }

            logger.info("Encontrados {} registros ITPED_LAB para avaliar", pendentes.size());

            List<Future<Boolean>> futures = new java.util.ArrayList<>();

            for (PedidoLabPendente p : pendentes) {
                Future<Boolean> f = executorService.submit(() -> processarItpedLab(p));
                futures.add(f);
            }

            for (Future<Boolean> f : futures) {
                try {
                    f.get(5, TimeUnit.MINUTES);
                } catch (TimeoutException e) {
                    logger.error("Timeout no processamento de ITPED_LAB");
                    f.cancel(true);
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

    private void atualizarMapaPardini() {
        try {
            LocalDateTime fim = LocalDateTime.now();
            LocalDateTime inicio = fim.minusHours(janelaHoras);

            logger.info("Atualizando mapa Pardini via getResultado ({}h): {} -> {}",
                    janelaHoras, inicio, fim);

            String xml = hpwsClient.getResultadoPeriodo(inicio, fim, 0);

            int upserts = mapaRepository.atualizarMapaDeXml(xml);

            logger.info("Mapa Pardini atualizado: {} registros (upsert)", upserts);

        } catch (Exception e) {
            // não derruba o ciclo inteiro
            logger.warn("Falha ao atualizar mapa Pardini (getResultado): {}", e.getMessage());
        }
    }

    private boolean processarItpedLab(PedidoLabPendente it) {
        String cdPedLab = it.getCdPedLab();
        Long cdAtendimento = it.getCdAtendimento();

        try {
            if (isBlank(cdPedLab)) {
                logger.warn("ITPED_LAB sem CD_PED_LAB (ignorando). Atendimento={}", cdAtendimento);
                return false;
            }

            // 1) Validação “com o XML”: se não mapeou, é porque NÃO apareceu no getResultado
            MapeamentoPardini mp = mapaRepository.buscarPorCodPedLab(cdPedLab);
            if (mp == null || isBlank(mp.getCodPedApoio()) || mp.getAnoCodPedApoio() == null) {
                logger.info("Ainda não apareceu no getResultado: CD_PED_LAB={} (vai tentar no próximo ciclo)", cdPedLab);
                return false;
            }

            // 2) Segurança: valida atendimento
            if (!mv2000Integrator.atendimentoExiste(cdAtendimento)) {
                throw new Exception("Atendimento " + cdAtendimento + " não existe no MV2000");
            }

            Long cdPaciente = mv2000Integrator.obterPacienteDoAtendimento(cdAtendimento);

            // 3) Baixa PDF/Gráfico por getResultadoPedido
            ResultadoPardini resultado = hpwsClient.getResultadoPedido(
                    mp.getAnoCodPedApoio(),
                    mp.getCodPedApoio(),
                    1
            );

            if (!resultado.isSucesso()) {
                throw new Exception("Pardini retornou erro: " + resultado.getMensagemErro());
            }

            // 4) Anexa no MV2000 (mesma lógica de antes)
            String chave = mp.getAnoCodPedApoio() + "-" + mp.getCodPedApoio();

            Long cdArquivoPdf = null;
            Long cdArquivoGrafico = null;

            if (resultado.temPdf()) {
                String hashPdf = resultado.getHashPdf();

                String descricao = String.format(
                        "Laudo Hermes Pardini - CD_PED_LAB=%s - Pedido %s [HASH:%s]",
                        cdPedLab, chave, hashPdf
                );
                String nomeArquivo = String.format("LAUDO_%s_%s.PDF", cdPedLab, chave);

                cdArquivoPdf = mv2000Integrator.anexarDocumento(
                        resultado.getPdfBytes(),
                        "PDF",
                        cdAtendimento,
                        cdPaciente,
                        descricao,
                        nomeArquivo,
                        tpDocLaudo
                );

                logger.info("PDF anexado: CD_PED_LAB={} -> CD_ARQUIVO_DOCUMENTO={}", cdPedLab, cdArquivoPdf);
            }

            if (resultado.temGrafico()) {
                String hashGrafico = resultado.getHashGrafico();

                String tipoImagem = Base64Handler.detectFileType(resultado.getGraficoBytes());
                String descricao = String.format(
                        "Gráfico Eletroforese - CD_PED_LAB=%s - Pedido %s [HASH:%s]",
                        cdPedLab, chave, hashGrafico
                );
                String nomeArquivo = String.format("GRAFICO_%s_%s.%s", cdPedLab, chave, tipoImagem);

                cdArquivoGrafico = mv2000Integrator.anexarDocumento(
                        resultado.getGraficoBytes(),
                        tipoImagem,
                        cdAtendimento,
                        cdPaciente,
                        descricao,
                        nomeArquivo,
                        tpDocGrafico
                );

                logger.info("Gráfico anexado: CD_PED_LAB={} -> CD_ARQUIVO_DOCUMENTO={}", cdPedLab, cdArquivoGrafico);
            }

            auditLogger.info("SUCESSO|ITPED_LAB|CD_PED_LAB={}|ATEND={}|PEDIDO={}|PDF={}|GRAFICO={}",
                    cdPedLab, cdAtendimento, chave, cdArquivoPdf, cdArquivoGrafico);

            processados.incrementAndGet();
            return true;

        } catch (Exception e) {
            logger.error("Erro ao processar ITPED_LAB CD_PED_LAB={}: {}", cdPedLab, e.getMessage(), e);

            auditLogger.info("ERRO|ITPED_LAB|CD_PED_LAB={}|ATEND={}|{}",
                    cdPedLab, cdAtendimento, e.getMessage());

            erros.incrementAndGet();
            return false;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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
