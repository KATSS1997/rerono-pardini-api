-- ============================================
-- RERONO PARDINI API
-- Script para popular pedidos de integração
-- ============================================

-- Este script é um EXEMPLO de como popular a tabela RERONO_PEDIDO
-- a partir de dados existentes no MV2000.
-- ADAPTE conforme a estrutura real do seu ambiente!

-- ============================================
-- OPÇÃO 1: Popular a partir de tabela de exames externos
-- (ajuste os nomes das tabelas e colunas)
-- ============================================

/*
INSERT INTO RERONO_PEDIDO (
    ANO_COD_PED_APOIO,
    COD_PED_APOIO,
    CD_ATENDIMENTO,
    CD_PACIENTE,
    STATUS
)
SELECT DISTINCT
    EXTRACT(YEAR FROM e.DT_PEDIDO) AS ANO_COD_PED_APOIO,
    e.NR_PEDIDO_APOIO AS COD_PED_APOIO,
    e.CD_ATENDIMENTO,
    a.CD_PACIENTE,
    'PENDENTE'
FROM EXAME_APOIO e  -- Tabela de exames enviados ao laboratório de apoio
JOIN ATENDIME a ON e.CD_ATENDIMENTO = a.CD_ATENDIMENTO
WHERE e.CD_LABORATORIO_APOIO = 999  -- Código do Hermes Pardini
  AND e.SN_RESULTADO_RECEBIDO = 'N' -- Ainda não recebeu resultado
  AND e.DT_PEDIDO >= ADD_MONTHS(SYSDATE, -1)  -- Último mês
  AND NOT EXISTS (
      SELECT 1 FROM RERONO_PEDIDO p
      WHERE p.ANO_COD_PED_APOIO = EXTRACT(YEAR FROM e.DT_PEDIDO)
        AND p.COD_PED_APOIO = e.NR_PEDIDO_APOIO
  );

COMMIT;
*/


-- ============================================
-- OPÇÃO 2: Inserir pedido manualmente (para teste)
-- ============================================

-- Exemplo de inserção manual para teste:
/*
INSERT INTO RERONO_PEDIDO (
    ANO_COD_PED_APOIO,
    COD_PED_APOIO,
    CD_ATENDIMENTO,
    CD_PACIENTE
) VALUES (
    2023,           -- Ano do pedido
    '1234567',      -- Código do pedido no Pardini
    999999,         -- CD_ATENDIMENTO do MV2000
    888888          -- CD_PACIENTE (opcional)
);
COMMIT;
*/


-- ============================================
-- OPÇÃO 3: Usar procedure para inserir
-- ============================================

/*
DECLARE
    v_id_pedido NUMBER;
BEGIN
    PRC_RERONO_INSERIR_PEDIDO(
        p_ano_cod_ped_apoio => 2023,
        p_cod_ped_apoio     => '1234567',
        p_cd_atendimento    => 999999,
        p_cd_paciente       => 888888,
        p_id_pedido         => v_id_pedido
    );
    DBMS_OUTPUT.PUT_LINE('Pedido criado com ID: ' || v_id_pedido);
END;
/
*/


-- ============================================
-- CONSULTAS ÚTEIS
-- ============================================

-- Ver status geral
SELECT * FROM VW_RERONO_STATUS;

-- Ver pedidos pendentes
SELECT * FROM RERONO_PEDIDO WHERE STATUS = 'PENDENTE' ORDER BY DT_CRIACAO;

-- Ver pedidos com erro
SELECT * FROM VW_RERONO_ERROS;

-- Ver últimos processados
SELECT * FROM RERONO_PEDIDO 
WHERE STATUS = 'PROCESSADO' 
ORDER BY DT_PROCESSAMENTO DESC
FETCH FIRST 10 ROWS ONLY;

-- Estatísticas por dia
SELECT 
    TRUNC(DT_CRIACAO) AS DIA,
    COUNT(*) AS TOTAL,
    SUM(CASE WHEN STATUS = 'PROCESSADO' THEN 1 ELSE 0 END) AS PROCESSADOS,
    SUM(CASE WHEN STATUS = 'ERRO' THEN 1 ELSE 0 END) AS ERROS
FROM RERONO_PEDIDO
WHERE DT_CRIACAO >= SYSDATE - 7
GROUP BY TRUNC(DT_CRIACAO)
ORDER BY DIA DESC;


-- ============================================
-- REPROCESSAMENTO
-- ============================================

-- Reprocessar um pedido específico
-- EXEC PRC_RERONO_REPROCESSAR(123);

-- Reprocessar todos com erro
-- EXEC PRC_RERONO_REPROCESSAR_TODOS;

-- Reprocessar pedidos com erro específico
/*
UPDATE RERONO_PEDIDO
SET STATUS = 'PENDENTE', TENTATIVAS = 0, ERRO_MSG = NULL
WHERE STATUS = 'ERRO'
  AND ERRO_MSG LIKE '%timeout%';
COMMIT;
*/


-- ============================================
-- LIMPEZA (usar com cuidado!)
-- ============================================

-- Limpar logs antigos (mais de 90 dias)
/*
DELETE FROM RERONO_LOG WHERE DT_LOG < SYSDATE - 90;
COMMIT;
*/

-- Limpar pedidos processados antigos (mais de 1 ano)
/*
DELETE FROM RERONO_PEDIDO 
WHERE STATUS = 'PROCESSADO' 
  AND DT_PROCESSAMENTO < ADD_MONTHS(SYSDATE, -12);
COMMIT;
*/