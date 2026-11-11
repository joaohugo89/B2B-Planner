import { useEffect, useState } from 'react';
import { Captions, Clapperboard, Download, FileAudio, Film, History, Play, Plus, Sparkles, Trash2, UserRound, X } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { postsApi, videoApi, type VideoJob, type VideoProject, type VideoStep } from '../api/services';
import { describeError } from '../api/client';
import { useApi } from '../api/useApi';
import { Button, ConfirmAction, Field, Section, Select, TextArea } from '../components/ui';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/primitives';

/**
 * Video Studio: do roteiro ao MP4, uma etapa por vez.
 *
 * <p>Cada etapa é um job no servidor e só depende dos arquivos que o projeto já tem. Por
 * isso a tela é uma lista de etapas refazíveis, não um assistente de "próximo, próximo":
 * corrigir a legenda e renderizar de novo não passa por voz nem por sincronia.</p>
 */
export function VideoStudioPage() {
  const projetos = useApi(() => videoApi.list(), []);
  const [params, setParams] = useSearchParams();
  const [criando, setCriando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  // O projeto aberto mora na URL: o post consegue apontar direto para o vídeo dele.
  const aberto = Number(params.get('projeto')) || projetos.data?.[0]?.id || null;

  async function novo() {
    setCriando(true);
    setErro(null);
    try {
      const criado = await videoApi.create({ title: 'Novo vídeo', script: null, postId: null });
      projetos.reload();
      setParams({ projeto: String(criado.id) });
    } catch (err) {
      setErro(describeError(err));
    } finally {
      setCriando(false);
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="JH.COMP // VIDEO STUDIO"
        title="Produção de vídeo"
        description="Roteiro, voz, sincronia, legendas e render — cada etapa pode ser refeita sozinha."
        actions={
          <Button variant="primary" onClick={novo} busy={criando} icon={<Plus size={14} />}>
            Novo projeto
          </Button>
        }
      />

      <div className="shell flex flex-col gap-6 py-10">
        {erro && <ErrorState message={erro} />}
        {projetos.loading && !projetos.data && <LoadingState />}
        {projetos.error && <ErrorState message={projetos.error} onRetry={projetos.reload} />}
        {projetos.data?.length === 0 && (
          <EmptyState message="Nenhum vídeo ainda. Crie um projeto para começar pelo roteiro ou pelo vídeo-base." />
        )}

        {projetos.data && projetos.data.length > 0 && (
          <>
            <div className="flex gap-2 overflow-x-auto border-b border-surface-high pb-3">
              {projetos.data.map((projeto) => (
                <button
                  key={projeto.id}
                  type="button"
                  onClick={() => setParams({ projeto: String(projeto.id) })}
                  className={`label-code whitespace-nowrap rounded-lg px-4 py-2 transition ${
                    aberto === projeto.id
                      ? 'border-b-2 border-brand bg-surface text-brand-soft'
                      : 'bg-surface-low text-content-muted hover:text-content'
                  }`}
                >
                  {projeto.title}
                </button>
              ))}
            </div>

            {aberto && (
              <Projeto
                key={aberto}
                id={aberto}
                onListChanged={projetos.reload}
                onDeleted={() => {
                  setParams({});
                  projetos.reload();
                }}
              />
            )}
          </>
        )}
      </div>
    </>
  );
}

const ETAPAS: { step: VideoStep; nome: string; faz: string; semMotor: string }[] = [
  { step: 'VOZ', nome: 'Voz', faz: 'Gera o áudio a partir do roteiro.', semMotor: 'Sem motor de voz neste servidor: envie o áudio em Arquivos.' },
  { step: 'LIPSYNC', nome: 'Sincronia labial', faz: 'Faz o vídeo-base falar o áudio.', semMotor: 'Sem motor de sincronia: o render usa o vídeo-base como está.' },
  { step: 'LEGENDAS', nome: 'Legendas', faz: 'Transcreve o áudio para SRT.', semMotor: 'Sem motor de transcrição: escreva ou cole o SRT ao lado.' },
  { step: 'RENDER', nome: 'Render final', faz: 'Junta vídeo, áudio e legendas num MP4.', semMotor: 'FFmpeg não encontrado no servidor.' },
];

const ESTADO: Record<VideoJob['state'], string> = {
  NA_FILA: 'Na fila',
  EXECUTANDO: 'Executando',
  CONCLUIDO: 'Concluído',
  ERRO: 'Erro',
  CANCELADO: 'Cancelado',
};

const ativo = (job: VideoJob) => job.state === 'NA_FILA' || job.state === 'EXECUTANDO';

function Projeto({ id, onListChanged, onDeleted }: { id: number; onListChanged: () => void; onDeleted: () => void }) {
  const projeto = useApi(() => videoApi.get(id), [id]);
  const motores = useApi(() => videoApi.engines(), []);
  const posts = useApi(() => postsApi.list(), []);
  const avatar = useApi(() => videoApi.hasAvatar(), []);
  const [ocupado, setOcupado] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  const dados = projeto.data;
  const emAndamento = dados?.jobs.find(ativo) ?? null;

  // Enquanto houver etapa rodando, a tela pergunta de novo. Sem job ativo, não há tráfego.
  useEffect(() => {
    if (!emAndamento) return;
    const relogio = setInterval(projeto.reload, 2000);
    return () => clearInterval(relogio);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [emAndamento?.id]);

  /** Toda ação da tela passa por aqui: um só lugar para "ocupado" e para a mensagem de erro. */
  async function agir(nome: string, acao: () => Promise<VideoProject | void>) {
    setOcupado(nome);
    setErro(null);
    try {
      const atualizado = await acao();
      if (atualizado) projeto.setData(atualizado);
      else projeto.reload();
    } catch (err) {
      setErro(describeError(err));
    } finally {
      setOcupado(null);
    }
  }

  if (projeto.loading && !dados) return <LoadingState label="Abrindo projeto…" />;
  if (projeto.error && !dados) return <ErrorState message={projeto.error} onRetry={projeto.reload} />;
  if (!dados) return null;

  const travado = Boolean(emAndamento) || Boolean(ocupado);

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <div className="flex flex-col gap-6 lg:col-span-6">
        <Roteiro
          projeto={dados}
          posts={posts.data ?? []}
          travado={travado}
          ocupado={ocupado}
          onSalvar={(campos) =>
            agir('salvar', async () => {
              const salvo = await videoApi.update(id, campos);
              onListChanged();
              return salvo;
            })
          }
          onGerar={(tema) => agir('roteiro', () => videoApi.script(id, tema))}
        />

        <Section icon={<Film size={14} />} title="Arquivos" description="O vídeo-base é você falando para a câmera; o áudio é a fala final. Trocar um deles invalida só o que foi feito a partir dele.">
          <Envio rotulo="Vídeo-base" aceita=".mp4,.mov,.webm" tem={dados.hasBase} travado={travado} enviando={ocupado === 'BASE'}
            onArquivo={(arquivo) => agir('BASE', () => videoApi.upload(id, 'BASE', arquivo))} />
          <Envio rotulo="Áudio" aceita=".wav,.mp3,.m4a" tem={dados.hasAudio} travado={travado} enviando={ocupado === 'AUDIO'}
            onArquivo={(arquivo) => agir('AUDIO', () => videoApi.upload(id, 'AUDIO', arquivo))} />

          <div className="flex flex-wrap items-center gap-3 rounded bg-surface p-3">
            <span className="min-w-0 flex-1 text-xs text-content-muted">
              {avatar.data
                ? 'Avatar salvo: projetos novos já nascem com ele como vídeo-base.'
                : 'Sem avatar salvo: cada projeto novo pede o vídeo-base de novo.'}
            </span>
            <Button
              disabled={!dados.hasBase || travado}
              busy={ocupado === 'avatar'}
              icon={<UserRound size={12} />}
              onClick={() =>
                agir('avatar', async () => {
                  await videoApi.saveAvatar(id);
                  avatar.reload();
                  return dados;
                })
              }
            >
              {avatar.data ? 'Trocar avatar por este vídeo' : 'Salvar como meu avatar'}
            </Button>
          </div>
        </Section>

        <Legendas
          // Outra legenda no servidor (um job de transcrição terminou) remonta o editor.
          // Não usa updatedAt: salvar o título jogaria fora um SRT ainda não salvo.
          key={`${dados.hasSubtitles}-${ultimo(dados, 'LEGENDAS')}`}
          id={id}
          tem={dados.hasSubtitles}
          travado={travado}
          salvando={ocupado === 'legendas'}
          onSalvar={(texto) => agir('legendas', () => videoApi.saveSubtitles(id, texto))}
        />
      </div>

      <div className="flex flex-col gap-6 lg:col-span-6">
        {erro && <ErrorState message={erro} />}

        <Section
          icon={<Clapperboard size={14} />}
          title="Produção"
          description="Produzir tudo roda as etapas em sequência, do roteiro salvo ao MP4. Cada uma também pode ser refeita sozinha."
          actions={
            <Button
              variant="primary"
              disabled={travado || !dados.hasBase || !motores.data?.RENDER}
              busy={ocupado === 'tudo'}
              icon={<Play size={12} />}
              title={dados.hasBase ? undefined : 'Envie o vídeo-base ou salve um avatar antes'}
              onClick={() => agir('tudo', async () => void (await videoApi.produce(id)))}
            >
              Produzir tudo
            </Button>
          }
        >
          <ul className="flex flex-col gap-2">
            {ETAPAS.map((etapa) => {
              const disponivel = motores.data?.[etapa.step] ?? false;
              const rodando = emAndamento?.step === etapa.step;
              return (
                <li key={etapa.step} className="flex flex-wrap items-center gap-3 rounded bg-surface p-3">
                  <div className="flex min-w-0 flex-1 flex-col">
                    <span className="text-sm text-content">{etapa.nome}</span>
                    <span className="text-xs text-content-muted">
                      {!motores.data || disponivel ? etapa.faz : etapa.semMotor}
                    </span>
                  </div>
                  {rodando && emAndamento ? (
                    <>
                      <Barra job={emAndamento} />
                      <Button onClick={() => agir('cancelar', () => videoApi.cancel(emAndamento.id))} icon={<X size={12} />}>
                        Cancelar
                      </Button>
                    </>
                  ) : (
                    <Button
                      variant={etapa.step === 'RENDER' ? 'primary' : 'ghost'}
                      disabled={!disponivel || travado}
                      busy={ocupado === etapa.step}
                      onClick={() => agir(etapa.step, async () => void (await videoApi.submit(id, etapa.step)))}
                    >
                      {pronto(dados, etapa.step) ? 'Refazer' : 'Rodar'}
                    </Button>
                  )}
                </li>
              );
            })}
          </ul>
        </Section>

        <Resultado id={id} tem={dados.hasFinal} versao={ultimo(dados, 'RENDER')} />

        <Section icon={<History size={14} />} title="Histórico">
          {dados.jobs.length === 0 && <p className="text-sm text-content-muted">Nenhuma etapa rodou ainda.</p>}
          <ul className="flex flex-col gap-2">
            {dados.jobs.map((job) => (
              <li key={job.id} className="rounded bg-surface p-3">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="label-code text-content">{job.step}</span>
                  <span className={`label-code ${job.state === 'ERRO' ? 'text-brand-soft' : 'text-content-muted'}`}>
                    {ESTADO[job.state]}
                  </span>
                  <span className="label-code ml-auto text-content-muted">{duracao(job)}</span>
                </div>
                {job.error && <p className="mt-1 text-sm text-brand-soft">{job.error}</p>}
                {job.log && (
                  <details className="mt-1">
                    <summary className="label-code cursor-pointer text-content-muted">Saída do motor</summary>
                    <pre className="mt-1 max-h-48 overflow-auto whitespace-pre-wrap rounded bg-surface-lowest p-2 font-mono text-xs text-content-muted">
                      {job.log}
                    </pre>
                  </details>
                )}
              </li>
            ))}
          </ul>
        </Section>

        <ConfirmAction
          label="Apagar projeto"
          icon={<Trash2 size={12} />}
          question={`Apagar "${dados.title}" e todos os arquivos dele? Não dá para desfazer.`}
          confirmLabel="Apagar projeto"
          disabled={travado}
          busy={ocupado === 'apagar'}
          onConfirm={() =>
            agir('apagar', async () => {
              await videoApi.remove(id);
              onDeleted();
            })
          }
        />
      </div>
    </div>
  );
}

/**
 * Quanto da etapa já foi. Sem número do motor (fila, ou modelo ainda carregando), mostra o
 * estado e o tempo: uma barra parada em 0% diria "travou" sobre algo que está andando.
 */
function Barra({ job }: { job: VideoJob }) {
  if (job.progress === null) {
    return (
      <span className="label-code text-brand-soft" role="status">
        {ESTADO[job.state]}… {job.startedAt ? duracao(job) : ''}
      </span>
    );
  }
  return (
    <div className="flex min-w-[9rem] flex-1 items-center gap-2">
      <div
        className="h-2 flex-1 overflow-hidden rounded bg-surface-high"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={job.progress}
        aria-label={`Progresso da etapa ${job.step}`}
      >
        <div className="h-full bg-brand transition-[width] duration-500" style={{ width: `${job.progress}%` }} />
      </div>
      <span className="label-code w-20 text-right text-brand-soft">
        {job.progress}% · {duracao(job)}
      </span>
    </div>
  );
}

function pronto(projeto: VideoProject, step: VideoStep) {
  return { VOZ: projeto.hasAudio, LIPSYNC: projeto.hasLipsync, LEGENDAS: projeto.hasSubtitles, RENDER: projeto.hasFinal }[step];
}

/** Id do último job concluído da etapa: muda exatamente quando o arquivo dela muda. */
function ultimo(projeto: VideoProject, step: VideoStep) {
  return projeto.jobs.find((job) => job.step === step && job.state === 'CONCLUIDO')?.id ?? 0;
}

function duracao(job: VideoJob) {
  if (!job.startedAt) return '—';
  const fim = job.finishedAt ? new Date(job.finishedAt).getTime() : Date.now();
  const segundos = Math.max(0, Math.round((fim - new Date(job.startedAt).getTime()) / 1000));
  return segundos < 60 ? `${segundos}s` : `${Math.floor(segundos / 60)}min ${segundos % 60}s`;
}

function Roteiro({
  projeto,
  posts,
  travado,
  ocupado,
  onSalvar,
  onGerar,
}: {
  projeto: VideoProject;
  posts: { id: number; code: string; title: string }[];
  travado: boolean;
  ocupado: string | null;
  onSalvar: (campos: { title: string; script: string | null; postId: number | null }) => void;
  onGerar: (tema: string) => void;
}) {
  const [title, setTitle] = useState(projeto.title);
  const [script, setScript] = useState(projeto.script ?? '');
  const [postId, setPostId] = useState(projeto.postId ? String(projeto.postId) : '');
  const [tema, setTema] = useState('');

  // O roteiro gerado pela IA chega pelo servidor: sem isto o campo ficaria com o texto antigo.
  useEffect(() => setScript(projeto.script ?? ''), [projeto.script]);

  const mudou =
    title !== projeto.title || script !== (projeto.script ?? '') || postId !== (projeto.postId ? String(projeto.postId) : '');

  return (
    <Section icon={<Sparkles size={14} />} title="Roteiro">
      <Field label="Título do projeto" value={title} onChange={setTitle} />
      <Select
        label="Publicação do planner"
        value={postId}
        onChange={setPostId}
        hint="Opcional. Vinculado, o vídeo aparece nos Posts Detalhados dessa publicação."
        options={[{ value: '', label: 'Nenhuma' }, ...posts.map((post) => ({ value: String(post.id), label: `${post.code} — ${post.title}` }))]}
      />

      <div className="flex flex-wrap items-end gap-2">
        <div className="min-w-0 flex-1">
          <Field label="Tema para a IA" value={tema} onChange={setTema} placeholder="Ex.: por que limpar o PC por dentro uma vez por ano" />
        </div>
        <Button onClick={() => onGerar(tema)} disabled={!tema.trim() || travado} busy={ocupado === 'roteiro'} icon={<Sparkles size={12} />}>
          Gerar roteiro
        </Button>
      </div>

      <TextArea
        label="Texto falado"
        value={script}
        onChange={setScript}
        rows={10}
        placeholder="O que será dito no vídeo. É este texto que vai para o motor de voz."
        hint={mudou ? 'Alterações não salvas — a etapa de voz usa o texto salvo.' : undefined}
      />

      <div>
        <Button
          variant="primary"
          disabled={!mudou || !title.trim() || travado}
          busy={ocupado === 'salvar'}
          onClick={() => onSalvar({ title: title.trim(), script: script.trim() || null, postId: postId ? Number(postId) : null })}
        >
          Salvar
        </Button>
      </div>
    </Section>
  );
}

function Envio({
  rotulo,
  aceita,
  tem,
  travado,
  enviando,
  onArquivo,
}: {
  rotulo: string;
  aceita: string;
  tem: boolean;
  travado: boolean;
  enviando: boolean;
  onArquivo: (arquivo: File) => void;
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="label-code text-content-muted">
        {rotulo} — {enviando ? 'enviando…' : tem ? 'enviado' : 'falta enviar'}
      </span>
      <input
        type="file"
        accept={aceita}
        disabled={travado}
        onChange={(evento) => {
          const arquivo = evento.target.files?.[0];
          // Limpa o campo: escolher o mesmo arquivo de novo precisa disparar outra vez.
          evento.target.value = '';
          if (arquivo) onArquivo(arquivo);
        }}
        className="rounded bg-surface px-3 py-2 text-xs file:mr-3 file:rounded file:border-0 file:bg-surface-high file:px-3 file:py-1 file:text-content disabled:opacity-60"
      />
    </label>
  );
}

function Legendas({
  id,
  tem,
  travado,
  salvando,
  onSalvar,
}: {
  id: number;
  tem: boolean;
  travado: boolean;
  salvando: boolean;
  onSalvar: (texto: string) => void;
}) {
  const salvo = useApi(() => (tem ? videoApi.subtitles(id) : Promise.resolve('')), [id, tem]);
  const [texto, setTexto] = useState<string | null>(null);
  const atual = texto ?? salvo.data ?? '';

  return (
    <Section icon={<Captions size={14} />} title="Legendas" description="Formato SRT. Salvar aqui derruba só o render — voz e sincronia continuam valendo.">
      <TextArea
        label="SRT"
        value={atual}
        onChange={setTexto}
        rows={8}
        mono
        disabled={salvo.loading}
        placeholder={'1\n00:00:00,000 --> 00:00:02,500\nPrimeira frase da legenda.'}
      />
      <div>
        <Button onClick={() => onSalvar(atual)} disabled={texto === null || travado} busy={salvando}>
          Salvar legendas
        </Button>
      </div>
    </Section>
  );
}

/** O MP4 final. Baixado com o token e tocado de um endereço blob:, que some ao sair. */
function Resultado({ id, tem, versao }: { id: number; tem: boolean; versao: number }) {
  const [url, setUrl] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!tem) {
      setUrl(null);
      return;
    }
    let vivo = true;
    let criado: string | null = null;
    setErro(null);
    videoApi
      .file(id, 'FINAL')
      .then((blob) => {
        if (!vivo) return;
        criado = URL.createObjectURL(blob);
        setUrl(criado);
      })
      .catch((err) => vivo && setErro(describeError(err)));
    return () => {
      vivo = false;
      if (criado) URL.revokeObjectURL(criado);
    };
  }, [id, tem, versao]);

  return (
    <Section icon={<FileAudio size={14} />} title="Resultado">
      {!tem && <p className="text-sm text-content-muted">O vídeo final aparece aqui depois do render.</p>}
      {erro && <ErrorState message={erro} />}
      {tem && !url && !erro && <LoadingState label="Carregando o vídeo…" />}
      {url && (
        <>
          <video src={url} controls className="mx-auto max-h-[70vh] w-full max-w-[24rem] rounded bg-black" />
          <div>
            <a href={url} download={`video-${id}.mp4`} className="btn-ghost">
              <Download size={12} />
              Baixar MP4
            </a>
          </div>
        </>
      )}
    </Section>
  );
}
