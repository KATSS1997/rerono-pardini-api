# Rerono Pardini API

Integração entre o Hermes Pardini (HPWS.XMLServer) e o sistema MV2000.

## Objetivo

Consumir Web Services SOAP do Hermes Pardini para obtenção de laudos de exames
(PDF e gráficos de eletroforese), decodificar os conteúdos Base64 e anexá-los
ao prontuário/atendimento no MV2000, utilizando Oracle Database 12c.

## Tecnologias

- Java (a definir versão)
- SOAP / XML
- Oracle Database 12c
- MV2000
- Git / GitHub

## Estrutura do Projeto

docs/  
- arquitetura.md  
- integracao-hpws.md  
- mv2000.md  

src/  
- código-fonte da integração  

scripts/  
- scripts auxiliares (SQL, batch, utilitários)

## Status

Projeto em fase inicial de estruturação.
