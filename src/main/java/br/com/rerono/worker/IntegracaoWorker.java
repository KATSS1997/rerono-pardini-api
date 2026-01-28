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

    private final int janelaHoras;
    private final int anoDefault;
    private final int anoFallbackYears;

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
        this.anoDefault = config.getPardiniAnoCodPedApoioDefault();
        this.anoFallbackYears = config.getPardiniAnoCodPedApoioFallbackYears();

        int poolSize = config.getWorkerThreadPoolSize();
        this.executorService = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "IntegracaoWorker-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });

        logger.info("Worker inicializado: poolSize={}, batchSize={}, janelaGetResultado={}h, anoDefault={}, fallbackYears={}",
                poolSize, batchSize, janelaHoras, anoDefault, anoFallbackYears);
    }

    public int executarCiclo() {
        logger.info("Iniciando ciclo (ITPED_LAB SN_ASSINADO='N' via CD_PED_LAB + validação getResultado)...");
        processados.set(0);
        erros.set(0);

        try {
            // 1) Atualiza mapa (CodPedLab -> CodPedApoio) via getResultado
            atualizarMapaPardini();

            // 2) Busca CD_PED_LAB pendentes no MV2000
            List<PedidoLabPendente> pendentes = itpedLabRepository.buscarPendentesAssinatura(batchSize);
            if (pendentes.isEmpty()) {
                logger.info("Nenhum CD_PED_LAB pendente (SN_ASSINADO='N')");
                return 0;
            }

            logger.info("Encontrados {} CD_PED_LAB pendentes", pendentes.size());

            List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (PedidoLabPendente p : pendentes) {
                futures.add(executorService.submit(() -> processarCdPedLab(p)));
            }

            for (Future<Boolean> f : futures) {
                try {
                    f.get(10, TimeUnit.MINUTES);
                } catch (TimeoutException e) {
                    logger.error("Timeout no processamento");
                    f.cancel(true);
                } catch (Exception e) {
                    logger.error("Erro ao aguardar processamento: {}", e.getMessage());
                }
            }

            logger.info("Ciclo concluído: {} processados, {} erros", processados.get(), erros.get());
            return processados.get();

        } catch (Exception e) {
            logger.error("Erro no ciclo: {}", e.getMessage(), e);
            return 0;
        }
    }

    private void atualizarMapaPardini() {
        try {
            LocalDateTime fim = LocalDateTime.now();              // SYSDATE equivalente
            LocalDateTime inicio = fim.minusHours(janelaHoras);

            logger.info("Chamando getResultado ({}h): {} -> {}", janelaHoras, inicio, fim);

            // grafico=0 (não precisamos de gráficos aqui; só do mapa)
            String xml = hpwsClient.getResultadoPeriodo(inicio, fim, 0);

            int upserts = mapaRepository.atualizarMapaDeXml(xml);
            logger.info("Mapa Pardini atualizado (CodPedLab->CodPedApoio): {} upserts", upserts);

        } catch (Exception e) {
            logger.warn("Falha ao atualizar mapa Pardini: {}", e.getMessage());
        }
    }

    private boolean processarCdPedLab(PedidoLabPendente it) {
        String cdPedLab = it.getCdPedLab();

        try {
            if (isBlank(cdPedLab)) {
                logger.warn("Registro ITPED_LAB sem CD_PED_LAB (ignorando)");
                return false;
            }

            // 1) Validar “com o XML”: se não está no mapa, não apareceu no getResultado do período
            MapeamentoPardini mp = mapaRepository.buscarPorCodPedLab(cdPedLab);
            if (mp == null || isBlank(mp.getCodPedApoio())) {
                logger.info("Não apareceu no getResultado (ainda): CD_PED_LAB={} (vai tentar no próximo ciclo)", cdPedLab);
                return false;
            }

            String codPedApoio = mp.getCodPedApoio();

            // 2) Descobrir atendimento/paciente a partir do CD_PED_LAB (MV2000)
            Long cdAtendimento = mv2000Integrator.obterAtendimentoPorCdPedLab(cdPedLab);
            if (cdAtendimento == null) {
                throw new Exception("Não foi possível encontrar CD_ATENDIMENTO para CD_PED_LAB=" + cdPedLab);
            }

            if (!mv2000Integrator.atendimentoExiste(cdAtendimento)) {
                throw new Exception("Atendimento " + cdAtendimento + " não existe no MV2000");
            }

            Long cdPaciente = mv2000Integrator.obterPacienteDoAtendimento(cdAtendimento);

            // 3) Baixar PDF via getResultadoPedido tentando ano default + fallback
            ResultadoPardini resultado = baixarResultadoPedidoComFallbackAno(codPedApoio);
            if (resultado == null) {
                throw new Exception("Não foi possível baixar PDF para CodPedApoio=" + codPedApoio + " (ano default + fallback falharam)");
            }

            if (!resultado.isSucesso()) {
                throw new Exception("Pardini retornou erro: " + resultado.getMensagemErro());
            }

            Long cdArquivoPdf = null;
            Long cdArquivoGrafico = null;

            if (resultado.temPdf()) {
                String hashPdf = resultado.getHashPdf();

                String descricao = String.format(
                        "Laudo Hermes Pardini - CD_PED_LAB=%s - CodPedApoio=%s [HASH:%s]",
                        cdPedLab, codPedApoio, hashPdf
                );
                String nomeArquivo = String.format("LAUDO_%s_%s.PDF", cdPedLab, codPedApoio);

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
                        "Gráfico Eletroforese - CD_PED_LAB=%s - CodPedApoio=%s [HASH:%s]",
                        cdPedLab, codPedApoio, hashGrafico
                );
                String nomeArquivo = String.format("GRAFICO_%s_%s.%s", cdPedLab, codPedApoio, tipoImagem);

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

            auditLogger.info("SUCESSO|CD_PED_LAB={}|ATEND={}|COD_PED_APOIO={}|PDF={}|GRAFICO={}",
                    cdPedLab, cdAtendimento, codPedApoio, cdArquivoPdf, cdArquivoGrafico);

            processados.incrementAndGet();
            return true;

        } catch (Exception e) {
            logger.error("Erro ao processar CD_PED_LAB={}: {}", cdPedLab, e.getMessage(), e);
            auditLogger.info("ERRO|CD_PED_LAB={}|{}", cdPedLab, e.getMessage());
            erros.incrementAndGet();
            return false;
        }
    }

    private ResultadoPardini baixarResultadoPedidoComFallbackAno(String codPedApoio) {
        for (int i = 0; i <= anoFallbackYears; i++) {
            int ano = anoDefault - i;
            try {
                logger.info("Baixando getResultadoPedido: ano={}, CodPedApoio={}", ano, codPedApoio);
                ResultadoPardini r = hpwsClient.getResultadoPedido(ano, codPedApoio, 1);

                if (r != null && r.isSucesso() && (r.temPdf() || r.temGrafico())) {
                    return r;
                }

                if (r != null && !r.isSucesso()) {
                    logger.warn("Tentativa ano {} falhou: {}", ano, r.getMensagemErro());
                }

            } catch (Exception e) {
                logger.warn("Tentativa ano {} lançou exceção: {}", ano, e.getMessage());
            }
        }
        return null;
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
