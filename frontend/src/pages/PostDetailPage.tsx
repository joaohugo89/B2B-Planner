import { useEffect, useState } from 'react';
import { Copy, Download, Instagram, Loader2, Pencil, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { instagramApi, postsApi, type PublishStatus } from '../api/services';
import { describeError } from '../api/client';
import { useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { PublicacaoEditor } from '../components/PublicacaoEditor';
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusTag } from '../components/primitives';
import type { Audience, Post, PostStatus } from '../types/api';
import { assetUrl } from '../api/assetUrl';

const AUDIENCE_LABEL: Record<Audience, string> = {
  B2B: 'B2B',
  B2C: 'B2C',
  HIBRIDO: 'Híbrido',
};

export function PostDetailPage() {
  const { session } = useAuth();
  const posts = useApi(() => postsApi.list(), [Boolean(session)]);
  const [selected, setSelected] = useState<string | null>(null);

  const current = posts.data?.find((p) => p.code === selected) ?? posts.data?.[0] ?? null;

  return (
    <>
      <PageHeader
        eyebrow="JH.COMP // POST ART SYSTEM 4:5"
        title="Galeria & Renderizador de Posts"
        description="Visualizador dos posts em formato portrait 1080×1350, com metadados táticos e copy estratégica."
      />

      <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-10 md:px-8">
        {posts.loading && <LoadingState />}
        {posts.error && <ErrorState message={posts.error} onRetry={posts.reload} />}
        {posts.data?.length === 0 && <EmptyState message="Nenhum post para inspecionar." />}

        {posts.data && posts.data.length > 0 && (
          <>
            <div className="flex gap-2 overflow-x-auto border-b border-surface-high pb-3">
              {posts.data.map((post) => (
                <button
                  key={post.id}
                  type="button"
                  onClick={() => setSelected(post.code)}
                  className={`label-code whitespace-nowrap rounded-lg px-4 py-2 transition ${
                    current?.code === post.code
                      ? 'border-b-2 border-brand bg-surface text-brand-soft'
                      : 'bg-surface-low text-content-muted hover:text-content'
                  }`}
                >
                  {post.code} // {post.brandPillar ?? post.title}
                </button>
              ))}
            </div>

            {current && (
              <PostInspector
                key={current.id}
                code={current.code}
                onDeleted={() => {
                  setSelected(null);
                  posts.reload();
                }}
              />
            )}
          </>
        )}
      </div>
    </>
  );
}

const STATUS_FLOW: { value: PostStatus; label: string; hint: string }[] = [
  { value: 'RASCUNHO', label: 'Rascunho', hint: 'Ainda em produção' },
  { value: 'AGENDADO', label: 'Agendado', hint: 'Data definida' },
  { value: 'PRONTO_PARA_POSTAR', label: 'Pronto para postar', hint: 'Arte e legenda aprovadas' },
  { value: 'PUBLICADO', label: 'Publicado', hint: 'Já está no ar' },
];

function PostInspector({ code, onDeleted }: { code: string; onDeleted: () => void }) {
  const { session } = useAuth();
  // Um rascunho só existe para quem está logado: entrar ou sair refaz a busca.
  const { data: post, loading, error, reload } = useApi(() => postsApi.byCode(code), [code, Boolean(session)]);
  const [copied, setCopied] = useState(false);
  const [statusBusy, setStatusBusy] = useState(false);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [editando, setEditando] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);

  /** Remove o post do planner; a arte continua no catálogo de assets. */
  async function deletePost() {
    if (!post || deleting) return;
    setDeleting(true);
    setStatusError(null);
    try {
      await postsApi.remove(post.id);
      onDeleted();
    } catch (err) {
      setStatusError(describeError(err));
      setDeleting(false);
      setConfirmingDelete(false);
    }
  }

  async function changeStatus(next: PostStatus) {
    if (!post || statusBusy) return;
    setStatusBusy(true);
    setStatusError(null);
    try {
      await postsApi.changeStatus(post.id, next);
      reload();
    } catch (err) {
      setStatusError(describeError(err));
    } finally {
      setStatusBusy(false);
    }
  }

  // Só troca a prancha por spinner na primeira carga: num reload depois de salvar,
  // desmontar o painel apagaria o retorno da ação.
  if (loading && !post) return <LoadingState label="Renderizando prancha…" />;
  if (error && !post) return <ErrorState message={error} onRetry={reload} />;
  if (!post) return null;

  const caption = [post.caption, post.hashtags].filter(Boolean).join('\n\n') || post.summary || '';

  return (
    <div className="grid grid-cols-1 gap-8 lg:grid-cols-12">
      <div className="flex flex-col gap-3 lg:col-span-7">
        {/* Com arte final do editor, mostramos a imagem como ela vai ao ar. */}
        {post.coverImageUrl ? (
          <>
            <img
              src={assetUrl(post.coverImageUrl)}
              alt={`Arte do post ${post.code}`}
              className="mx-auto w-full max-w-[500px] rounded-xl border border-outline-variant/30 shadow-2xl"
            />
            <a
              href={assetUrl(post.coverImageUrl)}
              download={`jhcomp-post-${post.code}.png`}
              className="btn-ghost mx-auto"
            >
              <Download size={12} />
              Baixar a arte
            </a>
          </>
        ) : (
        <div
          className="relative mx-auto flex w-full max-w-[500px] flex-col justify-between overflow-hidden rounded-xl border border-outline-variant/30 bg-surface-lowest p-8"
          style={{ aspectRatio: '4/5' }}
        >
          <div className="relative flex items-center justify-between">
            <span className="label-code text-brand-soft">// {post.brandPillar ?? 'JH.COMP'}</span>
            <span className="label-code rounded bg-surface-high px-2 py-0.5">REF: JH-{post.code}</span>
          </div>
          <div className="relative my-auto flex flex-col gap-3">
            <h2 className="font-display text-3xl uppercase leading-tight">{post.title}</h2>
            <div className="h-0.5 w-16 bg-gradient-to-r from-brand to-transparent" />
            <p className="text-sm text-content-muted">{post.summary}</p>
          </div>
          <div className="relative rounded bg-brand px-4 py-3 text-center font-mono text-[12px] uppercase tracking-wider text-brand-ink">
            {post.ctaLabel ?? 'Falar no WhatsApp'}
          </div>
        </div>
        )}

        {!post.coverImageUrl && (
          <p className="mx-auto max-w-[500px] text-center text-xs text-content-muted">
            Este post ainda não tem arte. Monte uma no <Link to="/studio" className="text-brand-soft underline">Editor de Posts</Link> e
            clique em “Salvar no planner”.
          </p>
        )}
      </div>

      <aside className="panel flex flex-col gap-5 p-6 lg:col-span-5">
        <div className="flex items-center justify-between border-b border-surface-high pb-3">
          <span className="label-code text-brand-soft">METADADOS // POST {post.code}</span>
          <StatusTag status={post.status} />
        </div>


        <div className="flex flex-col gap-2">
          <span className="label-code text-content-muted">Situação da produção</span>
          <div className="grid grid-cols-2 gap-2">
            {STATUS_FLOW.map((option) => (
              <button
                key={option.value}
                type="button"
                title={option.hint}
                disabled={!session || statusBusy || post.status === option.value}
                onClick={() => changeStatus(option.value)}
                className={`label-code rounded px-3 py-2 text-left transition disabled:opacity-60 ${
                  post.status === option.value ? 'bg-brand text-brand-ink' : 'bg-surface-high text-content-muted hover:text-content'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
          {!session && (
            <span className="text-xs text-content-muted">Entre com sua conta para mudar a situação.</span>
          )}
          {statusError && <span className="text-xs text-brand-soft">{statusError}</span>}

          {/* Os textos ficam editáveis depois de criados: o rascunho da IA quase nunca sai pronto. */}
          {session && !editando && (
            <button type="button" className="btn-ghost" onClick={() => setEditando(true)}>
              <Pencil size={12} />
              Editar textos da publicação
            </button>
          )}

          {session && editando && (
            <PublicacaoEditor
              post={post}
              onCancel={() => setEditando(false)}
              onSaved={() => {
                setEditando(false);
                reload();
              }}
            />
          )}

          {session && <PublishControl post={post} onPublished={reload} />}

          {session && (
            confirmingDelete ? (
              <div className="flex flex-col gap-2 rounded border border-outline-variant/50 bg-surface-lowest p-3">
                <span className="text-xs text-content-muted">
                  Apagar o post {post.code} do planner? A ação não pode ser desfeita (as artes continuam no catálogo).
                </span>
                <div className="flex items-center gap-2">
                  <button type="button" className="btn-primary" onClick={deletePost} disabled={deleting}>
                    <Trash2 size={12} />
                    {deleting ? 'Apagando…' : 'Apagar post'}
                  </button>
                  <button type="button" className="btn-ghost" onClick={() => setConfirmingDelete(false)} disabled={deleting}>
                    Cancelar
                  </button>
                </div>
              </div>
            ) : (
              <button type="button" className="btn-ghost self-start" onClick={() => setConfirmingDelete(true)}>
                <Trash2 size={12} />
                Apagar post
              </button>
            )
          )}
        </div>

        <dl className="grid grid-cols-2 gap-3">
          <Meta label="Formato" value={`${post.format} (${post.slideCount} slide${post.slideCount > 1 ? 's' : ''})`} />
          <Meta label="Público" value={AUDIENCE_LABEL[post.audience]} />
          <Meta label="Alcance est." value={post.estimatedReach?.toLocaleString('pt-BR') ?? '—'} />
          <Meta label="Salvos est." value={post.estimatedSaves?.toLocaleString('pt-BR') ?? '—'} />
        </dl>

        <div className="flex flex-col gap-2 rounded border border-outline-variant/30 bg-surface-lowest p-4">
          <div className="flex items-center justify-between">
            <span className="label-code text-brand-soft">Copy para legenda</span>
            <button
              type="button"
              className="btn-ghost"
              onClick={async () => {
                await navigator.clipboard.writeText(caption);
                setCopied(true);
                setTimeout(() => setCopied(false), 2000);
              }}
            >
              <Copy size={12} />
              {copied ? 'Copiado!' : 'Copiar'}
            </button>
          </div>
          <p className="whitespace-pre-line text-xs text-content-muted">
            {caption || 'Legenda ainda não cadastrada para este post.'}
          </p>
        </div>
      </aside>
    </div>
  );
}


/** Publica o post no Instagram, com os avisos do que falta quando não dá. */
function PublishControl({ post, onPublished }: { post: Post; onPublished: () => void }) {
  const status = useApi(() => instagramApi.status(), []);
  const limites = useApi(() => instagramApi.check(post.id), [post.id]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [progress, setProgress] = useState<PublishStatus | null>(null);

  // A publicação roda no backend; se a página for aberta no meio dela, o próprio post já vem
  // marcado como PUBLICANDO e o acompanhamento recomeça daqui.
  const publishing = progress ? progress.publishing : post.publishState === 'PUBLICANDO';
  const publishError = progress ? progress.error : post.publishError;

  useEffect(() => {
    if (!publishing) return;

    let alive = true;
    const timer = window.setInterval(async () => {
      try {
        const next = await instagramApi.publishStatus(post.id);
        if (!alive) return;
        setProgress(next);
        if (!next.publishing) {
          window.clearInterval(timer);
          // Sucesso: recarrega o post para mostrar o id da mídia e a data de publicação.
          if (next.published) onPublished();
        }
      } catch (err) {
        if (alive) setError(describeError(err));
      }
    }, 2500);

    return () => {
      alive = false;
      window.clearInterval(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [publishing, post.id]);

  if (!status.data) return null;

  if (post.instagramMediaId) {
    return (
      <div className="flex items-center gap-2 rounded border border-outline-variant/40 bg-surface-lowest p-3">
        <Instagram size={14} className="text-brand" />
        <span className="text-xs text-content-muted">
          Publicado no Instagram
          {post.publishedAt
            ? ` em ${new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(post.publishedAt))}`
            : ''}
          .
        </span>
      </div>
    );
  }

  const ready = status.data.connected && status.data.publicUrlReady;
  const check = limites.data;

  /** O POST só agenda (202): o progresso passa a vir do endpoint de status. */
  async function publish() {
    setBusy(true);
    setError(null);
    try {
      setProgress(await instagramApi.publish(post.id));
      setConfirming(false);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-2 rounded border border-outline-variant/40 bg-surface-lowest p-3">
      {publishing && (
        <div className="flex items-center gap-2 rounded bg-surface-high p-3">
          <Loader2 size={14} className="animate-spin text-brand" />
          <span className="text-xs text-content-muted">
            Publicando… a Meta baixa cada arte, o que leva alguns segundos num carrossel. Esta tela se atualiza sozinha.
          </span>
        </div>
      )}

      {!publishing && publishError && (
        <p className="rounded bg-surface-high p-3 text-xs text-brand-soft">
          A última tentativa falhou: {publishError}
        </p>
      )}

      {check && (
        <div className="flex flex-wrap items-center gap-2">
          <Limite rotulo="Legenda" valor={check.captionLength} limite={check.maxCaption} />
          <Limite rotulo="Hashtags" valor={check.hashtags} limite={check.maxHashtags} />
          <Limite rotulo="Imagens" valor={check.images} limite={10} />
        </div>
      )}

      {check && !check.ok && (
        <ul className="flex flex-col gap-1">
          {check.problems.map((p) => (
            <li key={p} className="text-xs text-brand-soft">• {p}</li>
          ))}
        </ul>
      )}

      {publishing ? null : ready ? (
        confirming ? (
          <>
            <span className="text-xs text-content-muted">
              Publicar agora no @{status.data.username}? O post vai ao ar imediatamente.
            </span>
            <div className="flex items-center gap-2">
              <button type="button" className="btn-primary" onClick={publish} disabled={busy}>
                <Instagram size={12} />
                {busy ? 'Publicando…' : 'Publicar agora'}
              </button>
              <button type="button" className="btn-ghost" onClick={() => setConfirming(false)} disabled={busy}>
                Cancelar
              </button>
            </div>
          </>
        ) : (
          <button
            type="button"
            className="btn-primary self-start"
            onClick={() => setConfirming(true)}
            disabled={Boolean(check && !check.ok)}
            title={check && !check.ok ? 'Resolva os avisos acima antes de publicar' : undefined}
          >
            <Instagram size={12} />
            Publicar no Instagram
          </button>
        )
      ) : (
        <span className="text-xs text-content-muted">
          {status.data.connected
            ? 'Falta uma URL pública do backend para a Meta baixar as artes.'
            : 'Nenhuma conta conectada.'}{' '}
          <Link to="/instagram" className="text-brand-soft underline">
            Abrir conexão com o Instagram
          </Link>
        </span>
      )}

      {error && <span className="text-xs text-brand-soft">{error}</span>}
    </div>
  );
}

/** Contador de limite do Instagram: fica vermelho quando estoura. */
function Limite({ rotulo, valor, limite }: { rotulo: string; valor: number; limite: number }) {
  const estourou = valor > limite;
  return (
    <span
      className={`label-code rounded px-2 py-1 ${estourou ? 'bg-brand text-brand-ink' : 'bg-surface-high text-content-muted'}`}
      title={estourou ? `Acima do limite de ${limite}` : `Limite: ${limite}`}
    >
      {rotulo} {valor}/{limite}
    </span>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded bg-surface p-3">
      <dt className="label-code text-content-muted">{label}</dt>
      <dd className="mt-1 font-mono text-xs text-content">{value}</dd>
    </div>
  );
}
