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
      {/* py-6: numa ferramenta densa, 40px a mais de respiro no topo empurrava o trabalho
          para fora da primeira dobra em todas as telas. */}
      <div className="shell flex flex-col gap-4 py-6 md:flex-row md:items-end md:justify-between">
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
      <span className="font-display text-lg text-content sm:text-xl">{value}</span>
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

/**
 * Copia texto e diz se conseguiu. navigator.clipboard só existe em HTTPS ou
 * localhost: abrindo o planner pelo IP da rede ele é undefined e os botões falhavam calados. O
 * execCommand é obsoleto, mas é o único caminho que funciona em contexto inseguro.
 */
export async function copiarTexto(texto: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(texto);
      return true;
    }
  } catch {
    // Permissão negada ou contexto inseguro: cai no plano B.
  }

  try {
    const campo = document.createElement('textarea');
    campo.value = texto;
    // Fora da tela, mas precisa estar no documento e focável para o execCommand ver a seleção.
    campo.setAttribute('readonly', '');
    campo.style.cssText = 'position:fixed;top:-1000px;opacity:0';
    document.body.appendChild(campo);
    campo.select();
    const copiou = document.execCommand('copy');
    document.body.removeChild(campo);
    if (copiou) return true;
  } catch {
    // Sem saída automática: resta mostrar o texto para o usuário copiar na mão.
  }

  window.prompt('Não foi possível copiar sozinho. Copie o texto abaixo:', texto);
  return false;
}
