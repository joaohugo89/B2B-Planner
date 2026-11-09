import { useState, type ReactNode } from 'react';
import { Check, Loader2, X } from 'lucide-react';

/**
 * Peças de interface que toda tela do planner usa.
 *
 * <p>Antes cada página repetia as mesmas classes do Tailwind à mão — 38 campos com
 * "rounded bg-surface px-3 py-2" copiado, a confirmação de exclusão reescrita em três
 * telas. Mudar o visual exigia caçar ocorrência por ocorrência, e tela nova começava do
 * zero.</p>
 *
 * <p>Aqui ficam só as primitivas. Componentes que conhecem o negócio — editor de
 * publicação, carrossel do post — moram em arquivos próprios e falam a língua do
 * domínio.</p>
 */

/** Rótulo + campo de texto, com dica opcional embaixo. */
export function Field({
  label,
  value,
  onChange,
  hint,
  placeholder,
  disabled,
  type = 'text',
}: {
  label: string;
  value: string;
  onChange: (valor: string) => void;
  hint?: ReactNode;
  placeholder?: string;
  disabled?: boolean;
  type?: 'text' | 'email' | 'password' | 'url' | 'number';
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="label-code text-content-muted">{label}</span>
      <input
        type={type}
        value={value}
        onChange={(evento) => onChange(evento.target.value)}
        placeholder={placeholder}
        disabled={disabled}
        className="rounded bg-surface px-3 py-2 text-sm outline-none transition focus:ring-1 focus:ring-brand/60 disabled:opacity-60"
      />
      {hint && <span className="text-xs text-content-muted">{hint}</span>}
    </label>
  );
}

/** Texto longo. `contador` mostra quanto já foi escrito do limite. */
export function TextArea({
  label,
  value,
  onChange,
  rows = 4,
  limite,
  hint,
  placeholder,
  disabled,
  mono,
}: {
  label: string;
  value: string;
  onChange: (valor: string) => void;
  rows?: number;
  limite?: number;
  hint?: ReactNode;
  placeholder?: string;
  disabled?: boolean;
  mono?: boolean;
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="label-code text-content-muted">{label}</span>
      <textarea
        value={value}
        onChange={(evento) => onChange(evento.target.value)}
        rows={rows}
        maxLength={limite}
        placeholder={placeholder}
        disabled={disabled}
        className={`rounded bg-surface p-3 outline-none transition focus:ring-1 focus:ring-brand/60 disabled:opacity-60 ${
          mono ? 'font-mono text-xs' : 'text-sm leading-relaxed'
        }`}
      />
      <div className="flex items-start justify-between gap-2">
        {hint ? <span className="text-xs text-content-muted">{hint}</span> : <span />}
        {limite && (
          <span className="label-code shrink-0 text-content-muted">
            {value.length}/{limite}
          </span>
        )}
      </div>
    </label>
  );
}

/** Lista de opções. */
export function Select<T extends string>({
  label,
  value,
  onChange,
  options,
  hint,
  disabled,
}: {
  label: string;
  value: T;
  onChange: (valor: T) => void;
  options: ReadonlyArray<{ value: T; label: string }>;
  hint?: ReactNode;
  disabled?: boolean;
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="label-code text-content-muted">{label}</span>
      <select
        value={value}
        onChange={(evento) => onChange(evento.target.value as T)}
        disabled={disabled}
        className="rounded bg-surface px-3 py-2 font-mono text-xs outline-none transition focus:ring-1 focus:ring-brand/60 disabled:opacity-60"
      >
        {options.map((opcao) => (
          <option key={opcao.value} value={opcao.value}>
            {opcao.label}
          </option>
        ))}
      </select>
      {hint && <span className="text-xs text-content-muted">{hint}</span>}
    </label>
  );
}

/**
 * Bloco de conteúdo com cabeçalho — o painel que se repete em toda tela.
 * `acoes` vão à direita do título.
 */
export function Section({
  icon,
  title,
  description,
  actions,
  children,
}: {
  icon?: ReactNode;
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="panel flex flex-col gap-3 p-4">
      <header className="flex flex-wrap items-center gap-2">
        {icon && <span className="text-brand">{icon}</span>}
        <span className="label-code text-brand-soft">{title}</span>
        {actions && <div className="ml-auto flex items-center gap-2">{actions}</div>}
      </header>

      {description && <p className="text-sm text-content-muted">{description}</p>}

      {children}
    </section>
  );
}

/** Botão com estado de espera: evita o clique duplo enquanto a chamada não volta. */
export function Button({
  children,
  onClick,
  variant = 'ghost',
  busy,
  disabled,
  icon,
  type = 'button',
  title,
}: {
  children: ReactNode;
  onClick?: () => void;
  variant?: 'primary' | 'ghost';
  busy?: boolean;
  disabled?: boolean;
  icon?: ReactNode;
  type?: 'button' | 'submit';
  title?: string;
}) {
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled || busy}
      title={title}
      className={variant === 'primary' ? 'btn-primary' : 'btn-ghost'}
    >
      {busy ? <Loader2 size={12} className="animate-spin" /> : icon}
      {children}
    </button>
  );
}

/**
 * Ação destrutiva com confirmação no lugar, sem janela modal.
 *
 * <p>O botão vira pergunta ao ser clicado. Modal para "tem certeza?" custa foco e uma
 * decisão a mais; aqui a resposta fica onde a pergunta nasceu.</p>
 */
export function ConfirmAction({
  label,
  question,
  confirmLabel = 'Apagar',
  onConfirm,
  busy,
  disabled,
  icon,
}: {
  label: string;
  question: ReactNode;
  confirmLabel?: string;
  onConfirm: () => void;
  busy?: boolean;
  disabled?: boolean;
  icon?: ReactNode;
}) {
  const [perguntando, setPerguntando] = useState(false);

  if (!perguntando) {
    return (
      <Button onClick={() => setPerguntando(true)} disabled={disabled} icon={icon}>
        {label}
      </Button>
    );
  }

  return (
    <div className="flex flex-col gap-2 rounded border border-outline-variant/50 bg-surface-lowest p-3">
      <span className="text-xs text-content-muted">{question}</span>
      <div className="flex items-center gap-2">
        <Button variant="primary" onClick={onConfirm} busy={busy} icon={<Check size={12} />}>
          {confirmLabel}
        </Button>
        <Button onClick={() => setPerguntando(false)} disabled={busy} icon={<X size={12} />}>
          Cancelar
        </Button>
      </div>
    </div>
  );
}
