-- ============================================
-- RERONO PARDINI API
-- Script de criação das tabelas de controle
-- Oracle Database 12c+
-- ============================================

-- Dropar objetos se existirem (cuidado em produção!)
-- DROP TABLE RERONO_LOG CASCADE CONSTRAINTS;
-- DROP TABLE RERONO_PEDIDO CASCADE CONSTRAINTS;
-- DROP SEQUENCE SEQ_RERONO_PEDIDO;
-- DROP SEQUENCE SEQ_RERONO_LOG;

-- ============================================
-- TABELA: RERONO_PEDIDO
-- Controle de pedidos para integração
-- ============================================
CREATE TABLE RERONO_PEDIDO (
    ID_PEDIDO               NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ANO_COD_PED_APOIO       NUMBER(4)       NOT NULL,
    COD_PED_APOIO           VARCHAR2(50)    NOT NULL,
    CD_ATENDIMENTO          NUMBER          NOT NULL,
    CD_PACIENTE             NUMBER,
    STATUS                  VARCHAR2(20)    DEFAULT 'PENDENTE' NOT NULL,
    TENTATIVAS              NUMBER          DEFAULT 0 NOT NULL,
    DT_CRIACAO              TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    DT_PROCESSAMENTO        TIMESTAMP,
    CD_ARQUIVO_PDF          NUMBER,         -- FK para ARQUIVO_DOCUMENTO (laudo PDF)
    CD_ARQUIVO_GRAFICO      NUMBER,         -- FK para ARQUIVO_DOCUMENTO (gráfico)
    HASH_PDF                VARCHAR2(64),   -- SHA-256 para idempotência
    HASH_GRAFICO            VARCHAR2(64),   -- SHA-256 para idempotência
    ERRO_MSG                VARCHAR2(4000),
    -- Constraint única para evitar duplicidade de pedidos
    CONSTRAINT UK_RERONO_PEDIDO UNIQUE (ANO_COD_PED_APOIO, COD_PED_APOIO)
);

-- Comentários
COMMENT ON TABLE RERONO_PEDIDO IS 'Controle de pedidos de exames do Hermes Pardini para integração com MV2000';
COMMENT ON COLUMN RERONO_PEDIDO.ID_PEDIDO IS 'Identificador único interno';
COMMENT ON COLUMN RERONO_PEDIDO.ANO_COD_PED_APOIO IS 'Ano do código do pedido no Pardini';
COMMENT ON COLUMN RERONO_PEDIDO.COD_PED_APOIO IS 'Código do pedido no Pardini';
COMMENT ON COLUMN RERONO_PEDIDO.CD_ATENDIMENTO IS 'Código do atendimento no MV2000';
COMMENT ON COLUMN RERONO_PEDIDO.CD_PACIENTE IS 'Código do paciente no MV2000';
COMMENT ON COLUMN RERONO_PEDIDO.STATUS IS 'Status: PENDENTE, PROCESSANDO, PROCESSADO, ERRO, IGNORADO';
COMMENT ON COLUMN RERONO_PEDIDO.TENTATIVAS IS 'Número de tentativas de processamento';
COMMENT ON COLUMN RERONO_PEDIDO.CD_ARQUIVO_PDF IS 'FK para ARQUIVO_DOCUMENTO do PDF gerado';
COMMENT ON COLUMN RERONO_PEDIDO.CD_ARQUIVO_GRAFICO IS 'FK para ARQUIVO_DOCUMENTO do gráfico gerado';
COMMENT ON COLUMN RERONO_PEDIDO.HASH_PDF IS 'Hash SHA-256 do PDF para idempotência';
COMMENT ON COLUMN RERONO_PEDIDO.HASH_GRAFICO IS 'Hash SHA-256 do gráfico para idempotência';

-- Índices para performance
CREATE INDEX IDX_RERONO_PEDIDO_STATUS ON RERONO_PEDIDO(STATUS);
CREATE INDEX IDX_RERONO_PEDIDO_ATEND ON RERONO_PEDIDO(CD_ATENDIMENTO);
CREATE INDEX IDX_RERONO_PEDIDO_DT_CRIACAO ON RERONO_PEDIDO(DT_CRIACAO);
CREATE INDEX IDX_RERONO_PEDIDO_HASH_PDF ON RERONO_PEDIDO(HASH_PDF);
CREATE INDEX IDX_RERONO_PEDIDO_HASH_GRAF ON RERONO_PEDIDO(HASH_GRAFICO);

-- Constraint de check para status válidos
ALTER TABLE RERONO_PEDIDO ADD CONSTRAINT CK_RERONO_PEDIDO_STATUS 
    CHECK (STATUS IN ('PENDENTE', 'PROCESSANDO', 'PROCESSADO', 'ERRO', 'IGNORADO'));


-- ============================================
-- TABELA: RERONO_LOG
-- Logs de auditoria da integração
-- ============================================
CREATE TABLE RERONO_LOG (
    ID_LOG                  NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ID_PEDIDO               NUMBER,
    ETAPA                   VARCHAR2(50),   -- SOAP_REQUEST, SOAP_RESPONSE, DECODE, PERSIST, MV_ATTACH
    NIVEL                   VARCHAR2(10),   -- INFO, WARN, ERROR, DEBUG
    MENSAGEM                VARCHAR2(4000),
    PAYLOAD                 CLOB,           -- XML completo ou stacktrace
    DT_LOG                  TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL
);

-- Comentários
COMMENT ON TABLE RERONO_LOG IS 'Logs de auditoria da integração Pardini/MV2000';
COMMENT ON COLUMN RERONO_LOG.ETAPA IS 'Etapa do processamento: SOAP_REQUEST, SOAP_RESPONSE, DECODE, PERSIST, MV_ATTACH';
COMMENT ON COLUMN RERONO_LOG.NIVEL IS 'Nível do log: INFO, WARN, ERROR, DEBUG';
COMMENT ON COLUMN RERONO_LOG.PAYLOAD IS 'Conteúdo completo (XML SOAP, stacktrace, etc)';

-- Índices
CREATE INDEX IDX_RERONO_LOG_PEDIDO ON RERONO_LOG(ID_PEDIDO);
CREATE INDEX IDX_RERONO_LOG_DT ON RERONO_LOG(DT_LOG);
CREATE INDEX IDX_RERONO_LOG_NIVEL ON RERONO_LOG(NIVEL);

-- FK para RERONO_PEDIDO (opcional, pode deixar sem para performance)
-- ALTER TABLE RERONO_LOG ADD CONSTRAINT FK_RERONO_LOG_PEDIDO 
--     FOREIGN KEY (ID_PEDIDO) REFERENCES RERONO_PEDIDO(ID_PEDIDO);


-- ============================================
-- SEQUENCES (caso não use IDENTITY)
-- ============================================
-- Se sua versão do Oracle não suporta IDENTITY, use:
-- CREATE SEQUENCE SEQ_RERONO_PEDIDO START WITH 1 INCREMENT BY 1 NOCACHE;
-- CREATE SEQUENCE SEQ_RERONO_LOG START WITH 1 INCREMENT BY 1 NOCACHE;


-- ============================================
-- GRANTS (ajustar conforme necessário)
-- ============================================
-- GRANT SELECT, INSERT, UPDATE, DELETE ON RERONO_PEDIDO TO USUARIO_INTEGRACAO;
-- GRANT SELECT, INSERT ON RERONO_LOG TO USUARIO_INTEGRACAO;


-- ============================================
-- VIEW para monitoramento
-- ============================================
CREATE OR REPLACE VIEW VW_RERONO_STATUS AS
SELECT 
    STATUS,
    COUNT(*) AS TOTAL,
    MIN(DT_CRIACAO) AS PRIMEIRO,
    MAX(DT_CRIACAO) AS ULTIMO
FROM RERONO_PEDIDO
GROUP BY STATUS
ORDER BY 
    CASE STATUS 
        WHEN 'ERRO' THEN 1 
        WHEN 'PENDENTE' THEN 2 
        WHEN 'PROCESSANDO' THEN 3 
        WHEN 'PROCESSADO' THEN 4 
        ELSE 5 
    END;

COMMENT ON VIEW VW_RERONO_STATUS IS 'Resumo do status dos pedidos de integração';


-- ============================================
-- VIEW para pedidos com erro
-- ============================================
CREATE OR REPLACE VIEW VW_RERONO_ERROS AS
SELECT 
    p.ID_PEDIDO,
    p.ANO_COD_PED_APOIO || '-' || p.COD_PED_APOIO AS PEDIDO_PARDINI,
    p.CD_ATENDIMENTO,
    p.TENTATIVAS,
    p.DT_CRIACAO,
    p.ERRO_MSG,
    a.NM_PACIENTE
FROM RERONO_PEDIDO p
LEFT JOIN ATENDIME a ON p.CD_ATENDIMENTO = a.CD_ATENDIMENTO
WHERE p.STATUS = 'ERRO'
ORDER BY p.DT_CRIACAO DESC;

COMMENT ON VIEW VW_RERONO_ERROS IS 'Pedidos com erro para análise';


-- ============================================
-- Procedure para inserir pedido (uso opcional)
-- ============================================
CREATE OR REPLACE PROCEDURE PRC_RERONO_INSERIR_PEDIDO (
    p_ano_cod_ped_apoio IN NUMBER,
    p_cod_ped_apoio     IN VARCHAR2,
    p_cd_atendimento    IN NUMBER,
    p_cd_paciente       IN NUMBER DEFAULT NULL,
    p_id_pedido         OUT NUMBER
) AS
BEGIN
    INSERT INTO RERONO_PEDIDO (
        ANO_COD_PED_APOIO,
        COD_PED_APOIO,
        CD_ATENDIMENTO,
        CD_PACIENTE,
        STATUS,
        TENTATIVAS
    ) VALUES (
        p_ano_cod_ped_apoio,
        p_cod_ped_apoio,
        p_cd_atendimento,
        p_cd_paciente,
        'PENDENTE',
        0
    ) RETURNING ID_PEDIDO INTO p_id_pedido;
    
    COMMIT;
EXCEPTION
    WHEN DUP_VAL_ON_INDEX THEN
        -- Pedido já existe, retornar ID existente
        SELECT ID_PEDIDO INTO p_id_pedido
        FROM RERONO_PEDIDO
        WHERE ANO_COD_PED_APOIO = p_ano_cod_ped_apoio
          AND COD_PED_APOIO = p_cod_ped_apoio;
    WHEN OTHERS THEN
        ROLLBACK;
        RAISE;
END;
/


-- ============================================
-- Procedure para marcar reprocessamento
-- ============================================
CREATE OR REPLACE PROCEDURE PRC_RERONO_REPROCESSAR (
    p_id_pedido IN NUMBER
) AS
BEGIN
    UPDATE RERONO_PEDIDO
    SET STATUS = 'PENDENTE',
        TENTATIVAS = 0,
        ERRO_MSG = NULL
    WHERE ID_PEDIDO = p_id_pedido
      AND STATUS = 'ERRO';
    
    IF SQL%ROWCOUNT = 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Pedido não encontrado ou não está em erro');
    END IF;
    
    COMMIT;
END;
/


-- ============================================
-- Procedure para reprocessar todos com erro
-- ============================================
CREATE OR REPLACE PROCEDURE PRC_RERONO_REPROCESSAR_TODOS AS
BEGIN
    UPDATE RERONO_PEDIDO
    SET STATUS = 'PENDENTE',
        TENTATIVAS = 0,
        ERRO_MSG = NULL
    WHERE STATUS = 'ERRO';
    
    DBMS_OUTPUT.PUT_LINE('Pedidos marcados para reprocessamento: ' || SQL%ROWCOUNT);
    COMMIT;
END;
/


-- ============================================
-- FIM DO SCRIPT
-- ============================================