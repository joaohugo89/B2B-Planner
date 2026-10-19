# JH.COMP // Engineering System — Planner B2B

Aplicação full-stack para o planner de conteúdo da JH.COMP: Feed Grid do Instagram, posts
detalhados, guia de copy e central de assets/templates. Identidade visual dark industrial
(carvão/grafite com acentos em vermelho).

```
.
├── backend/    Java 21 + Spring Boot 3.3 (Web, Data JPA, Security/JWT, Flyway, PostgreSQL)
├── frontend/   React 18 + TypeScript + Vite + Tailwind + Lucide + React Router
├── docker-compose.yml        PostgreSQL 16 local
└── docker-compose.prod.yml   Stack de produção (backend + frontend + Postgres)
```

## Subir tudo (3 terminais)

Nesta máquina a porta 8080 está ocupada pelo Apache (`httpd`), então o backend usa **8081** por padrão —
é a porta para a qual o proxy do Vite aponta. Para mudar, ajuste `SERVER_PORT` e `frontend/vite.config.ts` juntos.

```powershell
# 1) Banco (cria o cluster na primeira vez e o banco jhcomp_planner)
.\scripts\pg-local.ps1 start
```

```powershell
# 2) Backend  →  http://localhost:8081
cd backend
$env:DB_URL = "jdbc:postgresql://127.0.0.1:5440/jhcomp_planner"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "postgres"
$env:ANTHROPIC_API_KEY = "sk-ant-..."   # opcional: liga o Assistente IA
mvn spring-boot:run
```

```powershell
# 3) Frontend  →  http://localhost:5173
cd frontend
npm install   # só na primeira vez
npm run dev
```

Depois abra http://localhost:5173 e entre com `admin@jhcomp.com.br` / `jhcomp@2026`
(só as escritas exigem login; a leitura do planner é pública).

Para parar o banco no fim do dia: `.\scripts\pg-local.ps1 stop`.

### Usando o PostgreSQL instalado (configuração atual)

O planner roda na instância **PostgreSQL 18 (porta 5432)** com um usuário dedicado — mais perto do
que seria um banco gerenciado em produção:

```powershell
cd backend
$env:DB_URL = "jdbc:postgresql://127.0.0.1:5432/jhcomp_planner"
$env:DB_USER = "jhcomp"
$env:DB_PASSWORD = "jhcomp"
mvn spring-boot:run
```

Para recriar esse usuário do zero (como `postgres`):

```sql
CREATE USER jhcomp WITH LOGIN PASSWORD 'jhcomp';
CREATE DATABASE jhcomp_planner OWNER jhcomp;
```

O `scripts\migrar-para-postgres18.ps1` move os dados de um cluster para o outro (dump + restore +
conferência das tabelas) e o `scripts\pg-local.ps1` cria um cluster descartável em `.pgdata\`, útil
se você quiser um banco isolado de novo. Os arquivos de `backend\uploads\` **não** entram no dump —
copie a pasta junto, senão os posts apontam para imagens que não existem.

## 1. Banco de dados — alternativas

O `scripts\pg-local.ps1` cria um cluster próprio em `.pgdata/` na porta **5440**, sem tocar nas
instâncias do PostgreSQL já instaladas (que ocupam 5432–5437). Se preferir usar uma delas, crie o banco
e aponte as variáveis:

```powershell
psql -U postgres -c "create database jhcomp_planner;"
$env:DB_URL = "jdbc:postgresql://localhost:5432/jhcomp_planner"
```

Com Docker disponível, `docker compose up -d` também serve (PostgreSQL 16 em `localhost:5432`,
usuário/senha `postgres`).

## 2. Backend

- API: http://localhost:8081/api
- Swagger/OpenAPI: http://localhost:8081/swagger-ui.html
- Flyway cria o schema (`V1`) e popula o Batch 01 com 6 posts, 16 templates e 6 copies (`V2`).
- Um usuário admin é criado no primeiro boot (`admin@jhcomp.com.br`). A senha vem de
  `JHCOMP_ADMIN_PASSWORD`; sem essa variável, uma senha aleatória é gerada e impressa no log
  **uma única vez**. Instalações que já têm o usuário continuam com a senha atual.

Variáveis úteis: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `SERVER_PORT`, `ANTHROPIC_API_KEY`,
`INSTAGRAM_APP_ID`, `INSTAGRAM_APP_SECRET`, `INSTAGRAM_PUBLIC_BASE_URL`.

### Segurança de credenciais e sessão

Sem `SPRING_PROFILES_ACTIVE` o perfil é `dev` e tudo roda na máquina local sem configuração extra.
Em produção (`SPRING_PROFILES_ACTIVE=prod` ou qualquer perfil != dev) duas variáveis passam a ser
**obrigatórias** — a aplicação não sobe sem elas:

| Variável | Para quê |
| --- | --- |
| `JWT_SECRET` | Assina os tokens. O default de desenvolvimento é recusado fora de dev, e o segredo precisa de 32+ caracteres. |
| `JHCOMP_ENCRYPTION_KEY` | Chave AES (32 bytes em Base64, ou uma frase longa) que cifra segredos em repouso. |

Cifrados em repouso com AES-GCM (prefixo `enc:v1:` na coluna): `instagram_accounts.access_token`,
`app_settings.instagram_app_secret` e `app_settings.ai_api_key`. Valores que já estavam em claro são
cifrados no primeiro boot. Trocar a chave torna os valores salvos ilegíveis — regrave-os pelo painel.

Sessão: `POST /api/auth/logout` (corpo `{"refreshToken": "..."}`) incrementa o `token_version` do
usuário e invalida **todos** os refresh tokens já emitidos. Access tokens em circulação continuam
valendo até expirar (30 min).

### Painel da conta

O nome do usuário no topo abre um painel lateral com: a sessão atual (nome, papel, sair), o estado
das conexões (Instagram e Assistente de IA) e, principalmente, **as chaves das integrações** —
App ID, App Secret, URI de redirecionamento e URL pública do Instagram, mais a chave e o modelo da
Anthropic. O que você salva ali fica no banco e **vale na hora, sem reiniciar o backend**; deixando
em branco, volta a valer a variável de ambiente.

Segredos nunca voltam do servidor: a API responde só `instagramAppSecretDefined` / `aiApiKeyDefined`,
o campo fica em branco com a marca “já definido”, e o ✕ ao lado apaga a chave salva.
`GET`/`PUT /api/settings/integrations` exigem login (a leitura também).

### Publicar no Instagram

A aba **Instagram** conecta a conta e os posts aprovados ganham o botão *Publicar no Instagram*
em Posts Detalhados (imagem única ou carrossel de até 10 slides, com legenda e hashtags do post).

**O que precisa existir antes:**

1. **Conta Profissional** (Comercial ou Criador) no Instagram, vinculada a uma **Página do Facebook**
   que você administra.
2. **App no [Meta for Developers](https://developers.facebook.com/)**: crie um app do tipo *Business*,
   adicione o produto **Facebook Login**, e em *Configurações do Login* registre a URI de redirecionamento
   `http://localhost:5173/instagram/callback` (em produção, a URL equivalente do seu domínio).
3. **URL pública do backend.** A Meta baixa cada arte pela URL, então `localhost` não serve. Em
   desenvolvimento, use um túnel:

   ```bash
   ngrok http 8081
   ```

4. **Chaves** — informe pelo painel da conta (recomendado) ou por variáveis antes de `mvn spring-boot:run`:

   ```bash
   $env:INSTAGRAM_APP_ID = "seu-app-id"; $env:INSTAGRAM_APP_SECRET = "seu-app-secret"; $env:INSTAGRAM_PUBLIC_BASE_URL = "https://xxxx.ngrok-free.app"
   ```

5. **Permissões**: enquanto o app estiver em *Modo de Desenvolvimento*, só contas com papel no app
   (admin, desenvolvedor ou testador) publicam — o suficiente para a JH.COMP usar internamente. Para
   liberar a qualquer conta é preciso passar pelo **App Review** da Meta pedindo `instagram_basic`,
   `instagram_content_publish`, `pages_show_list` e `pages_read_engagement`.

Depois disso: aba **Instagram** → *Conectar conta* → autorize no Facebook → volte ao planner.
A tela mostra um checklist dizendo exatamente qual desses itens ainda falta.

**Endpoints** (`/api/instagram`): `GET /status` (público), `GET /authorize-url`, `POST /connect`,
`DELETE /connection` e `POST /publish/{postId}` — os quatro últimos exigem JWT. Publicar grava
`instagramMediaId` e `publishedAt` no post e muda o status para `PUBLICADO`; um post já publicado
não é republicado.

**Limites da Meta:** 25 publicações por 24 h, até 10 imagens por carrossel, JPEG/PNG, e o token da
Página vale ~60 dias (reconecte quando expirar — a tela mostra a validade). Stories e Reels não
estão implementados aqui.

### Assistente de conteúdo (Claude)

O backend fala com a API da Anthropic (modelo `claude-opus-5-5`, configurável em `jhcomp.ai.model`).
Sem `ANTHROPIC_API_KEY` a aplicação sobe normalmente e o assistente aparece desativado na UI.

```bash
# PowerShell
$env:ANTHROPIC_API_KEY = "sk-ant-..."
```

| Método | Rota | Descrição |
| --- | --- | --- |
| GET | `/api/ai/status` | Diz se o assistente está configurado (público) |
| POST | `/api/ai/chat` | Conversa com histórico `[{role, content}]` (exige JWT) |
| POST | `/api/ai/draft` | Gera um post completo a partir de um briefing (exige JWT) |

O `/draft` usa structured outputs, então o rascunho volta com título, pilar, formato, público, resumo,
legenda, hashtags e CTA já nos campos do planner — a tela **Assistente IA** salva isso como post em rascunho.

### Endpoints principais

| Método | Rota | Descrição |
| --- | --- | --- |
| POST | `/api/auth/login` | Emite access + refresh token |
| POST | `/api/auth/refresh` | Renova o access token |
| GET | `/api/posts?status=&audience=` | Lista posts do planner |
| GET | `/api/posts/metrics` | Métricas agregadas (distribuição, formatos, alcance) |
| GET | `/api/posts/code/{code}` | Post por código (`01`…`06`) |
| POST/PUT/PATCH/DELETE | `/api/posts...` | CRUD + troca de status (exige JWT) |
| GET | `/api/assets?kind=&ratio=` | Templates, backgrounds e logos |
| PUT | `/api/posts/{id}/slides` | Substitui as artes do carrossel, na ordem enviada (exige JWT) |
| POST | `/api/assets/upload` | Upload do arquivo + registro do template (multipart, exige JWT) |
| GET | `/api/assets/files/{arquivo}` | Serve o arquivo enviado (prévia e download) |
| GET | `/api/copies?tone=&postCode=` | Legendas e hashtags |

### Editor de Posts

Tela **Editor de Posts** (`/studio`): escolhe proporção (1080×1350 ou 1080×1080), um dos três
layouts e um template do catálogo; os campos — chapéu, manchete, apoio, CTA e selo — caem em
posições fixas, com as fontes (Syne / Plus Jakarta Sans / JetBrains Mono), o vermelho da marca e
as safe zones do Instagram (72 px laterais, 150 px topo/base no 4:5). "Puxar de um post" preenche
os textos a partir do planner, e **Baixar PNG** exporta na resolução nativa.

O bloco **Posição dos textos** move o bloco na arte: ancora em cima, no meio ou embaixo, alinha à
esquerda ou centraliza, e o ajuste fino desloca até 240 px para cima ou para baixo — sempre preso
dentro das margens seguras.

O bloco **Tamanho dos textos** aumenta ou diminui cada grupo de 60% a 160% (manchete; apoio e
tópicos; chapéu, CTA e selo), com botões −/+, slider e botão Padrão. Mesmo no máximo a manchete
encolhe sozinha se não couber nas margens.

No bloco de textos, **Puxar do Guia de Copy** traz uma legenda da biblioteca: *Usar neste slide*
preenche manchete, apoio e CTA; *Dividir em slides* cria um slide por parágrafo da legenda —
atalho para montar carrossel a partir de uma copy já escrita.

### Rascunhos

A aba **Rascunhos**, ao lado de *Editor*, lista os posts em `RASCUNHO` com miniatura da arte:
*Abrir* carrega os textos de volta no editor (salvar de novo atualiza o mesmo post) e *Apagar*
descarta o rascunho com confirmação. As artes geradas pelo editor entram como assets `ARTE`
e por isso **não** aparecem no catálogo da tela Exportador de Templates.

### Fotos nos posts

O bloco **Foto do slide** envia uma imagem sua (PNG, JPG ou WEBP) e a coloca na arte de dois jeitos:

- **Em destaque** — a foto entra num quadro com borda vermelha logo abaixo do cabeçalho, e o texto
  se reorganiza no espaço restante (o apoio é cortado no que couber, nunca invade a faixa de CTA).
- **Como fundo** — a foto cobre a arte inteira no lugar do template, com o véu reforçado
  automaticamente para o texto continuar legível.

O bloco **Enquadramento** controla como a foto se encaixa: *Zoom* de 40% a 300% e os controles
*Horizontal* e *Vertical* movem a imagem; o botão *Padrão* volta ao enquadramento original.
Você também pode **arrastar a foto direto na prévia** — o cursor vira uma mãozinha quando há foto.
Abaixo de 100% a imagem fica menor que a área, sobrando respiro em volta (no quadro, o fundo do
painel; como fundo, o template aparece atrás). No modo em
destaque o quadro só ocupa o espaço que sobra depois do texto — se a manchete crescer, a foto
diminui, e abaixo de 180 px de altura ela deixa de ser desenhada.

As fotos ficam no catálogo como assets `FOTO` e podem ser reusadas em outros slides pela galeria
“Fotos já enviadas”; como as artes (`ARTE`), elas não aparecem na tela de Templates.

### Carrossel

A barra de slides no topo do editor monta carrosséis: **+ Slide** cria um slide novo, o ícone de
cópia duplica o atual, a lixeira remove e as setas reordenam. Cada slide tem template, layout e
textos próprios; a proporção vale para o carrossel inteiro. O botão de download vira
*Baixar N slides* (um PNG por slide, numerados) e *Salvar no planner* sobe todas as artes,
marca o post como `CARROSSEL` e grava a ordem em `post_slides` (migration `V5`).

### Prévia do Instagram

No **Feed Grid**, *Ver no Instagram* abre um pop-up que simula o post no feed: cabeçalho do perfil,
a arte no formato real, setas e indicadores de carrossel, ícones de ação e a legenda com "… mais".
Navega pelas setas do teclado e fecha com Esc.

### Do editor até o "pronto para postar"

1. **Editor de Posts** → *Salvar no planner*: a arte sobe como asset (`kind: ARTE`) e vira um post
   em **Rascunho**, com a imagem como capa. Se você usou "Puxar de um post", ele atualiza aquele post.
2. **Posts Detalhados**: mostra a arte final como ela vai ao ar (com botão para baixar) e o bloco
   **Situação da produção** troca o status em um clique — Rascunho → Agendado → Pronto para postar → Publicado.
   Ali também fica **Apagar post** (com confirmação); as artes permanecem no catálogo de assets.
3. **Guia de Copy**: escolha a legenda e use *Usar no post…* para gravar texto, hashtags e CTA
   naquele post.
4. **Feed Grid**: mostra o grid com as artes reais e as métricas do ciclo.

Tudo é desenhado em `<canvas>` no navegador — `frontend/src/editor/canvas.ts` (primitivas) e
`layouts.ts` (os três layouts). Para criar um layout novo, adicione a função de desenho e registre
em `LAYOUTS`.

### Subindo os templates básicos pelo site

Na tela **Exportador de Templates**, logado, use o bloco **Enviar template**: escolha o arquivo,
dê um código (`BG-V11`, `LOGO-HW-D`…), um nome, e selecione tipo / proporção / tema. O arquivo é
gravado em `backend/uploads/` (configurável por `ASSETS_DIR`) e o card passa a mostrar a prévia
e o botão de baixar. Aceita PNG, JPG, WEBP, SVG, ZIP, AFDESIGN e JSON, até 25 MB.
Apagar o template pelo `DELETE /api/assets/{id}` também remove o arquivo do disco.

### Guia de Copy

Biblioteca de legendas da marca: cada entrada tem título interno, tom, legenda completa, hashtags,
CTA, estratégia e melhor horário. Dá para criar, editar, excluir, copiar para a área de
transferência e **aplicar em um post do planner** (grava legenda, hashtags e CTA no post).
O Assistente IA também alimenta essa biblioteca.

O botão **Tons e hashtags** abre os ajustes:

- **Hashtags fixas** — entram no fim de toda legenda copiada ou aplicada, sem repetir as que a
  legenda já tem (`GET`/`PUT /api/settings/hashtags`).
- **Tons** — cadastro próprio (código, nome e quando usar), com criação, edição e exclusão
  (`/api/tones`). Renomear o código atualiza as legendas que o usam; apagar um tom em uso é
  bloqueado com `409` e a mensagem dizendo quantas legendas dependem dele. Os três iniciais
  (Autoridade, Dor & solução, B2B / Web) vêm da migration `V6`.

### Templates

Os templates reais ficam versionados em `templates/`, organizados por proporção e tema:

```
templates/
├── square-1x1/{dark,light}/BG-SQ-{D,L}0N.png   (1080×1080, 1:1)
├── vertical-4x5/{dark,light}/BG-V-{D,L}0N.png  (1080×1350, 4:5)
├── originais/                                   arquivos como vieram (1024² e 896×1200)
└── manifest.json
```

Os verticais chegaram em 896×1200 (3:4): foram reescalados para cobrir 1080×1350 e cortados
96 px no centro da altura; os quadrados subiram de 1024² para 1080². Os arquivos originais
ficam em `templates/originais/` caso você queira refazer o enquadramento.

Os 24 já estão cadastrados no catálogo. Os 16 registros de exemplo do seed foram removidos
(migration `V3`), porque eram só metadados sem arquivo.

**Política de acesso:** leituras (`GET /api/**`) são públicas para facilitar o uso do painel;
toda escrita exige `Authorization: Bearer <accessToken>`.

## 3. Frontend

http://localhost:5173 — o Vite faz proxy de `/api` para `localhost:8081`, então nenhuma
configuração extra é necessária em desenvolvimento. Para apontar para outra API, defina
`VITE_API_BASE_URL` — é o que cobre o cenário de **domínios separados** (ver "Colocar no ar").

Telas: **Editor de Posts** (monta a arte sobre os templates e baixa o PNG), **Assistente IA**
(chat com o Claude + gerador de post que salva direto no planner), **Feed Grid** (grid 4:5 / 1:1 com tags de status, métricas e cópia rápida de legenda),
**Posts Detalhados** (renderizador 1080×1350 + metadados), **Guia de Copy** (filtro por tom,
copiar legenda + hashtags) e **Exportador de Templates** (catálogo por categoria).

## 4. Colocar no ar

Rodando **sem nenhuma variável nova**, nada muda: o perfil continua `dev`, os uploads vão para
`backend/uploads` no disco e o CORS libera as duas origens do Vite local. Tudo abaixo só entra em
produção.

### 4.1 Passo a passo

1. **Gere os segredos** (uma vez, e guarde no gerenciador de segredos do provedor):

   ```bash
   openssl rand -base64 48   # JWT_SECRET
   openssl rand -base64 32   # JHCOMP_ENCRYPTION_KEY (chave AES de 32 bytes)
   ```

2. **Decida os domínios.** Eles definem duas variáveis que andam juntas:

   | Cenário | `VITE_API_BASE_URL` (build do frontend) | `CORS_ALLOWED_ORIGINS` (backend) |
   | --- | --- | --- |
   | Domínio único, com um proxy roteando `/api` para o backend | `/api` (o padrão) | o domínio do site |
   | **Domínios separados** (frontend e backend em hosts diferentes) | `https://api.exemplo.com/api` | `https://planner.exemplo.com` |

   `VITE_API_BASE_URL` é lida pelo Vite **no momento do build** e gravada no bundle: trocar o valor
   exige rebuild da imagem do frontend, não basta reiniciar o container. Com ela absoluta, o
   frontend também passa a resolver as URLs de asset (`/api/assets/files/...`, relativas no banco)
   contra a origem do backend — é o que mantém as imagens funcionando com os domínios separados.

3. **Escolha onde ficam os uploads.** `ASSETS_STORAGE=disk` (padrão) grava no disco do container,
   que é apagado a cada deploy — serve só com um volume persistente montado em `ASSETS_DIR`. Para
   um provedor com disco efêmero, use `ASSETS_STORAGE=s3` (ver 4.3).

4. **Suba a stack.** Com Docker Compose no próprio host:

   ```bash
   cp .env.prod.example .env    # preencha os valores
   docker compose -f docker-compose.prod.yml --env-file .env up -d --build
   ```

   O frontend sobe na porta 8080 (nginx) e o backend na 8081. Ponha um proxy com HTTPS na frente
   (Caddy, Traefik ou o do próprio provedor) apontando cada domínio para a sua porta.

   Em um PaaS que constrói imagens, aponte dois serviços para `backend/Dockerfile` e
   `frontend/Dockerfile` e passe `VITE_API_BASE_URL` como *build arg* do frontend.

5. **Pegue a senha do admin.** No primeiro boot com o banco vazio, o backend gera uma senha
   aleatória e a imprime **uma única vez** no log. Guarde-a (ou defina `JHCOMP_ADMIN_PASSWORD`
   antes do primeiro boot) e troque-a depois de entrar.

6. **Instagram, se for publicar.** A Meta busca as imagens no seu servidor, então o backend precisa
   estar acessível por **HTTPS público** e `INSTAGRAM_PUBLIC_BASE_URL` precisa apontar para ele.

### 4.2 Variáveis obrigatórias em produção

| Variável | Para que serve |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=prod` | Liga o perfil de produção; sem ele os padrões de dev valem |
| `DB_URL` | JDBC do Postgres, ex.: `jdbc:postgresql://host:5432/jhcomp_planner` |
| `DB_USER` / `DB_PASSWORD` | Credenciais do banco |
| `JWT_SECRET` | Assina os tokens de acesso. **Obrigatória** no perfil `prod`: o boot falha sem ela |
| `JHCOMP_ENCRYPTION_KEY` | Chave AES-GCM (32 bytes em Base64) dos segredos em repouso. **Obrigatória** no perfil `prod` |
| `CORS_ALLOWED_ORIGINS` | Domínio(s) do frontend liberados na API, separados por vírgula |
| `SERVER_PORT` | Porta do backend (8081 por padrão); muitos PaaS exigem a porta que eles injetam |

Opcionais, por funcionalidade:

| Variável | Para que serve |
| --- | --- |
| `INSTAGRAM_APP_ID` / `INSTAGRAM_APP_SECRET` | App da Meta; sem elas a publicação fica desativada |
| `INSTAGRAM_REDIRECT_URI` | Callback do OAuth, ex.: `https://planner.exemplo.com/instagram/callback` |
| `INSTAGRAM_PUBLIC_BASE_URL` | Base HTTPS pública do backend, de onde a Meta baixa as imagens |
| `ANTHROPIC_API_KEY` | Liga o Assistente IA; sem ela a tela aparece desativada |
| `JHCOMP_ADMIN_PASSWORD` | Define a senha do admin no primeiro boot em vez de sortear uma |

### 4.3 Uploads num bucket (S3 ou Cloudflare R2)

O backend tem duas implementações de storage, escolhidas por `ASSETS_STORAGE`:

- `disk` (padrão) — grava em `ASSETS_DIR` (`backend/uploads` localmente).
- `s3` — grava num bucket compatível com S3, via AWS SDK v2. Serve para AWS S3, Cloudflare R2 e
  MinIO.

| Variável | Para que serve |
| --- | --- |
| `ASSETS_STORAGE=s3` | Liga o bucket |
| `ASSETS_S3_BUCKET` | Nome do bucket (obrigatório quando `storage=s3`) |
| `ASSETS_S3_REGION` | Região do bucket; no R2 use `auto` |
| `ASSETS_S3_ENDPOINT` | R2: `https://<account-id>.r2.cloudflarestorage.com`. AWS: deixe vazio |
| `ASSETS_S3_ACCESS_KEY` / `ASSETS_S3_SECRET_KEY` | Credenciais. Vazias, o SDK usa a role da instância |
| `ASSETS_S3_PREFIX` | Prefixo de chave dentro do bucket, para separar ambientes |
| `ASSETS_S3_PUBLIC_BASE_URL` | Domínio público do bucket/CDN |
| `ASSETS_S3_PATH_STYLE` | `true` no MinIO |

As URLs devolvidas pela API seguem funcionando nos dois modos. Com `ASSETS_S3_PUBLIC_BASE_URL`
preenchida, a API devolve a URL direta do bucket; vazia, ela continua devolvendo
`/api/assets/files/{nome}` e o backend busca o objeto no bucket para servir — útil quando o bucket
é privado.

> Os assets já enviados guardam a URL no banco. Trocar de `disk` para `s3` **não** move os
> arquivos: suba-os de novo pela tela de Templates, ou copie a pasta `uploads` para o bucket
> mantendo os nomes.

### 4.4 Imagens Docker

```bash
# Backend: Maven multi-stage → JRE 21 Alpine, porta por SERVER_PORT
docker build -t jhcomp-planner-backend ./backend

# Frontend: build do Vite → nginx, com fallback para index.html nas rotas do React Router
docker build -t jhcomp-planner-frontend \
  --build-arg VITE_API_BASE_URL=https://api.exemplo.com/api ./frontend
```

## 5. Operação do dia a dia

Três scripts em `scripts/`, todos com parâmetros nomeados e valores padrão para esta máquina
(Postgres 18 em `localhost:5432`, banco `jhcomp_planner`, usuário/senha `jhcomp`/`jhcomp`).

### 5.1 Log de erros em arquivo

Toda exceção tratada pelo `GlobalExceptionHandler` — e qualquer erro `WARN`/`ERROR` do resto da
aplicação, inclusive o que estoura fora dos handlers — é gravada com **stack trace completo** em:

```
backend/logs/erros-AAAA-MM-DD.log
```

Um arquivo por dia, **14 dias** de retenção (os mais antigos são apagados no boot). Cada linha traz
o status HTTP, o código do erro, o método e o caminho da requisição, e o stack trace:

```
2026-10-19 16:45:02.118 WARN  [http-nio-8081-exec-3] b.c.j.planner.errors - HTTP 404 NOT_FOUND em GET /api/posts/999999 — br.com.jhcomp.planner.common.NotFoundException: Post 999999 não encontrado
	br.com.jhcomp.planner.common.NotFoundException: Post 999999 não encontrado
		at br.com.jhcomp.planner.post.PostService...
```

Configurado em `backend/src/main/resources/logback-spring.xml`. Dois detalhes de propósito:

- **O console não mudou.** O logger `br.com.jhcomp.planner.errors`, usado pelo handler, tem
  `additivity="false"` e aponta só para o arquivo. O que aparecia no terminal continua igual.
- `logs/` está no `.gitignore` — nada disso vai para o repositório.

Para mudar a pasta ou a retenção, sem editar o XML:

```powershell
$env:LOG_DIR = 'D:\logs\planner'   # padrão: logs (relativo à pasta de onde o backend sobe)
$env:ERROS_MAX_HISTORY = '30'      # padrão: 14 dias
```

### 5.2 Backup num comando

```powershell
.\scripts\backup.ps1                        # destino padrão: %USERPROFILE%\Backups
.\scripts\backup.ps1 -Destino D:\Backups
.\scripts\backup.ps1 -Manter 7              # mantém só os 7 .zip mais recentes no destino
```

Gera `jhcomp-planner-backup-AAAA-MM-DD-HHmmss.zip` com:

| Dentro do .zip | O que é |
| --- | --- |
| `banco.dump` | `pg_dump -F c` (formato custom) do banco inteiro |
| `uploads/` | cópia de `backend/uploads` |
| `backup.txt` | data, máquina, banco de origem e contagem de arquivos |

Parâmetros de conexão, quando não forem os desta máquina: `-DbHost`, `-DbPort`, `-DbName`,
`-DbUser`, `-DbPassword`, e `-BinDir` para o caminho dos binários do Postgres (padrão
`C:\Program Files\PostgreSQL\18\bin`).

> Com `ASSETS_STORAGE=s3` os uploads vivem no bucket, não em `backend/uploads`: aí o `.zip` sai
> só com o banco e o backup dos arquivos é a replicação/versionamento do próprio bucket.

### 5.3 Restore

```powershell
.\scripts\restore.ps1                       # pega o .zip mais recente do destino padrão
.\scripts\restore.ps1 -Arquivo C:\Backups\jhcomp-planner-backup-2026-10-19-162259.zip
.\scripts\restore.ps1 -SomenteBanco
.\scripts\restore.ps1 -SomenteUploads
```

**Pare o backend antes.** O script mostra o manifesto do backup, lista exatamente o que vai ser
sobrescrito (banco e/ou pasta de uploads, com a contagem de arquivos de hoje e a do backup) e só
age depois que você digitar `restaurar`. Qualquer outra resposta cancela sem alterar nada.
`-Force` pula a confirmação, para automação.

O banco é recriado com `pg_restore --clean --if-exists`. A pasta de uploads atual **não é
apagada**: ela é movida para `backend/uploads.antes-<data>` ao lado, caso você precise voltar.

### 5.4 Deploy num comando

```powershell
.\scripts\deploy.ps1 -EnvFile .env          # lê as variáveis de um arquivo CHAVE=valor
.\scripts\deploy.ps1 -Tag v1.2.0
.\scripts\deploy.ps1 -SomenteValidar        # só a checagem de variáveis
```

Quatro etapas, parando na primeira que falhar:

1. **Variáveis obrigatórias** — `SPRING_PROFILES_ACTIVE`, `DB_URL`, `DB_USER`, `DB_PASSWORD`,
   `JWT_SECRET`, `JHCOMP_ENCRYPTION_KEY`, `CORS_ALLOWED_ORIGINS` (a tabela completa está em 4.2).
   Faltando qualquer uma, o script para com código 1 e diz **quais** faltam e para que cada uma
   serve. Também avisa se `SPRING_PROFILES_ACTIVE` não for `prod`.
2. `mvn test` no backend.
3. `npm run build` no frontend (roda `npm ci` antes se `node_modules` não existir).
4. `docker build` das duas imagens — `jhcomp-planner-backend:<tag>` e
   `jhcomp-planner-frontend:<tag>`, esta com `VITE_API_BASE_URL` como *build arg* (padrão `/api`).

`-PularTestes` pula as etapas 2 e 3.

> **Etapa 4 ainda não exercitada.** Esta máquina não tem Docker, então o `docker build` das duas
> imagens nunca rodou aqui. Quando o `docker` não está no PATH, o script **avisa e termina com
> código 0** depois das etapas 1-3, imprimindo os comandos de build para rodar numa máquina com
> Docker — em vez de estourar um erro de comando não encontrado. A primeira build real continua
> pendente (ver 6).

## 6. Verificação já executada

- O planner começa **vazio**: as migrations `V3`/`V4` removem os posts, copies e assets de exemplo
  que o seed inicial trazia (eles não eram conteúdo real da JH.COMP).
- `mvn test` no backend: 48 testes passando (23 originais + 21 de criptografia, segredo JWT e
  revogação de sessão + 4 de storage de assets e CORS).
- `npm run build` (tsc + vite) no frontend: OK.
- `mvn test` após a entrada dos scripts de operação: **67 testes passando**, incluindo 3 novos em
  `common/ErrorLogTest` que travam o caminho handler → log de erros (status, código e stack trace
  anexado em todos os níveis).
- Log em arquivo verificado com o backend subindo de verdade: `backend/logs/erros-2026-10-19.log`
  foi criado e guardou o stack trace completo de uma falha de boot (erro **não** tratado,
  confirmando que o `root` também cai no arquivo).
- Operação (19/10/2026): `scripts/backup.ps1` rodou de verdade nesta máquina e gerou um `.zip` de
  37,7 MB (`banco.dump` + 24 arquivos de `uploads` + `backup.txt`); `scripts/restore.ps1` foi
  validado nos dois caminhos — cancelando na confirmação (nada alterado) e restaurando os uploads
  com `-SomenteUploads -Force`; `scripts/deploy.ps1` para com código 1 listando as variáveis que
  faltam e, com todas presentes, chega à etapa 4 e avisa que não há Docker.
- `docker build` das duas imagens: **não verificado** — esta máquina não tem Docker instalado.
  Os Dockerfiles e o `docker-compose.prod.yml` ainda precisam de uma build real antes do deploy.
- Bateria de 19/10/2026 (2ª rodada): 8 endpoints públicos 200, 5 rotas protegidas 401, as 7 telas
  sem erro de console, carrossel com foto salvo no planner, prévia do Instagram navegando,
  templates sem artes/fotos e `mvn test` com 23 testes passando.
- Bateria completa de 19/10/2026: endpoints públicos 200, escrita sem token 401, validação 400,
  404 e conflito 409 corretos; as 6 telas abrem sem erro de console; carrossel de 3 slides salvo,
  legenda aplicada, status aprovado, prévia do Instagram navegando e upload de template pela UI.
- Stack completa validada nesta máquina: migrations aplicadas e o fluxo ponta a ponta exercitado
  pela UI — montar a arte no editor, salvar como post, marcar "Pronto para postar" e aplicar uma
  legenda do Guia de Copy. A sessão renova o token sozinha quando o access token expira.
- O **Assistente IA** só foi exercitado sem chave (aparece desativado); as chamadas reais à API da
  Anthropic ainda não foram testadas.
