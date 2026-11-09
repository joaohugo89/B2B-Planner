# 02 — Requisitos do produto

## Visão

O JH.COMP Content Studio é o JH Planner com produção de vídeo: uma plataforma só para
planejar, criar e acompanhar conteúdo, rodando na máquina do próprio João, sem serviço pago
obrigatório.

## Módulos

| Módulo | Papel | Situação |
|---|---|---|
| Planner | Posts, grade, legendas, editor de artes, templates, Instagram | Existe |
| Video Studio | Projeto de vídeo: roteiro → voz → sincronia → legendas → render | **Núcleo entregue nesta etapa** |
| AI Content Engine | Ideias, roteiros, legendas | Existe (`ai/`); roteiro de vídeo acrescentado |
| Analytics | Desempenho real das publicações | Não iniciado (roadmap, fase 5) |

## Requisitos funcionais

Implementados:

- RF01 Criar, listar, editar e apagar projetos de vídeo.
- RF02 Vincular um projeto a uma publicação do Planner (opcional).
- RF03 Gerar o roteiro com a IA configurada, e editá-lo à mão.
- RF04 Enviar vídeo-base (mp4, mov, webm) e áudio (wav, mp3, m4a).
- RF05 Gerar a voz a partir do roteiro, quando houver motor de voz configurado.
- RF06 Sincronizar lábios (vídeo-base + áudio), quando houver motor configurado.
- RF07 Gerar legendas por transcrição, quando houver motor; editar o SRT à mão sempre.
- RF08 Renderizar o MP4 final com FFmpeg (áudio trocado, legendas queimadas).
- RF09 Cada etapa é um job independente: refazer legendas não refaz voz nem sincronia.
- RF10 Acompanhar estado, log e erro de cada job; cancelar; histórico por projeto.
- RF11 Assistir e baixar o resultado; ver, no post, os vídeos vinculados a ele.

Planejados (não implementados): perfis de voz gerenciados pela tela, templates de vídeo,
motion graphics, campanhas, calendário editorial, biblioteca de mídia unificada, analytics.

## Requisitos não funcionais

- RNF01 Java + Spring Boot é o único backend; motores de IA são processos externos.
- RNF02 Um job pesado por vez (12 GB de VRAM não comportam dois modelos carregados).
- RNF03 Nada enviado pelo usuário é executado; só extensões de mídia conhecidas entram.
- RNF04 Arquivos de vídeo exigem login — não há URL pública como a das artes.
- RNF05 Reinício do backend não deixa job preso: o que estava rodando vira erro explicado.
- RNF06 Funciona sem nenhum motor de IA instalado (envio manual de áudio + FFmpeg).

## Uso de voz e imagem

O sistema é de um usuário e processa a voz e o rosto dele mesmo. Ainda assim: o áudio de
referência de voz fica no disco do próprio usuário, nunca sai da máquina, e não há função
para clonar voz a partir de arquivo de terceiros com outro nome. O Chatterbox grava marca
d'água inaudível (PerTh) em todo áudio gerado — é comportamento do motor, e deve ser
mantido ligado.

## Escopo do MVP entregue

MVP 1 (estrutura: projeto, arquivos, jobs) e MVP 2 (upload, áudio, render, histórico,
status na tela) completos. Do MVP 3, o roteiro por IA e os pontos de encaixe de voz,
transcrição e sincronia — que só passam a produzir algo depois de os motores serem
instalados (05 e 08). Do MVP 4, a tela do Video Studio e o vínculo com o Planner.
