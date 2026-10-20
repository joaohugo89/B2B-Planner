# Rodando o Planner no Docker

Dois jeitos de trabalhar. Escolha um:

| | O que sobe | Quando usar |
|---|---|---|
| **Modo desenvolvimento** | Só o Postgres no Docker; backend e frontend na mão | Dia a dia, mexendo no código: salvou, recarregou |
| **Modo produção** | Postgres + backend + frontend, tudo em containers | Testar como vai para o ar, ou rodar de verdade num servidor |

---

## Antes de começar

Abra o **Docker Desktop** e espere o ícone da baleia ficar verde ("Engine running").
Nada abaixo funciona com ele fechado. Para conferir:

```bash
docker ps
```

Se responder com um cabeçalho de tabela (mesmo vazio), está no ar.

---

## Modo desenvolvimento

Sobe só o banco; você continua rodando o backend e o frontend como já roda hoje.

```bash
docker compose up -d
```

O Postgres fica em `localhost:5432`, banco `jhcomp_planner`, usuário `postgres`, senha `postgres`.

> **Atenção:** a sua instalação do PostgreSQL 18 na máquina também usa a 5432. Rode um ou outro,
> nunca os dois. Para usar o container, pare o serviço do Windows antes:
> `Stop-Service postgresql-x64-18` (e `Start-Service` para voltar).

Para desligar:

```bash
docker compose down
```

---

## Modo produção (tudo em container)

### 1. Crie o arquivo de configuração, uma vez

```bash
cp .env.prod.example .env
```

Abra o `.env` e preencha, no mínimo:

- `DB_PASSWORD` — invente uma senha
- `JWT_SECRET` — gere com `openssl rand -base64 48`
- `JHCOMP_ENCRYPTION_KEY` — gere com `openssl rand -base64 32`
- `CORS_ALLOWED_ORIGINS` — `http://localhost:8090` para testar na sua máquina
- `VITE_API_BASE_URL` — `http://localhost:8081/api` para testar na sua máquina

O `.env` **nunca** vai para o GitHub: já está no `.gitignore`.

> A `JHCOMP_ENCRYPTION_KEY` cifra as chaves do Instagram e da IA no banco. Se você trocá-la
> depois, o que já estava salvo deixa de abrir e precisa ser digitado de novo no painel.

### 2. Suba

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.local.yml --env-file .env up -d --build
```

O `--build` só é necessário na primeira vez e depois de mudar o código.

Pronto:

- **Painel:** http://localhost:8090
- **API:** http://localhost:8081

A senha do admin é gerada no primeiro boot e aparece no log:

```bash
docker compose -f docker-compose.prod.yml logs backend | grep -i senha
```

### 3. Comandos do dia a dia

```bash
docker compose -f docker-compose.prod.yml ps
```

```bash
docker compose -f docker-compose.prod.yml logs -f backend
```

Desligar, guardando o banco:

```bash
docker compose -f docker-compose.prod.yml down
```

Desligar e **apagar o banco e os uploads** (recomeça do zero):

```bash
docker compose -f docker-compose.prod.yml down -v
```

### 4. Depois de mudar o código

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.local.yml --env-file .env up -d --build
```

Mexeu só no frontend? Troque o final por `--build frontend` e sobe mais rápido.

---

## Por que a porta 8090?

O `docker-compose.prod.yml` publica o painel na 8080, que é a porta padrão num servidor.
Na sua máquina o Apache (`httpd.exe`) já ocupa a 8080, então o `docker-compose.local.yml`
muda só essa porta para 8090. Num servidor de verdade, use o `docker-compose.prod.yml` sozinho.

---

## Quando algo não sobe

| Sintoma | O que é |
|---|---|
| `error during connect` / `pipe docker_engine` | Docker Desktop fechado |
| `port is already allocated` | Outro programa na porta — o Apache na 8080, o Postgres 18 na 5432 |
| Painel abre mas diz "Falha de comunicação" | `VITE_API_BASE_URL` sem o `/api` no fim, ou `CORS_ALLOWED_ORIGINS` sem a porta certa |
| Mudou o `VITE_API_BASE_URL` e nada aconteceu | Essa variável entra no bundle no build: refaça com `--build frontend` |
| `backend` reiniciando sem parar | Veja `logs backend`: quase sempre é uma variável obrigatória vazia no `.env` |
