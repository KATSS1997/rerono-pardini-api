# Rerono Pardini API

Integração entre o **Hermes Pardini** (HPWS.XMLServer) e o sistema hospitalar **MV2000**, utilizando Oracle Database 12c.

## 📋 Objetivo

Consumir Web Services SOAP do Hermes Pardini para obtenção de laudos de exames laboratoriais:
- **Laudos em PDF**
- **Gráficos de eletroforese** (imagem PNG/JPG)

Os conteúdos são retornados em Base64 nas tags `<PDF>` e `<Grafico>` do XML SOAP, decodificados e anexados ao prontuário/atendimento no MV2000.

## 🏗️ Arquitetura

```
┌─────────────────────────────────────────────────────────────────┐
│                     RERONO PARDINI API                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐  │
│  │   SCHEDULER  │─────▶│    WORKER    │─────▶│    ORACLE    │  │
│  │   (Quartz)   │      │    SOAP      │      │    MV2000    │  │
│  └──────────────┘      └──────┬───────┘      └──────────────┘  │
│                               │                                 │
│                               ▼                                 │
│                     ┌──────────────────┐                        │
│                     │  HERMES PARDINI  │                        │
│                     │  HPWS.XMLServer  │                        │
│                     └──────────────────┘                        │
└─────────────────────────────────────────────────────────────────┘
```

## 🔄 Fluxo de Integração

1. **Consultar pedidos pendentes** → Tabela `RERONO_PEDIDO`
2. **Chamar SOAP Hermes Pardini** → `getResultadoPedido`
3. **Parsear XML e extrair Base64** → Tags `<PDF>` e `<Grafico>`
4. **Decodificar Base64 → byte[]**
5. **Verificar idempotência** → Hash SHA-256
6. **Inserir em `ARQUIVO_DOCUMENTO`** → BLOB do MV2000
7. **Vincular em `ARQUIVO_ATENDIMENTO`** → Relacionar ao atendimento
8. **Atualizar controle** → Marcar como processado

## 📁 Estrutura do Projeto

```
rerono-pardini-api/
├── pom.xml                          # Maven config
├── src/
│   └── main/
│       ├── java/br/com/rerono/
│       │   ├── Application.java     # Classe principal
│       │   ├── config/              # Configurações
│       │   ├── soap/                # Cliente SOAP
│       │   ├── codec/               # Base64 handler
│       │   ├── persistence/         # Repositórios
│       │   ├── mv2000/              # Integração MV2000
│       │   ├── worker/              # Worker de processamento
│       │   ├── scheduler/           # Agendador Quartz
│       │   └── model/               # Classes de domínio
│       └── resources/
│           ├── application.properties
│           └── logback.xml
├── scripts/
│   ├── 01_criar_tabelas.sql         # DDL das tabelas
│   └── 02_popular_pedidos.sql       # Scripts de carga
└── docs/
    └── ...
```

## ⚙️ Tecnologias

| Tecnologia | Versão | Uso |
|------------|--------|-----|
| Java | 11+ | Runtime |
| Maven | 3.8+ | Build |
| Oracle JDBC | 19.x | Conexão DB |
| HikariCP | 5.x | Pool de conexões |
| JAX-WS | 4.x | Cliente SOAP |
| Quartz | 2.3.x | Scheduler |
| SLF4J + Logback | 2.x | Logging |

## 🚀 Instalação

### 1. Pré-requisitos

- Java 11 ou superior
- Maven 3.8+
- Oracle Database 12c+
- Acesso ao endpoint HPWS do Hermes Pardini

### 2. Criar tabelas no Oracle

```bash
sqlplus usuario/senha@banco @scripts/01_criar_tabelas.sql
```

### 3. Configurar application.properties

Edite `src/main/resources/application.properties`:

```properties
# Hermes Pardini
pardini.soap.endpoint=https://wshomolog.hermespardini.com.br/...
pardini.soap.login=SEU_LOGIN
pardini.soap.passwd=SUA_SENHA

# Oracle
oracle.jdbc.url=jdbc:oracle:thin:@//servidor:1521/BANCO
oracle.jdbc.username=USUARIO
oracle.jdbc.password=SENHA
```

### 4. Build

```bash
mvn clean package
```

### 5. Executar

```bash
# Modo contínuo (scheduler)
java -jar target/rerono-pardini-api-1.0.0-SNAPSHOT.jar

# Executar uma vez
java -jar target/rerono-pardini-api-1.0.0-SNAPSHOT.jar --run-once

# Testar conexão Oracle
java -jar target/rerono-pardini-api-1.0.0-SNAPSHOT.jar --test-db

# Testar conexão SOAP
java -jar target/rerono-pardini-api-1.0.0-SNAPSHOT.jar --test-soap
```

## 🔐 Variáveis de Ambiente

Para segurança, use variáveis de ambiente para senhas:

```bash
export PARDINI_PASSWD=senha_pardini
export ORACLE_PASSWD=senha_oracle
export ORACLE_URL=jdbc:oracle:thin:@//prod:1521/PROD
```

## 📊 Monitoramento

### Views SQL

```sql
-- Status geral
SELECT * FROM VW_RERONO_STATUS;

-- Pedidos com erro
SELECT * FROM VW_RERONO_ERROS;
```

### Logs

Os logs são gravados em:
- `rerono-pardini.log` - Log geral
- `rerono-pardini-soap.log` - Comunicação SOAP
- `rerono-pardini-audit.log` - Auditoria

## 🔁 Reprocessamento

```sql
-- Reprocessar pedido específico
EXEC PRC_RERONO_REPROCESSAR(123);

-- Reprocessar todos com erro
EXEC PRC_RERONO_REPROCESSAR_TODOS;
```

## 📋 Tabelas do MV2000 Utilizadas

| Tabela | Uso |
|--------|-----|
| `ARQUIVO_DOCUMENTO` | Armazena o BLOB do PDF/imagem |
| `ARQUIVO_ATENDIMENTO` | Vincula documento ao atendimento |
| `ATENDIME` | Validação do atendimento |
| `DOCUMENTO` | Tipos de documento (CD 841 = Resultado de Exames) |

## ⚠️ Pontos de Atenção

1. **Idempotência**: O hash SHA-256 garante que o mesmo laudo não seja anexado duas vezes.

2. **Reprocessamento**: Pedidos com erro são reprocessados automaticamente até `max.tentativas`.

3. **Triggers MV2000**: A tabela `ARQUIVO_DOCUMENTO` possui trigger que pode gerar o ID automaticamente.

4. **Volume**: Para alto volume, ajuste `worker.thread.pool.size` e `worker.batch.size`.

5. **Credenciais**: NUNCA commite senhas no repositório. Use variáveis de ambiente.

## 📄 Licença

Projeto interno - uso restrito.

## 👥 Contato

Desenvolvido para integração hospitalar Hermes Pardini / MV2000.