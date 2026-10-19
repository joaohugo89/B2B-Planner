import { useState } from 'react';
import { Copy, Layers, Image as ImageIcon, CalendarDays, Instagram } from 'lucide-react';
import { postsApi } from '../api/services';
import { useApi } from '../api/useApi';
import { EmptyState, ErrorState, LoadingState, MetricCard, PageHeader, StatusTag } from '../components/primitives';
import { InstagramPreview } from '../components/InstagramPreview';
import type { Audience, Post } from '../types/api';
import { assetUrl } from '../api/assetUrl';

const AUDIENCE_LABEL: Record<Audience, string> = {
  B2B: 'B2B',
  B2C: 'B2C',
  HIBRIDO: 'Híbrido',
};

function plural(count: number, singular: string, many: string): string {
  return `${count} ${count === 1 ? singular : many}`;
}

function formatSchedule(value: string | null): string {
  if (!value) return 'Sem data definida';
  return new Intl.DateTimeFormat('pt-BR', {
    weekday: 'short',
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

function PostCard({ post, ratio }: { post: Post; ratio: '4/5' | '1/1' }) {
  const [copied, setCopied] = useState(false);
  const [previewing, setPreviewing] = useState(false);

  async function copyCaption() {
    const text = [post.caption, post.hashtags].filter(Boolean).join('\n\n') || post.summary || post.title;
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  return (
    <article className="group flex flex-col overflow-hidden rounded-xl bg-surface-lowest shadow-lg transition-transform hover:-translate-y-1">
      <div
        className="relative w-full overflow-hidden bg-surface-highest"
        style={{ aspectRatio: ratio }}
      >
        {post.coverImageUrl ? (
          <img src={assetUrl(post.coverImageUrl)} alt={post.title} className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full w-full items-center justify-center bg-[radial-gradient(circle_at_30%_20%,rgba(255,83,84,0.25),transparent_60%)]">
            <span className="font-display text-5xl text-brand-soft/30">JH</span>
          </div>
        )}

        <div className="absolute left-3 top-3">
          <StatusTag status={post.status} />
        </div>
        <div className="absolute right-3 top-3 flex items-center gap-1 rounded bg-surface-lowest/80 px-2 py-1 backdrop-blur">
          {post.format === 'CARROSSEL' ? <Layers size={12} className="text-brand" /> : <ImageIcon size={12} className="text-brand" />}
          <span className="label-code">
            {post.format === 'CARROSSEL' ? `1/${post.slideCount}` : 'ESTÁTICO'}
          </span>
        </div>

        {/* Com arte final, o card mostra a imagem como ela vai ao ar — sem texto por cima. */}
        {post.coverImageUrl ? (
          <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-surface-lowest/90 to-transparent px-3 pb-2 pt-8">
            <span className="label-code text-brand-soft">// POST {post.code}</span>
          </div>
        ) : (
          <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-surface-lowest via-surface-lowest/70 to-transparent p-4">
            <span className="label-code text-brand-soft">// POST {post.code} • {post.brandPillar ?? 'JH.COMP'}</span>
            <p className="mt-1 font-display text-lg leading-tight">{post.title}</p>
          </div>
        )}
      </div>

      <div className="flex flex-col gap-3 bg-surface p-4">
        <div className="flex items-center justify-between">
          <span className="label-code flex items-center gap-1 text-content-muted">
            <CalendarDays size={12} className="text-brand" />
            {formatSchedule(post.scheduledAt)}
          </span>
          {post.estimatedSaves != null && (
            <span className="label-code rounded bg-surface-high px-2 py-0.5 text-content-variant">
              EST. {post.estimatedSaves} SALVOS
            </span>
          )}
        </div>

        <p className="line-clamp-2 text-sm text-content-muted">{post.summary}</p>

        <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
          <button type="button" className="btn-ghost" onClick={copyCaption}>
            <Copy size={12} />
            {copied ? 'Copiado!' : 'Copiar legenda'}
          </button>
          <button type="button" className="btn-ghost" onClick={() => setPreviewing(true)}>
            <Instagram size={12} />
            Ver no Instagram
          </button>
          <span className="label-code text-content-muted">{AUDIENCE_LABEL[post.audience]}</span>
        </div>
      </div>

      {previewing && <InstagramPreview post={post} onClose={() => setPreviewing(false)} />}
    </article>
  );
}

export function FeedGridPage() {
  const [ratio, setRatio] = useState<'4/5' | '1/1'>('4/5');
  const posts = useApi(() => postsApi.list(), []);
  const metrics = useApi(() => postsApi.metrics(), []);

  return (
    <>
      <PageHeader
        eyebrow="FEED STRATEGY // BATCH 01"
        title="Instagram Planner & Grid Matrix"
        description="Planejamento arquitetônico de autoridade técnica e conversão B2B/B2C com cadência 4:5 e 1:1."
        actions={
          <div className="flex items-center gap-1 rounded-lg bg-surface-low p-1">
            {(['4/5', '1/1'] as const).map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => setRatio(option)}
                className={`label-code rounded px-3 py-1.5 transition ${
                  ratio === option ? 'bg-brand text-brand-ink' : 'text-content-muted hover:text-content'
                }`}
              >
                GRID {option.replace('/', ':')}
              </button>
            ))}
          </div>
        }
      />

      <div className="mx-auto flex max-w-7xl flex-col gap-8 px-4 py-10 md:px-8">
        {metrics.data && (
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <MetricCard
              label="Distribuição"
              value={`${metrics.data.b2b} B2B / ${metrics.data.b2c} B2C`}
              hint={`${metrics.data.total} posts no ciclo`}
            />
            <MetricCard
              label="Formatos"
              value={`${plural(metrics.data.carousels, 'carrossel', 'carrosséis')} / ${plural(
                metrics.data.statics,
                'estático',
                'estáticos',
              )}`}
            />
            <MetricCard
              label="Estado de produção"
              value={`${metrics.data.readyToPost}/${metrics.data.total} aprovados`}
              hint={`${metrics.data.scheduled} agendados`}
            />
            <MetricCard
              label="Alcance estimado"
              value={metrics.data.estimatedReach.toLocaleString('pt-BR')}
              hint={`${metrics.data.estimatedSaves.toLocaleString('pt-BR')} salvos previstos`}
            />
          </div>
        )}

        {posts.loading && !posts.data && <LoadingState />}
        {posts.error && <ErrorState message={posts.error} onRetry={posts.reload} />}
        {posts.data?.length === 0 && <EmptyState message="Nenhum post cadastrado neste ciclo." />}

        {posts.data && posts.data.length > 0 && (
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {posts.data.map((post) => (
              <PostCard key={post.id} post={post} ratio={ratio} />
            ))}
          </div>
        )}
      </div>
    </>
  );
}
