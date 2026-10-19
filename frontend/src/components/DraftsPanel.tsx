import { useState } from 'react';
import { FilePenLine, Layers, Trash2 } from 'lucide-react';
import { postsApi } from '../api/services';
import { describeError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { EmptyState, LoadingState } from './primitives';
import type { Post } from '../types/api';
import { assetUrl } from '../api/assetUrl';

/** Posts ainda não finalizados (rascunho), para retomar ou descartar sem sair do editor. */
export function DraftsPanel({
  posts,
  loading,
  onOpen,
  onChanged,
}: {
  posts: Post[];
  loading: boolean;
  onOpen: (post: Post) => void;
  onChanged: () => void;
}) {
  const { session } = useAuth();
  const [confirming, setConfirming] = useState<number | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const drafts = posts.filter((post) => post.status === 'RASCUNHO');

  async function remove(post: Post) {
    setBusyId(post.id);
    setError(null);
    try {
      await postsApi.remove(post.id);
      setConfirming(null);
      onChanged();
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusyId(null);
    }
  }

  if (loading && !posts.length) return <LoadingState label="Carregando rascunhos…" />;

  if (!drafts.length) {
    return (
      <EmptyState message="Nenhum rascunho por aqui. Monte um post e use “Salvar no planner” para guardá-lo como rascunho." />
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {error && <p className="text-sm text-brand-soft">{error}</p>}

      {drafts.map((post) => (
        <article key={post.id} className="panel flex items-center gap-3 p-3">
          <div className="h-20 w-16 shrink-0 overflow-hidden rounded bg-surface-high">
            {post.coverImageUrl ? (
              <img src={assetUrl(post.coverImageUrl)} alt={`Rascunho ${post.code}`} className="h-full w-full object-cover" />
            ) : (
              <div className="flex h-full w-full items-center justify-center font-display text-brand-soft/40">JH</div>
            )}
          </div>

          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <span className="label-code text-brand-soft">
              {post.code} • {post.format === 'CARROSSEL' ? `carrossel de ${post.slides.length || post.slideCount}` : 'post único'}
            </span>
            <span className="truncate text-sm font-semibold">{post.title}</span>
            <span className="truncate text-xs text-content-muted">{post.summary}</span>
          </div>

          <div className="flex shrink-0 flex-col items-end gap-2">
            <button type="button" className="btn-ghost" onClick={() => onOpen(post)}>
              <FilePenLine size={12} />
              Abrir
            </button>

            {session && (
              confirming === post.id ? (
                <div className="flex items-center gap-1">
                  <button type="button" className="btn-primary px-2" onClick={() => remove(post)} disabled={busyId === post.id}>
                    {busyId === post.id ? '…' : 'Apagar'}
                  </button>
                  <button type="button" className="btn-ghost px-2" onClick={() => setConfirming(null)}>
                    Não
                  </button>
                </div>
              ) : (
                <button type="button" className="btn-ghost" onClick={() => setConfirming(post.id)}>
                  <Trash2 size={12} />
                  Apagar
                </button>
              )
            )}
          </div>
        </article>
      ))}

      <p className="label-code text-content-muted">
        <Layers size={12} className="mr-1 inline text-brand" />
        Abrir carrega os textos do rascunho no editor; salvar de novo atualiza o mesmo post.
      </p>
    </div>
  );
}
