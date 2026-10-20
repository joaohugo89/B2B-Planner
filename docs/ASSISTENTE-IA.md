# Trocando o serviço de IA do assistente

O assistente não está preso a um fornecedor. Na aba da sua conta → **Chaves das integrações →
Assistente de IA**, o campo **Serviço** escolhe quem responde, e a mudança vale na hora, sem
reiniciar nada.

## Antes de escolher: o que é gratuito de verdade

**A OpenAI não tem chave de API gratuita.** O ChatGPT grátis é só o site; a API é um produto
separado, cobrado por crédito comprado. O que existe de gratuito são outros serviços que falam
o mesmo protocolo da OpenAI — e é por isso que o painel aceita todos eles.

| Serviço | Custo | Onde pegar a chave |
|---|---|---|
| **Groq** | Gratuito dentro de um limite diário, sem cartão | console.groq.com |
| **OpenRouter** | Tem modelos gratuitos (nome terminando em `:free`) | openrouter.ai |
| **Ollama** | Zero, roda no seu computador | não precisa de chave |
| **Claude (Anthropic)** | Pago por uso, melhor qualidade de texto | console.anthropic.com |
| **OpenAI** | Pago por crédito comprado | platform.openai.com |

## Passo a passo

1. Crie a conta no serviço escolhido e gere uma chave de API.
2. No planner, abra a aba da sua conta (ícone do usuário no topo).
3. Em **Serviço**, escolha o atalho correspondente. A URL base e um modelo sugerido já vêm
   preenchidos.
4. Cole a chave em **Chave de API** e confira o **Modelo**.
5. Clique em salvar e teste no **Assistente IA**.

A chave fica cifrada no banco (AES-GCM) e nunca volta para a tela: o painel só mostra se está
definida ou não.

### Modelos que costumam funcionar bem

| Serviço | Modelo |
|---|---|
| Groq | `llama-3.3-70b-versatile` |
| OpenRouter | qualquer um com `:free` no fim, copiado do catálogo deles |
| Ollama | `llama3.1` (rode antes `ollama pull llama3.1`) |
| Claude | `claude-opus-5-5` |
| OpenAI | `gpt-5` |

### Ollama: dois detalhes

- A URL base é `http://localhost:11434/v1`. Rodando o planner em Docker, use
  `http://host.docker.internal:11434/v1`, senão o container procura o Ollama dentro dele mesmo.
- O campo da chave não pode ficar vazio (é ele que liga o assistente): escreva qualquer coisa,
  como `ollama`.

## O que muda na prática

O **chat** funciona igual em todos. O **rascunho estruturado** (aquele que devolve título,
legenda e hashtags em campos separados) depende do modelo respeitar o pedido de responder em
JSON: o Claude garante isso pelo próprio protocolo, e nos demais o backend pede JSON e tolera
resposta cercada por ```json. Se um modelo pequeno responder fora do formato, o painel avisa
com "o modelo respondeu fora do formato esperado" — a saída é trocar por um modelo maior.

O **consumo em tokens** continua sendo registrado igual, qualquer que seja o serviço.

## Configurando por variável de ambiente

Útil no Docker, quando você quer o valor já de pé no primeiro boot (o painel sobrescreve):

```
AI_PROVIDER=openai
AI_BASE_URL=https://api.groq.com/openai/v1
AI_MODEL=llama-3.3-70b-versatile
AI_API_KEY=gsk_...
```

Com `AI_PROVIDER=anthropic`, a URL base pode ficar vazia.
