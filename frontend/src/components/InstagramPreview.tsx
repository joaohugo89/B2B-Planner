import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { Bookmark, ChevronLeft, ChevronRight, Heart, MessageCircle, Send, X } from 'lucide-react';
import type { Post } from '../types/api';
import { assetUrl } from '../api/assetUrl';

/** Mostra como o post aparece no feed do Instagram, com carrossel e legenda. */
export function InstagramPreview({ post, onClose }: { post: Post; onClose: () => void }) {
  const images = post.slides?.length ? post.slides : post.coverImageUrl ? [post.coverImageUrl] : [];
  const [index, setIndex] = useState(0);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
      if (event.key === 'ArrowRight') setIndex((i) => Math.min(i + 1, Math.max(0, images.length - 1)));
      if (event.key === 'ArrowLeft') setIndex((i) => Math.max(0, i - 1));
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose, images.length]);

  const caption = [post.caption, post.hashtags].filter(Boolean).join('\n\n');
  const schedule = post.scheduledAt
    ? new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'long' }).format(new Date(post.scheduledAt))
    : null;

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-sm"
      onClick={(event) => event.target === event.currentTarget && onClose()}
    >
      <div className="flex max-h-[92vh] w-full max-w-[420px] flex-col overflow-hidden rounded-xl border border-surface-high bg-surface-lowest">
        <header className="flex items-center justify-between border-b border-surface-high px-3 py-2">
          <div className="flex items-center gap-2">
            <div className="rounded-full bg-gradient-to-tr from-brand-deep via-brand to-brand-soft p-[2px]">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-surface-lowest font-display text-sm text-brand-soft">
                JH
              </div>
            </div>
            <div className="flex flex-col leading-tight">
              <span className="text-sm font-semibold">jh.comp_tecnologia</span>
              <span className="label-code text-content-muted">Prévia // Post {post.code}</span>
            </div>
          </div>
          <button type="button" className="btn-ghost px-2" onClick={onClose} aria-label="Fechar prévia">
            <X size={14} />
          </button>
        </header>

        <div className="relative bg-black" style={{ aspectRatio: '4/5' }}>
          {images.length > 0 ? (
            <img src={assetUrl(images[index])} alt={`Slide ${index + 1}`} className="h-full w-full object-cover" />
          ) : (
            <div className="flex h-full w-full items-center justify-center text-sm text-content-muted">
              Este post ainda não tem arte.
            </div>
          )}

          {images.length > 1 && (
            <>
              <span className="label-code absolute right-3 top-3 rounded-full bg-black/70 px-2 py-1 text-white">
                {index + 1}/{images.length}
              </span>

              {index > 0 && (
                <button
                  type="button"
                  onClick={() => setIndex((i) => i - 1)}
                  className="absolute left-2 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full bg-white/85 text-black"
                  aria-label="Slide anterior"
                >
                  <ChevronLeft size={16} />
                </button>
              )}
              {index < images.length - 1 && (
                <button
                  type="button"
                  onClick={() => setIndex((i) => i + 1)}
                  className="absolute right-2 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full bg-white/85 text-black"
                  aria-label="Próximo slide"
                >
                  <ChevronRight size={16} />
                </button>
              )}

              <div className="absolute inset-x-0 bottom-3 flex items-center justify-center gap-1.5">
                {images.map((_, i) => (
                  <span
                    key={i}
                    className={`h-1.5 w-1.5 rounded-full transition ${i === index ? 'bg-white' : 'bg-white/40'}`}
                  />
                ))}
              </div>
            </>
          )}
        </div>

        <div className="flex items-center justify-between px-3 py-2">
          <div className="flex items-center gap-4 text-content">
            <Heart size={20} />
            <MessageCircle size={20} />
            <Send size={20} />
          </div>
          <Bookmark size={20} className="text-content" />
        </div>

        <div className="flex-1 overflow-y-auto px-3 pb-4">
          <p className="text-sm leading-relaxed">
            <span className="font-semibold">jh.comp_tecnologia</span>{' '}
            {caption ? (
              <span className={expanded ? 'whitespace-pre-line' : 'line-clamp-2 whitespace-pre-line'}>{caption}</span>
            ) : (
              <span className="text-content-muted">
                Sem legenda ainda — aplique uma no Guia de Copy.
              </span>
            )}
          </p>

          {caption && !expanded && (
            <button type="button" className="mt-1 text-xs text-content-muted" onClick={() => setExpanded(true)}>
              … mais
            </button>
          )}

          <p className="label-code mt-3 text-content-muted">
            {schedule ? `Agendado para ${schedule}` : 'Sem data definida'}
            {post.format === 'CARROSSEL' ? ` • carrossel de ${images.length || post.slideCount}` : ' • post único'}
          </p>
        </div>
      </div>
    </div>,
    document.body,
  );
}
