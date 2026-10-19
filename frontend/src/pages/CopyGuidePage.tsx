import { useState } from 'react';
import { Copy, Hash, Pencil, Plus, Send, Settings2, Trash2 } from 'lucide-react';
import {
  copiesApi,
  hashtagBlocksApi,
  postsApi,
  settingsApi,
  tonesApi,
  type HashtagBlock,
  type Tone,
} from '../api/services';
import { describeError } from '../api/client';
import { useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/primitives';
import { ToneManager } from '../components/ToneManager';
import type { CopyEntry, Post } from '../types/api';

const EMPTY_FORM = {
  id: null as number | null,
  postCode: '',
  headline: '',
  tone: '',
  body: '',
  hashtags: '',
  cta: '',
  strategy: '',
  bestTime: '',
};

type FormState = typeof EMPTY_FORM;

/** Junta as hashtags da legenda com as fixas da marca, sem repetir. */
export function mergeHashtags(entryTags: string | null, globalTags: string): string {
  const words = `${entryTags ?? ''} ${globalTags ?? ''}`.split(/\s+/).filter(Boolean);
  const seen = new Set<string>();
  return words
    .filter((tag) => {
      const key = tag.toLowerCase();
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .join(' ');
}

export function CopyGuidePage() {
  const [tone, setTone] = useState<string>('ALL');
  const copies = useApi(() => copiesApi.list(tone === 'ALL' ? undefined : { tone }), [tone]);
  const posts = useApi(() => postsApi.list(), []);
  const tones = useApi(() => tonesApi.list(), []);
  const hashtagsQuery = useApi(() => settingsApi.getHashtags(), []);
  const blocks = useApi(() => hashtagBlocksApi.list(), []);
  const { session } = useAuth();

  const [form, setForm] = useState<FormState | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [showSettings, setShowSettings] = useState(false);
  const [globalHashtags, setGlobalHashtags] = useState<string | null>(null);

  const tags = globalHashtags ?? hashtagsQuery.data ?? '';
  const toneList: Tone[] = tones.data ?? [];

  function startNew() {
    setFeedback(null);
    setForm({ ...EMPTY_FORM, tone: tone === 'ALL' ? toneList[0]?.code ?? '' : tone });
  }

  function startEdit(entry: CopyEntry) {
    setFeedback(null);
    setForm({
      id: entry.id,
      postCode: entry.postCode ?? '',
      headline: entry.headline,
      tone: entry.tone,
      body: entry.body,
      hashtags: entry.hashtags ?? '',
      cta: entry.cta ?? '',
      strategy: entry.strategy ?? '',
      bestTime: entry.bestTime ?? '',
    });
  }

  async function remove(entry: CopyEntry) {
    try {
      await copiesApi.remove(entry.id);
      setFeedback(`Legenda "${entry.headline}" removida.`);
      copies.reload();
      tones.reload();
    } catch (err) {
      setFeedback(describeError(err));
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="BIBLIOTECA DE LEGENDAS"
        title="Guia de Copy"
        description="Aqui ficam as legendas prontas da marca: escreva uma vez, reaproveite e aplique no post do planner com um clique. Cada legenda tem tom, CTA, hashtags e o melhor horário de publicação."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <button type="button" className="btn-ghost" onClick={() => setShowSettings((value) => !value)}>
              <Settings2 size={12} />
              {showSettings ? 'Fechar ajustes' : 'Tons e hashtags'}
            </button>
            {session && (
              <button type="button" className="btn-primary" onClick={startNew}>
                <Plus size={14} />
                Nova legenda
              </button>
            )}
          </div>
        }
      />

      <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-10 md:px-8">
        <section className="panel grid grid-cols-1 gap-4 p-4 md:grid-cols-3">
          <Step n="1" title="Escreva a legenda">
            Texto completo em português, com quebras de linha, CTA e hashtags separadas.
          </Step>
          <Step n="2" title="Classifique o tom">
            Os tons são cadastrados por você em “Tons e hashtags” — é o filtro que mantém o feed equilibrado.
          </Step>
          <Step n="3" title="Aplique no post">
            “Usar no post” grava a legenda, as hashtags da legenda e as fixas da marca no post escolhido.
          </Step>
        </section>

        {showSettings && (
          <ToneManager
            tones={toneList}
            globalHashtags={tags}
            blocks={blocks.data ?? []}
            onTonesChanged={() => {
              tones.reload();
              copies.reload();
            }}
            onBlocksChanged={blocks.reload}
            onHashtagsChanged={setGlobalHashtags}
          />
        )}

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => setTone('ALL')}
            className={`label-code rounded px-3 py-1.5 transition ${
              tone === 'ALL' ? 'bg-brand text-brand-ink' : 'bg-surface-low text-content-muted hover:text-content'
            }`}
          >
            Todas
          </button>
          {toneList.map((option) => (
            <button
              key={option.id}
              type="button"
              title={option.description ?? undefined}
              onClick={() => setTone(option.code)}
              className={`label-code rounded px-3 py-1.5 transition ${
                tone === option.code ? 'bg-brand text-brand-ink' : 'bg-surface-low text-content-muted hover:text-content'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>

        {tags && (
          <p className="label-code text-content-muted">
            Hashtags fixas em todo post: <span className="text-content-variant">{tags}</span>
          </p>
        )}

        {feedback && <p className="panel p-3 text-sm text-content-muted">{feedback}</p>}

        {form && session && (
          <CopyForm
            form={form}
            posts={posts.data ?? []}
            tones={toneList}
            blocks={blocks.data ?? []}
            onCancel={() => setForm(null)}
            onSaved={(message) => {
              setForm(null);
              setFeedback(message);
              copies.reload();
              tones.reload();
            }}
          />
        )}

        {copies.loading && !copies.data && <LoadingState />}
        {copies.error && <ErrorState message={copies.error} onRetry={copies.reload} />}
        {copies.data?.length === 0 && (
          <EmptyState message="Nenhuma legenda nesta biblioteca ainda. Crie a primeira ou gere uma pelo Assistente IA." />
        )}

        {copies.data?.map((entry) => (
          <CopyCard
            key={entry.id}
            entry={entry}
            posts={posts.data ?? []}
            tones={toneList}
            globalHashtags={tags}
            canEdit={Boolean(session)}
            onEdit={() => startEdit(entry)}
            onRemove={() => remove(entry)}
            onApplied={(message) => {
              setFeedback(message);
              posts.reload();
            }}
          />
        ))}
      </div>
    </>
  );
}

function Step({ n, title, children }: { n: string; title: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-3">
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded bg-brand font-mono text-xs text-brand-ink">
        {n}
      </span>
      <div className="flex flex-col gap-1">
        <span className="text-sm font-semibold">{title}</span>
        <span className="text-xs text-content-muted">{children}</span>
      </div>
    </div>
  );
}

function CopyCard({
  entry,
  posts,
  tones,
  globalHashtags,
  canEdit,
  onEdit,
  onRemove,
  onApplied,
}: {
  entry: CopyEntry;
  posts: Post[];
  tones: Tone[];
  globalHashtags: string;
  canEdit: boolean;
  onEdit: () => void;
  onRemove: () => void;
  onApplied: (message: string) => void;
}) {
  const [copied, setCopied] = useState(false);
  const [applying, setApplying] = useState(false);

  const toneLabel = tones.find((item) => item.code === entry.tone)?.label ?? entry.tone;
  const allTags = mergeHashtags(entry.hashtags, globalHashtags);

  /** Grava a legenda e as hashtags (da legenda + fixas) no post escolhido. */
  async function applyTo(postId: string) {
    const post = posts.find((item) => String(item.id) === postId);
    if (!post || applying) return;

    setApplying(true);
    try {
      await postsApi.update(post.id, {
        ...post,
        caption: entry.body,
        hashtags: allTags,
        ctaLabel: entry.cta ?? post.ctaLabel,
      });
      onApplied(`Legenda aplicada no post ${post.code}.`);
    } catch (err) {
      onApplied(describeError(err));
    } finally {
      setApplying(false);
    }
  }

  return (
    <article className="panel flex flex-col gap-4 p-6">
      <div className="flex flex-col justify-between gap-3 md:flex-row md:items-start">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded bg-brand font-display text-lg text-brand-ink">
            {entry.postCode || '--'}
          </div>
          <div>
            <span className="label-code text-brand-soft">{entry.strategy || toneLabel}</span>
            <h2 className="font-display text-xl">{entry.headline}</h2>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <span className="label-code rounded bg-surface-high px-2 py-1 text-content-muted">{toneLabel}</span>
          <span className="label-code rounded bg-surface-high px-2 py-1 text-content-muted">
            {entry.bestTime || 'Horário livre'}
          </span>
          <span className="label-code rounded bg-surface-high px-2 py-1 text-content-muted">
            {entry.charCount} caracteres
          </span>
          {canEdit && (
            <>
              <button type="button" className="btn-ghost" onClick={onEdit}>
                <Pencil size={12} />
                Editar
              </button>
              <button type="button" className="btn-ghost" onClick={onRemove}>
                <Trash2 size={12} />
              </button>
            </>
          )}
        </div>
      </div>

      <p className="whitespace-pre-line rounded bg-surface-lowest p-4 text-sm leading-relaxed">{entry.body}</p>

      {allTags && (
        <p className="rounded bg-surface-high p-3 font-mono text-[12px] text-content-variant">{allTags}</p>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="label-code text-content-muted">CTA: {entry.cta || 'Não definido'}</span>

        <div className="flex flex-wrap items-center gap-2">
          {canEdit && posts.length > 0 && (
            <select
              defaultValue=""
              disabled={applying}
              onChange={(event) => {
                void applyTo(event.target.value);
                event.target.value = '';
              }}
              className="rounded bg-surface-lowest px-3 py-2 font-mono text-[11px]"
            >
              <option value="">{applying ? 'Aplicando…' : 'Usar no post…'}</option>
              {posts.map((post) => (
                <option key={post.id} value={post.id}>
                  {post.code} — {post.title.slice(0, 34)}
                </option>
              ))}
            </select>
          )}

          <button
            type="button"
            className="btn-primary"
            onClick={async () => {
              await navigator.clipboard.writeText([entry.body, allTags].filter(Boolean).join('\n\n'));
              setCopied(true);
              setTimeout(() => setCopied(false), 2000);
            }}
          >
            <Copy size={14} />
            {copied ? 'Copiado!' : 'Copiar legenda & tags'}
          </button>
        </div>
      </div>
    </article>
  );
}

function CopyForm({
  form,
  posts,
  tones,
  blocks,
  onCancel,
  onSaved,
}: {
  form: FormState;
  posts: Post[];
  tones: Tone[];
  blocks: HashtagBlock[];
  onCancel: () => void;
  onSaved: (message: string) => void;
}) {
  const [state, setState] = useState<FormState>(form);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setState((current) => ({ ...current, [key]: value }));
  }

  async function submit() {
    if (!state.headline.trim() || !state.body.trim() || busy) return;
    setBusy(true);
    setError(null);
    try {
      const payload = {
        postCode: state.postCode || null,
        headline: state.headline,
        tone: state.tone || tones[0]?.code || 'AUTORIDADE',
        body: state.body,
        hashtags: state.hashtags || null,
        cta: state.cta || null,
        strategy: state.strategy || null,
        bestTime: state.bestTime || null,
      };
      if (state.id) {
        await copiesApi.update(state.id, payload);
        onSaved('Legenda atualizada.');
      } else {
        await copiesApi.create(payload);
        onSaved('Legenda criada.');
      }
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel flex flex-col gap-3 p-6">
      <div className="flex items-center justify-between">
        <span className="label-code text-brand-soft">{state.id ? 'Editar legenda' : 'Nova legenda'}</span>
        <button type="button" className="btn-ghost" onClick={onCancel}>
          Cancelar
        </button>
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
        <label className="flex flex-col gap-1 md:col-span-2">
          <span className="label-code text-content-muted">Título interno</span>
          <input
            value={state.headline}
            onChange={(event) => set('headline', event.target.value)}
            className="rounded bg-surface-lowest px-3 py-2 text-sm outline-none"
          />
        </label>

        <label className="flex flex-col gap-1">
          <span className="label-code text-content-muted">Tom</span>
          <select
            value={state.tone}
            onChange={(event) => set('tone', event.target.value)}
            className="rounded bg-surface-lowest px-3 py-2 text-sm"
          >
            {tones.map((option) => (
              <option key={option.id} value={option.code}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      <label className="flex flex-col gap-1">
        <span className="label-code text-content-muted">Legenda completa</span>
        <textarea
          rows={8}
          value={state.body}
          onChange={(event) => set('body', event.target.value)}
          className="rounded bg-surface-lowest px-3 py-2 text-sm leading-relaxed outline-none"
        />
      </label>

      <label className="flex flex-col gap-1">
        <span className="label-code text-content-muted">Hashtags desta legenda</span>
        <input
          value={state.hashtags}
          onChange={(event) => set('hashtags', event.target.value)}
          placeholder="#ManutencaoPC #SSDNvme …"
          className="rounded bg-surface-lowest px-3 py-2 font-mono text-xs outline-none"
        />
      </label>

      {blocks.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="label-code text-content-muted">Blocos:</span>
          {blocks.map((block) => (
            <button
              key={block.id}
              type="button"
              title={block.hashtags}
              onClick={() => set('hashtags', mergeHashtags(state.hashtags, block.hashtags))}
              className="label-code rounded bg-surface-high px-2 py-1 text-content-muted transition hover:text-content"
            >
              <Hash size={10} className="mr-1 inline" />
              {block.name} ({block.tagCount})
            </button>
          ))}
        </div>
      )}

      <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
        <label className="flex flex-col gap-1">
          <span className="label-code text-content-muted">CTA</span>
          <input
            value={state.cta}
            onChange={(event) => set('cta', event.target.value)}
            className="rounded bg-surface-lowest px-3 py-2 text-sm outline-none"
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="label-code text-content-muted">Estratégia</span>
          <input
            value={state.strategy}
            onChange={(event) => set('strategy', event.target.value)}
            className="rounded bg-surface-lowest px-3 py-2 text-sm outline-none"
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="label-code text-content-muted">Melhor horário</span>
          <input
            value={state.bestTime}
            onChange={(event) => set('bestTime', event.target.value)}
            placeholder="Terça 11h45"
            className="rounded bg-surface-lowest px-3 py-2 text-sm outline-none"
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="label-code text-content-muted">Post vinculado</span>
          <select
            value={state.postCode}
            onChange={(event) => set('postCode', event.target.value)}
            className="rounded bg-surface-lowest px-3 py-2 text-sm"
          >
            <option value="">Nenhum</option>
            {posts.map((post) => (
              <option key={post.id} value={post.code}>
                {post.code} — {post.title.slice(0, 28)}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && <p className="text-sm text-brand-soft">{error}</p>}

      <button
        type="button"
        className="btn-primary self-start"
        onClick={submit}
        disabled={busy || !state.headline.trim() || !state.body.trim()}
      >
        <Send size={14} />
        {busy ? 'Salvando…' : 'Salvar legenda'}
      </button>
    </section>
  );
}
