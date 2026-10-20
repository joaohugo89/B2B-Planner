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

| O quê | Onde |
|---|---|
| Painel | http://localhost:8090 |
| API | http://localhost:8081 |
| Banco (DBeaver, pgAdmin, psql) | `localhost:5440` |

### Login do painel

- **Usuário:** `admin@jhcomp.com.br`
- **Senha:** a que está no seu `.env`, na linha `JHCOMP_ADMIN_PASSWORD`. Para ver:

```bash
grep JHCOMP_ADMIN_PASSWORD .env
```

A senha do `.env` só é usada **no primeiro boot**, quando o usuário admin é criado. Se o banco já
existia, vale a senha antiga. O mesmo acontece ao restaurar um dump de outra instalação: o dump
traz a tabela `app_users` junto, e passa a valer a senha que você usava lá. E se você subir sem `JHCOMP_ADMIN_PASSWORD`, o backend sorteia uma e
a imprime uma única vez no log:

```bash
docker compose -f docker-compose.prod.yml logs backend | grep -i -A2 "senha gerada"
```

Para voltar a uma senha conhecida sem perder os dados, apague só o usuário e reinicie o backend —
ele recria o admin com a senha do `.env`:

```bash
docker compose -f docker-compose.prod.yml exec postgres psql -U jhcomp -d jhcomp_planner -c "delete from app_users"
```

### 3. Entrando no banco

**Pelo terminal, sem instalar nada** (o psql já está dentro do container):

```bash
docker compose -f docker-compose.prod.yml exec postgres psql -U jhcomp -d jhcomp_planner
```

Dentro do psql: `\dt` lista as tabelas, `\d posts` descreve uma, `\q` sai.

Uma consulta direta, sem entrar no modo interativo:

```bash
docker compose -f docker-compose.prod.yml exec postgres psql -U jhcomp -d jhcomp_planner -c "select code, title, status from posts"
```

**Por um programa gráfico** (DBeaver, pgAdmin, DataGrip), com a stack no ar:

| Campo | Valor |
|---|---|
| Host | `localhost` |
| Porta | `5440` |
| Banco | `jhcomp_planner` |
| Usuário | o `DB_USER` do seu `.env` (`jhcomp`) |
| Senha | o `DB_PASSWORD` do seu `.env` |

> A porta 5440 é a saída do container. As portas 5432 a 5437 já são das instâncias do PostgreSQL
> instaladas no Windows — por isso o container usa uma porta mais alta, e os dois mundos convivem
> sem conflito. **São bancos diferentes:** mudar algo numa porta não muda nada na outra.

**Backup do banco do container:**

```bash
docker compose -f docker-compose.prod.yml exec postgres pg_dump -U jhcomp -d jhcomp_planner > backup.sql
```

### 4. Comandos do dia a dia

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

### 5. Depois de mudar o código

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.local.yml --env-file .env up -d --build
```

Mexeu só no frontend? Troque o final por `--build frontend` e sobe mais rápido.

---

## Endereço fixo (planner.joaohugo.com)

Para um endereço que não muda, use o `docker-compose.dominio.yml`: o passo a passo está em
[DOMINIO.md](DOMINIO.md). É o indicado para usar o Instagram, que exige uma URI de
redirecionamento fixa.

## Link temporário para acessar de fora

Serve para abrir o planner no celular, mostrar para alguém, ou dar à Meta um endereço HTTPS de
onde baixar as artes na hora de publicar no Instagram. O endereço é sorteado a cada vez, não
precisa de conta e some quando você desliga.

**Ligar** (com a stack já no ar):

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.local.yml -f docker-compose.tunnel.yml --env-file .env up -d tunnel
```

**Ver o endereço** (demora uns segundos para aparecer):

```bash
docker compose -f docker-compose.prod.yml logs tunnel | grep trycloudflare.com
```

**Desligar o link**, deixando o planner rodando em casa:

```bash
docker compose -f docker-compose.prod.yml stop tunnel
```

> **Enquanto o link está no ar, qualquer pessoa que o tenha alcança o painel.** Ver o feed, os
> posts e os templates é público por desenho; criar, editar e publicar exige login. Mesmo assim,
> ligue só quando precisar e desligue depois — e nunca divulgue o endereço.

Funciona porque o nginx do frontend serve a API em `/api`, no mesmo endereço do painel. Por isso
o `.env` usa `VITE_API_BASE_URL=/api`: o mesmo build funciona em `localhost:8090`, no link
temporário e no domínio definitivo, sem rebuild e sem configurar CORS.

Usando o link para o Instagram, lembre de pôr esse mesmo endereço em **URL pública do backend**,
na aba da sua conta — e de atualizar quando ele mudar.

---

## Por que duas portas diferentes das padrões

O `docker-compose.prod.yml` sozinho é o arquivo de servidor: painel na 8080, banco sem porta
publicada. Nesta máquina essas portas já têm dono — o Apache na 8080 e seis instâncias do
PostgreSQL do Windows nas portas 5432 a 5437. Por isso o `docker-compose.local.yml` troca para
**8090** (painel) e **5440** (banco).

Esse segundo arquivo usa `!override` nas portas. Sem ele, o Compose **soma** as listas dos dois
arquivos e o painel acabaria publicado na 8080 e na 8090 ao mesmo tempo.

Num servidor de verdade, use o `docker-compose.prod.yml` sozinho.

---

## Quando algo não sobe

| Sintoma | O que é |
|---|---|
| `error during connect` / `pipe docker_engine` | Docker Desktop fechado |
| `port is already allocated` | Outro programa na porta — o Apache na 8080, o Postgres 18 na 5432 |
| Painel abre mas diz "Falha de comunicação" | `VITE_API_BASE_URL` sem o `/api` no fim, ou `CORS_ALLOWED_ORIGINS` sem a porta certa |
| Mudou o `VITE_API_BASE_URL` e nada aconteceu | Essa variável entra no bundle no build: refaça com `--build frontend` |
| `backend` reiniciando sem parar | Veja `logs backend`: quase sempre é uma variável obrigatória vazia no `.env` |
| `Tag mismatch` / `AEADBadTagException` no log | O banco veio de outra instalação e os segredos foram cifrados com outra `JHCOMP_ENCRYPTION_KEY`. O backend trata esses valores como não configurados e avisa no log: basta redigitar a chave da IA e o app secret do Instagram na aba da sua conta |
| Senha do admin não funciona | A do `.env` só vale no primeiro boot; veja em "Login do painel" como recuperar |
