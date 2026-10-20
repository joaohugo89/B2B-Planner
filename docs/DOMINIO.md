# planner.joaohugo.com — endereço fixo para o planner

O `joaohugo.com` já usa os servidores de nome da Cloudflare (`camilo` e `meera.ns.cloudflare.com`),
que é exatamente o que esta configuração precisa. O subdomínio `planner` ainda não existe e será
criado pelo próprio túnel.

Como funciona: o container `dominio` abre uma conexão **de dentro para fora** até a Cloudflare, e
ela entrega as visitas de `planner.joaohugo.com` por dentro dessa conexão. Você não abre porta
nenhuma no roteador, não expõe o seu IP e ganha HTTPS pronto.

Com o container parado, o endereço mostra a página de erro 1033 da Cloudflare. Nada da sua
máquina fica alcançável.

---

## Passo a passo, uma vez só

Esta parte é na sua conta — eu não tenho acesso a ela.

### 1. Abra o painel do Zero Trust

Entre em **https://one.dash.cloudflare.com** com a conta dona do `joaohugo.com`.

Na primeira vez, a Cloudflare pede para escolher um plano: use o **Free**. Ele pede um cartão
para confirmar, mas não cobra nada neste uso.

### 2. Crie o túnel

**Networks → Tunnels → Create a tunnel → Cloudflared → Next**

Dê um nome que você reconheça depois, por exemplo `jhcomp-planner`, e **Save tunnel**.

### 3. Copie o token

A tela seguinte mostra comandos de instalação para vários sistemas. **Não precisa instalar nada** —
o container já faz isso. Você só quer o token: na linha do comando do Docker, é o texto longo
depois de `--token`, começando com `eyJ`.

### 4. Guarde o token no `.env`

Acrescente ao final do seu `.env`, na raiz do projeto:

```
CLOUDFLARE_TUNNEL_TOKEN=eyJ...
```

O `.env` não vai para o GitHub. **Esse token dá acesso ao seu túnel: trate como senha.**

### 5. Aponte o endereço para o planner

Ainda na Cloudflare, abra a aba de rotas do túnel (**Published application routes**, ou
**Public Hostname** nas versões anteriores do painel) e adicione uma rota:

| Campo | Valor |
|---|---|
| Subdomain | `planner` |
| Domain | `joaohugo.com` |
| Path | *(vazio)* |
| Service URL | `http://frontend:80` |

**Add route**. O registro DNS do `planner` é criado sozinho.

> O `http://` é obrigatório — sem ele a tela recusa com *"Invalid service URL format"*. E é `http`
> mesmo, não `https`: esse trecho é interno, de um container para outro. O HTTPS fica por conta da
> Cloudflare, na frente. (Em telas mais antigas, isso aparecia como dois campos, *Type* `HTTP` e
> *URL* `frontend:80`.)

### 6. Suba

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.local.yml -f docker-compose.dominio.yml --env-file .env up -d
```

Em menos de um minuto, **https://planner.joaohugo.com** responde.

---

## O dia a dia

Ligar o planner junto com o endereço:

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.local.yml -f docker-compose.dominio.yml --env-file .env up -d
```

Desligar só o endereço, deixando o planner rodando em casa:

```bash
docker compose -f docker-compose.prod.yml stop dominio
```

Ver se o túnel conectou:

```bash
docker compose -f docker-compose.prod.yml logs dominio | grep -i "registered\|error"
```

---

## Depois que o endereço estiver no ar

### Instagram

Na aba da sua conta, preencha:

- **URL pública do backend:** `https://planner.joaohugo.com`
- **URI de redirecionamento:** `https://planner.joaohugo.com/instagram/callback`

E ponha essa mesma URI no app da Meta. Agora que o endereço é fixo, isso é configurado uma vez e
não muda mais — era justamente o que o link temporário não permitia.

### Quem alcança o endereço

**Qualquer pessoa com o endereço vê o feed, os posts e os templates.** Criar, editar e publicar
exige login. Como agora o endereço é fixo e fácil de adivinhar, vale fechar o acesso:

**Zero Trust → Access → Applications → Add an application → Self-hosted**, apontando para
`planner.joaohugo.com`, com uma política que permita só o seu e-mail. A Cloudflare passa a pedir
uma confirmação por e-mail antes de deixar qualquer um chegar ao planner, e o login do planner
continua valendo por dentro.

Sem isso, a sua defesa é só o endereço não ser divulgado.

### Se o computador desligar

O planner sai do ar enquanto a máquina estiver desligada — ele roda aí, não num servidor. Os
containers sobem sozinhos quando o Docker Desktop abre, porque todos usam `restart: unless-stopped`;
confira se o Docker Desktop está configurado para iniciar junto com o Windows.

---

## O endereço some quando eu desligo o container?

Não. O registro DNS vive na Cloudflare e é permanente: existe com o seu computador desligado,
com o Docker fechado e com a internet de casa fora. O que muda é só a resposta.

| Com o container | O DNS | O site |
|---|---|---|
| ligado | resolve | o planner |
| desligado | resolve igual | erro 1033 da Cloudflare ("Tunnel error") |

Essa diferença importa por causa do cache: "o nome não existe" é uma resposta que os
resolvedores guardam por um tempo — foi o que travou o acesso logo depois de criar a rota. Já o
erro 1033 não é cacheado. Religue o container e o site volta em segundos.

(Verificado em 20/10/2026 parando o container `dominio`: o 1.1.1.1 continuou resolvendo e o site
respondeu HTTP 530; ao religar, voltou a 200.)

## Abre no celular, mas não no computador

Acontece quando você tentou acessar o endereço **antes** de criar a rota: o roteador guardou o
"não existe" e repete essa resposta por um tempo. Dá para confirmar assim:

```powershell
Resolve-DnsName planner.joaohugo.com -Server 192.168.0.1
```

Se isso falhar e o mesmo comando com `-Server 1.1.1.1` funcionar, é só o cache do roteador.
O `ipconfig /flushdns` não resolve, porque limpa o cache do Windows, não o dele.

**Reiniciar o roteador** resolve na hora e não deixa nada para desfazer.

Se não der para reiniciar agora, um atalho temporário, no PowerShell **como administrador**:

```powershell
Add-Content -Path "$env:windir\System32\drivers\etc\hosts" -Value "`n104.21.48.120 planner.joaohugo.com  # temporario ate o roteador atualizar"
```

Isso fixa um IP da Cloudflare só para o seu PC. Como esse IP pode mudar um dia, tire a linha
assim que o roteador atualizar:

```powershell
(Get-Content "$env:windir\System32\drivers\etc\hosts") | Where-Object { $_ -notmatch 'planner.joaohugo.com' } | Set-Content "$env:windir\System32\drivers\etc\hosts"
```

---

## Quando algo não funciona

| O que aparece | O que é |
|---|---|
| Erro 1033 da Cloudflare | O container `dominio` não está rodando, ou o token está errado |
| Erro 502 | O túnel conectou, mas o `frontend` está parado — veja `docker compose ps` |
| "defina CLOUDFLARE_TUNNEL_TOKEN no .env" | Falta o passo 4 |
| O site abre mas o painel fica vazio | Veja `logs backend`: provavelmente o backend não subiu |
| DNS não resolve | O registro é criado no passo 5; confira em **DNS → Records** se existe `planner` |
| Abre no celular mas não no PC | O roteador guardou a resposta "não existe" de antes de o subdomínio existir. Veja abaixo |
