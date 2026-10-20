import { useState } from 'react';
import { Check, X } from 'lucide-react';
import { postsApi } from '../api/services';
import { describeError } from '../api/client';
import type { Post } from '../types/api';

/**
 * Edição dos textos de um post já criado: título, resumo, legenda, hashtags e CTA.
 *
 * <p>Vive num componente próprio porque o mesmo formulário é usado em dois lugares — nos
 * Posts Detalhados, ao lado da arte, e no Guia de Copy, onde as legendas de todos os posts
 * ficam reunidas. A legenda mora no próprio post: editar aqui muda o que será publicado,
 * sem cópia paralela para sair do lugar.</p>
 */
export function PublicacaoEditor({
  post,
  onSaved,
  onCancel,
}: {
  post: Post;
  onSaved: (atualizado: Post) => void;
  onCancel: () => void;
}) {
  const [title, setTitle] = useState(post.title);
  const [summary, setSummary] = useState(post.summary ?? '');
  const [caption, setCaption] = useState(post.caption ?? '');
  const [hashtags, setHashtags] = useState(post.hashtags ?? '');
  const [ctaLabel, setCtaLabel] = useState(post.ctaLabel ?? '');
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const mudou =
    title !== post.title ||
    summary !== (post.summary ?? '') ||
    caption !== (post.caption ?? '') ||
    hashtags !== (post.hashtags ?? '') ||
    ctaLabel !== (post.ctaLabel ?? '');

  async function salvar() {
    if (!title.trim() || salvando) return;
    setSalvando(true);
    setErro(null);
    try {
      // O backend exige o post inteiro no PUT; os campos intocados seguem como estão.
      const atualizado = await postsApi.update(post.id, {
        ...post,
        title: title.trim(),
        summary: summary.trim() || null,
        caption: caption.trim() || null,
        hashtags: hashtags.trim() || null,
        ctaLabel: ctaLabel.trim() || null,
      });
      onSaved(atualizado);
    } catch (err) {
      setErro(describeError(err));
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded border border-outline-variant/50 bg-surface-lowest p-3">
      <Campo label="Título" valor={title} aoMudar={setTitle} />
      <Campo label="Resumo" valor={summary} aoMudar={setSummary} />

      <label className="flex flex-col gap-1">
        <span className="label-code text-content-muted">Legenda</span>
        <textarea
          value={caption}
          onChange={(event) => setCaption(event.target.value)}
          rows={10}
          placeholder="A legenda que vai junto com a arte na publicação."
          className="rounded bg-surface p-3 text-sm leading-relaxed outline-none placeholder:text-content-muted/60"
        />
        <span className="label-code text-right text-content-muted">
          {caption.length}/2200 caracteres do Instagram
        </span>
      </label>

      <label className="flex flex-col gap-1">
        <span className="label-code text-content-muted">Hashtags</span>
        <textarea
          value={hashtags}
          onChange={(event) => setHashtags(event.target.value)}
          rows={2}
          placeholder="#JHComp #AssistenciaTecnica"
          className="rounded bg-surface p-3 font-mono text-xs outline-none placeholder:text-content-muted/60"
        />
      </label>

      <Campo label="Chamada para ação" valor={ctaLabel} aoMudar={setCtaLabel} />

      {erro && <p className="text-sm text-brand-soft">{erro}</p>}

      <div className="flex items-center gap-2">
        <button type="button" className="btn-primary" onClick={salvar} disabled={salvando || !mudou}>
          <Check size={12} />
          {salvando ? 'Salvando…' : 'Salvar alterações'}
        </button>
        <button type="button" className="btn-ghost" onClick={onCancel} disabled={salvando}>
          <X size={12} />
          Cancelar
        </button>
        {mudou && !salvando && (
          <span className="label-code text-content-muted">alterações não salvas</span>
        )}
      </div>
    </div>
  );
}

function Campo({ label, valor, aoMudar }: { label: string; valor: string; aoMudar: (v: string) => void }) {
  return (
    <label className="flex flex-col gap-1">
      <span className="label-code text-content-muted">{label}</span>
      <input
        value={valor}
        onChange={(event) => aoMudar(event.target.value)}
        className="rounded bg-surface px-3 py-2 text-sm outline-none"
      />
    </label>
  );
}
