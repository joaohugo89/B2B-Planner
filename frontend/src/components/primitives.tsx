import type { ReactNode } from 'react';
import { AlertTriangle, Loader2 } from 'lucide-react';
import type { PostStatus } from '../types/api';

export function PageHeader({ eyebrow, title, description, actions }: {
  eyebrow: string;
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <section className="border-b border-surface-high bg-surface-lowest">
      <div className="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-10 md:flex-row md:items-end md:justify-between md:px-8">
        <div className="flex max-w-2xl flex-col gap-2">
          <div className="flex items-center gap-2">
            <span className="h-2.5 w-2.5 rounded-full bg-brand shadow-glow" />
            <span className="label-code text-brand-soft">{eyebrow}</span>
          </div>
          <h1 className="font-display text-3xl uppercase tracking-tight md:text-4xl">{title}</h1>
          {description && <p className="text-content-muted">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </section>
  );
}

export function MetricCard({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="panel flex flex-col gap-1 p-4">
      <span className="label-code text-content-muted">{label}</span>
      <span className="font-display text-xl text-content">{value}</span>
      {hint && <span className="label-code text-brand-soft">{hint}</span>}
    </div>
  );
}

const STATUS_LABEL: Record<PostStatus, string> = {
  RASCUNHO: 'Rascunho',
  AGENDADO: 'Agendado',
  PRONTO_PARA_POSTAR: 'Pronto para postar',
  PUBLICADO: 'Publicado',
};

const STATUS_CLASS: Record<PostStatus, string> = {
  RASCUNHO: 'bg-surface-high text-content-muted',
  AGENDADO: 'bg-surface-high text-content',
  PRONTO_PARA_POSTAR: 'bg-brand text-brand-ink',
  PUBLICADO: 'bg-brand-deep text-content',
};

export function StatusTag({ status }: { status: PostStatus }) {
  return (
    <span className={`label-code rounded px-2 py-0.5 ${STATUS_CLASS[status]}`}>
      {STATUS_LABEL[status]}
    </span>
  );
}

export function LoadingState({ label = 'Carregando dados do sistema…' }: { label?: string }) {
  return (
    <div className="flex items-center gap-3 p-8 text-content-muted">
      <Loader2 className="animate-spin text-brand" size={18} />
      <span className="label-code">{label}</span>
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="panel flex flex-col gap-3 border-outline-variant p-6">
      <div className="flex items-center gap-2 text-brand-soft">
        <AlertTriangle size={18} />
        <span className="label-code">Falha de comunicação</span>
      </div>
      <p className="text-sm text-content-muted">{message}</p>
      {onRetry && (
        <button type="button" className="btn-ghost self-start" onClick={onRetry}>
          Tentar novamente
        </button>
      )}
    </div>
  );
}

export function EmptyState({ message }: { message: string }) {
  return <div className="panel p-6 text-sm text-content-muted">{message}</div>;
}
