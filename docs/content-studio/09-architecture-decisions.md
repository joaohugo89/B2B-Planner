# 09 — Decisões arquiteturais

## ADR-01 — Monólito modular, não serviços

- **Contexto**: um usuário, uma máquina, uma GPU; backend já organizado por domínio.
- **Decisão**: o vídeo é mais um pacote (`video`) do mesmo Spring Boot.
- **Alternativas**: (a) serviço de vídeo separado; (b) arquitetura orientada a eventos.
- **Por quê**: (a) duplicaria autenticação, configuração e implantação para um módulo que
  compartilha banco e usuário; (b) eventos desacoplam produtores e consumidores que aqui
  são o mesmo processo.
- **Consequência**: um deploy só. Se o vídeo precisar escalar sozinho, o pacote já tem
  fronteira clara (só depende de `ai` e de um id de post) para ser extraído.

## ADR-02 — Fila no banco e uma thread, sem Redis nem RabbitMQ

- **Contexto**: jobs de minutos, no máximo um por vez por causa da VRAM.
- **Decisão**: tabela `video_jobs` + executor de uma thread, no molde do
  `PublishStateStore` do Instagram.
- **Alternativas**: Redis (fila), RabbitMQ, Spring Batch, Quartz.
- **Por quê**: todas resolvem distribuição e concorrência, e o requisito é justamente
  **não** ter concorrência. O banco já dá durabilidade e histórico.
- **Consequência**: jobs na fila em memória se perdem num restart; por isso, no boot, tudo
  que estava NA_FILA ou EXECUTANDO vira ERRO com a explicação, e o usuário reenvia com um
  clique. Trocar por uma fila real muda só `VideoPipeline`.

## ADR-03 — Motores por linha de comando

- **Contexto**: Chatterbox e MuseTalk são Python; o backend é e continua sendo Java.
- **Decisão**: cada motor é um comando configurável que lê arquivos e grava um arquivo.
- **Alternativas**: (a) servidor HTTP por motor; (b) embutir via JNI/ONNX no Java.
- **Por quê**: processo que nasce e morre libera a VRAM sozinho e não exige serviço
  residente; (b) significaria reimplementar os pipelines dos modelos.
- **Consequência**: paga-se o tempo de carregar o modelo a cada job (segundos, contra
  minutos de processamento). Backend em contêiner não alcança motores no host — ver 08.

## ADR-04 — Armazenamento próprio para vídeo, fora do `AssetStorage`

- **Contexto**: `AssetStorage` serve imagens com URL pública e tem versão S3.
- **Decisão**: `VideoStorage` em disco, uma pasta por projeto, leitura só autenticada.
- **Por quê**: os motores precisam de **arquivo local** — com S3, cada etapa baixaria e
  subiria gigabytes; e vídeo de rosto e voz não deve ter URL pública.
- **Consequência**: vídeo não vai para S3/R2. Se for preciso, publica-se só o `final`.

## ADR-05 — `VideoProject` não é um "ContentProject" genérico

- **Decisão**: tabela específica de vídeo, ligada ao post por `post_id`.
- **Por quê**: só há dois tipos de conteúdo (post e vídeo), com campos quase disjuntos.
  Uma abstração comum hoje seria uma tabela com metade das colunas nulas.
- **Consequência**: se surgir um terceiro tipo com muito em comum, extrai-se a base então.

## ADR-06 — Substituir arquivo apaga o anterior

- **Decisão**: sem versões de arquivo; o histórico é o dos jobs.
- **Por quê**: cada versão custa de dezenas a centenas de megabytes, e o que se quer
  consultar depois é "o que rodou e por que falhou", não o vídeo intermediário antigo.
- **Consequência**: não dá para voltar a um render anterior — renderiza-se de novo.

## ADR-07 — Roteiro pela IA que já existe

- **Decisão**: `AiAssistantService.chat` com um pedido de roteiro; nenhum cliente novo.
- **Por quê**: o provedor "openai" já atende o Ollama, e o prompt de marca já está lá.
- **Consequência**: a qualidade do roteiro depende do modelo escolhido no painel.

## ADR-08 — Arquivo de vídeo entregue inteiro, sem streaming por faixa

- **Decisão**: a tela baixa o arquivo com o token e toca de um `blob:`.
- **Por quê**: `<video src>` não envia o cabeçalho de autenticação, e abrir uma URL
  pública contraria a ADR-04. Reels têm dezenas de megabytes.
- **Consequência**: vídeos longos (centenas de MB) ocupam memória do navegador. Quando
  isso incomodar: URL assinada de curta duração + suporte a `Range`.
