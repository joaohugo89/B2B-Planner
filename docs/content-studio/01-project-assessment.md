# 01 — Diagnóstico do JH Planner

Levantado do código em `main` (commit `7ad5d65`), não da descrição do produto.

## Tecnologias efetivamente em uso

| Camada | O que há | Versão |
|---|---|---|
| Backend | Java, Spring Boot (web, data-jpa, security, validation) | Java 21 no `pom.xml`, Boot 3.5.16 |
| Persistência | PostgreSQL + Flyway (11 migrations, `ddl-auto: validate`) | Postgres 18 no compose |
| Auth | JWT próprio (jjwt), access 30 min, refresh 14 dias, revogação por `tokenVersion` | — |
| Segredos | AES-GCM em repouso (`CryptoService`, prefixo `enc:v1:`) | — |
| IA | `AiProvider` com duas implementações: Anthropic e padrão OpenAI (`/chat/completions`) | anthropic-java 2.34.0 |
| Arquivos | `AssetStorage`: disco (padrão) ou S3/R2 | AWS SDK v2 |
| Frontend | React 18, TypeScript, Vite, Tailwind, React Router, axios, lucide | — |
| Implantação | Docker multi-stage, nginx servindo `/api` na mesma origem, Cloudflare Tunnel | — |
| Testes | 106 testes JUnit no backend; **nenhum** no frontend | — |

Pacotes do backend (`br.com.jhcomp.planner`): `post`, `copy`, `asset`, `ai`, `instagram`,
`settings`, `security`, `crypto`, `user`, `config`, `common`. Já é um monólito organizado por
domínio — não por camada técnica —, o que decide a arquitetura do Content Studio (ver 03).

## O que já existe e será reaproveitado

- **Tarefa em segundo plano com estado no banco**: `InstagramPublishService` +
  `PublishStateStore` + executor dedicado, com destrave de tarefas órfãs no boot. É o molde
  do sistema de jobs de vídeo; não é preciso inventar outro.
- **Ollama já funciona**: o `OpenAiCompatibleProvider` fala com qualquer servidor
  `/chat/completions`, e o Ollama expõe exatamente isso em `http://localhost:11434/v1`.
  O "AI Content Engine" não precisa de cliente novo, só de configuração.
- **Kit de interface** (`components/ui.tsx`, `primitives.tsx`, classe `shell`) e a receita
  de `docs/NOVA-TELA.md`.
- **Erros padronizados** (`ApiError`, `GlobalExceptionHandler`) e log de erros em arquivo.

## Problemas encontrados

| # | Problema | Onde | Criticidade |
|---|---|---|---|
| 1 | A imagem Docker compila com JDK 25 e o projeto declara Java 21 | `backend/Dockerfile` × `pom.xml` | Média — o que roda em produção não é o que é testado localmente |
| 2 | O build da imagem usa `-DskipTests`; o hook `pre-push` afirma que o build roda os testes | `backend/Dockerfile`, `scripts/git-hooks/pre-push` | Média — código quebrado passa pelo push |
| 3 | O assistente só liga com chave de API (`isEnabled()`), e o Ollama não usa chave | `AiAssistantService` | Baixa — contorno: digitar qualquer valor na chave |
| 4 | Estados de curta duração em memória (`OAuthStateStore`, `DraftCache`, cache de settings) | vários | Baixa hoje; impede rodar mais de uma instância |
| 5 | Limite global de upload de 25 MB (Spring e nginx) | `application.yml`, `nginx.conf` | Bloqueava vídeo — resolvido na implementação |
| 6 | `AssetStorage` é específico de imagem (lista de extensões, URL pública) | `asset/service` | Não serve para vídeo; vídeo ganhou armazenamento próprio |
| 7 | Sem testes de frontend | `frontend/` | Média — telas só são validadas à mão |
| 8 | Um único usuário administrador, sem papéis nem dono por registro | `user`, `SecurityConfig` | Baixa enquanto for ferramenta de uma pessoa |
| 9 | `PostStudioPage.tsx` tem cerca de 1000 linhas | frontend | Baixa — funciona; dividir quando for mexer |
| 10 | O endpoint de arquivos de arte é público por necessidade do Instagram | `SecurityConfig` | Aceito — vídeo **não** segue esse padrão |

## Preservar × refatorar

Preservar: organização por domínio, Flyway, o padrão de job do Instagram, `AiProvider`,
o kit de UI, a segurança (JWT + AES-GCM). Refatorar só o que o vídeo exige: limites de
upload e a imagem Docker (FFmpeg). Os itens 1 e 2 merecem correção, mas são independentes
do Content Studio e ficaram fora desta entrega.

## O que a máquina tem hoje

Verificado em 09/11/2026: RTX 5070 12 GB (driver 617.42), Java, Maven, Node, Docker e
Python instalados; 375 GB livres em `C:`. **Não instalados**: FFmpeg, Ollama, e nenhuma
distribuição Ubuntu no WSL2 (só a `docker-desktop`). Consequência direta: nenhum motor de
IA pôde ser executado de verdade nesta etapa; o pipeline foi testado com motores falsos
(ver 07).
