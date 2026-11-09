import { useState } from 'react';
import { Check, X } from 'lucide-react';
import { postsApi } from '../api/services';
import { describeError } from '../api/client';
import { Button, Field, TextArea } from './ui';
import type { Post } from '../types/api';

/** Teto de legenda do Instagram; o contador avisa antes de a Meta recusar. */
const LIMITE_LEGENDA = 2200;

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
      <Field label="Título" value={title} onChange={setTitle} />
      <Field label="Resumo" value={summary} onChange={setSummary} />

      <TextArea
        label="Legenda"
        value={caption}
        onChange={setCaption}
        rows={10}
        limite={LIMITE_LEGENDA}
        placeholder="A legenda que vai junto com a arte na publicação."
        hint="caracteres do Instagram"
      />

      <TextArea
        label="Hashtags"
        value={hashtags}
        onChange={setHashtags}
        rows={2}
        mono
        placeholder="#JHComp #AssistenciaTecnica"
      />

      <Field label="Chamada para ação" value={ctaLabel} onChange={setCtaLabel} />

      {erro && <p className="text-sm text-brand-soft">{erro}</p>}

      <div className="flex items-center gap-2">
        <Button variant="primary" onClick={salvar} busy={salvando} disabled={!mudou} icon={<Check size={12} />}>
          {salvando ? 'Salvando…' : 'Salvar alterações'}
        </Button>
        <Button onClick={onCancel} disabled={salvando} icon={<X size={12} />}>
          Cancelar
        </Button>
        {mudou && !salvando && <span className="label-code text-content-muted">alterações não salvas</span>}
      </div>
    </div>
  );
}
