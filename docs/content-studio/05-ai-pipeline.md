# 05 — Motores de IA e renderização

Pesquisado em 09/11/2026. **Nada daqui foi instalado nem executado nesta máquina** — o que
está escrito sobre requisitos e licenças vem da documentação e de páginas de terceiros
citadas no fim, e deve ser conferido na fonte oficial antes de uso comercial.

## Resumo

| Papel | Candidata | Código | Pesos / modelo | VRAM citada | Veredito |
|---|---|---|---|---|---|
| LLM | Ollama | MIT | varia **por modelo** (confira cada um) | 5–9 GB para modelos de 7–12B em Q4 | Usar — o backend já fala o protocolo |
| Transcrição | whisper.cpp | MIT | Whisper: MIT | ~3 GB (large-v3) ou CPU | Usar — binário nativo, sem Python |
| Voz | Chatterbox Multilingual | MIT | MIT | ~6 GB (não oficial) | Usar — tem português; marca d'água embutida |
| Sincronia labial | MuseTalk 1.5 | MIT | **não confirmado na fonte oficial** | ~4 GB | Usar para teste; confirmar licença antes de publicar comercialmente |
| Motion graphics | Motion Canvas | MIT | — | CPU | Adiar — manutenção incerta |
| Render | FFmpeg | LGPL 2.1+ (GPL com `libx264`) | — | CPU | Usar |

## Ollama

Expõe `/v1/chat/completions`, que o `OpenAiCompatibleProvider` já consome. Configuração no
painel da conta: provedor `openai`, URL base `http://localhost:11434/v1`, modelo instalado
(ex.: `qwen2.5:7b`) e **qualquer texto** na chave — o assistente só liga com a chave
preenchida (ver 01, item 3). Com o backend em contêiner, a URL é
`http://host.docker.internal:11434/v1`.

O Ollama em si é MIT, mas cada modelo tem a própria licença (Llama: licença da Meta;
Gemma: termos do Google; Qwen e Mistral variam por tamanho). Para texto de marketing isso
raramente restringe, mas é por modelo, não pelo Ollama.

VRAM: o Ollama deixa o modelo carregado por 5 minutos. Um modelo de 8 GB residente mais o
Chatterbox estouram os 12 GB. Defina `OLLAMA_KEEP_ALIVE=0` no ambiente do Ollama, ou use
um modelo pequeno para roteiro.

## Whisper

`whisper.cpp` (C++, MIT) em vez do pacote Python: é um executável só, escreve SRT direto
(`-osrt`) e roda bem em CPU no 7900X. Build com CUDA não é distribuído nos releases
oficiais segundo a pesquisa — compila-se localmente ou usa-se CPU. Para um reel de 60 s, a
CPU basta.

## Chatterbox (voz)

Código e pesos MIT segundo a Resemble AI; a variante multilíngue cobre 23 idiomas com
português, e há ajuste fino para português do Brasil. Clona voz a partir de ~10 s de
referência. Todo áudio sai com marca d'água PerTh. É Python: roda em ambiente próprio
(venv no WSL2), chamado por script.

Alternativa se a qualidade em pt-BR não convencer: **Piper** (MIT, vozes prontas, sem
clonagem, roda em CPU) como plano B sem dependência de GPU.

## MuseTalk (sincronia labial)

Código MIT. Para os pesos, páginas espelho indicam MIT, mas a pesquisa não conseguiu
confirmar na página oficial do Hugging Face; e o projeto depende de outros modelos
(whisper, sd-vae-ft-mse, dwpose, s3fd/face-parse), cada um com a sua licença. **Antes de
publicar vídeo comercial gerado com ele, conferir a licença dos pesos e das dependências.**

Alternativa: **LatentSync** (qualidade maior, ~18 GB de VRAM na versão 1.6 — não cabe em
12 GB) e **Wav2Lip** (leve, mas os pesos são só para uso não comercial). Para 12 GB e uso
comercial, MuseTalk é a candidata viável.

## RTX 5070

A série 50 (arquitetura `sm_120`) exige PyTorch 2.7+ compilado para CUDA 12.8 (`cu128`).
Builds para `cu124`/`cu126` não funcionam ou caem para um modo degradado. Isso afeta
Chatterbox e MuseTalk: os `requirements.txt` deles fixam versões antigas do torch, e a
instalação precisa sobrescrever:

```
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu128
python -c "import torch; print(torch.cuda.get_arch_list())"   # deve listar sm_120
```

Este é o maior risco técnico do projeto: MuseTalk depende de `mmcv`/`mmpose`, que
costumam não ter binário para torch recente. Se não compilar, o plano B é rodar o motor em
contêiner com imagem que já resolva isso, mantendo o mesmo contrato de comando.

## Motion Canvas

MIT, mas o último push encontrado no repositório principal é de fevereiro de 2025, e há
um fork comunitário (`canvas-commons`). Adiado para a fase 5; antes disso, FFmpeg cobre
legendas, logotipo sobreposto e vinheta com filtros (`subtitles`, `overlay`, `concat`).
Alternativa a avaliar na hora: Remotion (licença própria, paga para empresas acima de um
porte — conferir).

## FFmpeg

LGPL 2.1+; um build com `libx264` passa a GPL. Para uso interno (rodar o binário e
publicar o **vídeo**), nenhuma das duas impõe obrigação sobre o conteúdo gerado. A
obrigação aparece ao **distribuir o binário** junto com um produto. O backend chama o
executável, não liga a biblioteca.

## Integração com o Java

Todos entram pelo mesmo contrato do `EngineRunner` (ver 03): comando configurado,
argumentos em lista, um arquivo de saída. Tempo limite por etapa
(`VIDEO_STEP_TIMEOUT_MINUTES`, padrão 60), cancelamento matando o processo, e as últimas
linhas da saída gravadas no job.

## Estimativas para um reel de 60 s (não medidas)

| Etapa | Onde | Ordem de grandeza |
|---|---|---|
| Roteiro (Ollama 7B) | GPU | segundos |
| Voz (Chatterbox) | GPU | dezenas de segundos |
| Sincronia (MuseTalk) | GPU | alguns minutos |
| Legendas (whisper.cpp) | CPU | dezenas de segundos |
| Render (libx264) | CPU | segundos |

Medir na fase 3 e substituir esta tabela por números reais.

## Fontes

- [MuseTalk — licença e dependências (espelho do README)](https://huggingface.co/marcosremar2/MuseTalk/blob/main/README.md)
- [Licenças de lip sync open source para uso comercial](https://www.creativeainews.com/articles/open-source-lip-sync-licenses-commercial-use-2026/)
- [Chatterbox Multilingual — Resemble AI](https://resemble.ai/learn/models/chatterbox-multilingual)
- [Chatterbox — README no Hugging Face](https://huggingface.co/ResembleAI/chatterbox/blob/main/README.md)
- [PyTorch e sm_120 — fórum oficial](https://discuss.pytorch.org/t/pytorch-support-for-sm120/216099)
- [whisper.cpp](https://github.com/mmp/whisper.cpp)
- [Motion Canvas — dados do repositório](https://repos.ecosyste.ms/hosts/GitHub/repositories/motion-canvas%2Fmotion-canvas)
- [FFmpeg — licença e x264](https://ffmpeg.org/pipermail/ffmpeg-user/2021-November/053829.html)
