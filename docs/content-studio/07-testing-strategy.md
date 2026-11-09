# 07 — Estratégia de testes

## Regressão

Os 106 testes existentes rodam em todo `mvn test`. O módulo de vídeo não altera nenhuma
classe dos outros pacotes além de configuração (limite de upload), então qualquer teste
antigo quebrado é sinal de efeito colateral, não de mudança esperada.

## Unitários (entregues)

| Classe | O que garante |
|---|---|
| `VideoCommandsTest` | Os argumentos do FFmpeg para cada combinação (com/sem áudio, com/sem legenda, com sincronia); expansão dos marcadores do modelo de comando; marcador desconhecido é recusado |
| `VideoStorageTest` | Extensão fora da lista é recusada; nome com `..` não sai da pasta do projeto; apagar o projeto apaga a pasta |
| `EngineRunnerTest` | Sucesso grava a saída; código de saída ≠ 0 vira erro com o log; saída ausente ou vazia vira erro; tempo limite mata o processo |

O `EngineRunnerTest` usa um **motor falso**: uma classe Java de teste executada com o
próprio `java` da JVM em uso. Assim o teste roda igual no Windows e no Linux da imagem
Docker, sem depender de FFmpeg nem de shell.

## Contrato HTTP (entregue)

`VideoControllerTest`, no molde do `PostControllerTest`, com a cadeia de segurança real:
sem token responde 401 em leitura e escrita; etapa desconhecida responde 400.

## API ao vivo (feito nesta etapa)

O backend foi iniciado contra um PostgreSQL descartável (migrações `V1`–`V12` aplicadas do
zero) com motores falsos, e exercitado por HTTP: pré-requisitos recusados com 400, upload
de `.exe` recusado, job concluído gravando a legenda, job com executável ausente
terminando em ERRO explicado, segundo job no mesmo projeto recusado, cancelamento matando
o processo, novo áudio invalidando a legenda, vínculo com post e sobrevivência do vídeo ao
post apagado, exclusão do projeto levando a pasta.

O FFmpeg não está instalado nesta máquina, então a linha de comando gerada por
`VideoCommands.render` foi executada à parte, num contêiner Alpine igual ao da imagem do
backend, com um vídeo de teste: vídeo + áudio + legenda (MP4 h264/aac cortado na duração
do áudio, legenda visível no quadro), vídeo mudo sem áudio nem legenda, e arquivo que não
é vídeo (erro, sem saída). Achado desse teste: sem fonte instalada a legenda sai
invisível sem erro nenhum — a imagem passou a instalar `font-dejavu`. **O que não foi
feito**: um job RENDER disparado pela API com FFmpeg presente; as duas metades foram
provadas separadamente.

## Pipeline de ponta a ponta (manual, depende de instalação)

Não automatizado: precisa de FFmpeg e, na fase 3, dos motores. Roteiro de verificação:

1. Projeto novo → enviar `base.mp4` (10 s) e `voz.wav` → RENDER → o MP4 toca com o áudio novo.
2. Colar um SRT → RENDER → legendas visíveis; os jobs anteriores continuam no histórico.
3. Enviar outro áudio → a tela mostra legendas e final como ausentes.
4. Iniciar RENDER e cancelar → job CANCELADO, sem arquivo `final-*` novo.
5. Derrubar o backend durante um job → ao subir, o job aparece como ERRO "interrompido".
6. Enviar `malware.exe` renomeado para `.mp4` → o upload entra (só a extensão é
   conferida), o FFmpeg falha ao ler e o job termina em ERRO. Nada é executado.

## Desempenho

Medir por etapa, para um vídeo de 60 s em 1080×1920, e registrar em 05: tempo de parede,
pico de VRAM (`nvidia-smi --query-gpu=memory.used --format=csv -l 1`) e tamanho dos
arquivos. Critério inicial: o pico de VRAM de cada etapa abaixo de 11 GB.

## Frontend

Não há testes de frontend no projeto (01, item 7). O que foi verificado nesta etapa:
`tsc --noEmit` e `vite build` limpos, e a tela exercitada à mão num navegador (1440 px e
celular) contra um backend de teste: criar projeto, rodar uma etapa e ver o histórico
atualizar sozinho, cancelar uma etapa em execução.
