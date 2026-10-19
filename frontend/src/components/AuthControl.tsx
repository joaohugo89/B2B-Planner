import { useState } from 'react';
import { createPortal } from 'react-dom';
import { LogIn, LogOut, Settings2, X } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { UserPanel } from './UserPanel';

/** Botão de sessão no header: abre o formulário de login ou encerra a sessão. */
export function AuthControl() {
  const { session, signOut } = useAuth();
  const [open, setOpen] = useState(false);
  const [panelOpen, setPanelOpen] = useState(false);

  if (session) {
    return (
      <>
        {/* O nome abre o painel lateral com sessão, conexões e chaves. */}
        <button
          type="button"
          className="flex items-center gap-2 rounded px-2 py-1 transition hover:bg-surface-high"
          onClick={() => setPanelOpen(true)}
          title="Abrir painel da conta"
        >
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-brand font-mono text-[11px] text-brand-ink">
            {session.name.slice(0, 2).toUpperCase()}
          </span>
          <span className="hidden flex-col items-start leading-none sm:flex">
            <span className="text-sm">{session.name}</span>
            <span className="label-code text-content-muted">{session.role}</span>
          </span>
          <Settings2 size={12} className="text-content-muted" />
        </button>

        <button type="button" className="btn-ghost" onClick={signOut}>
          <LogOut size={12} />
          Sair
        </button>

        {panelOpen && <UserPanel onClose={() => setPanelOpen(false)} />}
      </>
    );
  }

  return (
    <>
      <button type="button" className="btn-ghost" onClick={() => setPanelOpen(true)} title="Painel da conta">
        <Settings2 size={12} />
      </button>
      <button type="button" className="btn-ghost" onClick={() => setOpen(true)}>
        <LogIn size={12} />
        Entrar
      </button>
      {panelOpen && <UserPanel onClose={() => setPanelOpen(false)} />}
      {open && createPortal(<LoginDialog onClose={() => setOpen(false)} />, document.body)}
    </>
  );
}

function LoginDialog({ onClose }: { onClose: () => void }) {
  const { signIn } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await signIn(email, password);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível entrar.');
    } finally {
      setBusy(false);
    }
  }

  // Precisa ir ao <body> via portal: o header usa backdrop-blur, que cria bloco
  // contentor para descendentes `fixed` e prenderia o overlay dentro dele.
  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/70 p-4">
      <form
        onSubmit={submit}
        className="panel flex w-full max-w-sm flex-col gap-4 bg-surface-lowest p-6"
      >
        <div className="flex items-start justify-between">
          <div className="flex flex-col gap-1">
            <span className="label-code text-brand-soft">ACESSO RESTRITO</span>
            <h2 className="font-display text-xl">Entrar no planner</h2>
          </div>
          <button type="button" onClick={onClose} aria-label="Fechar" className="text-content-muted hover:text-content">
            <X size={18} />
          </button>
        </div>

        <label className="flex flex-col gap-1">
          <span className="label-code text-content-muted">E-mail</span>
          <input
            type="email"
            required
            autoFocus
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="rounded border border-outline-variant/40 bg-surface px-3 py-2 text-sm outline-none focus:border-brand"
          />
        </label>

        <label className="flex flex-col gap-1">
          <span className="label-code text-content-muted">Senha</span>
          <input
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="rounded border border-outline-variant/40 bg-surface px-3 py-2 text-sm outline-none focus:border-brand"
          />
        </label>

        {error && <p className="text-sm text-brand-soft">{error}</p>}

        <button type="submit" className="btn-primary justify-center" disabled={busy}>
          <LogIn size={14} />
          {busy ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </div>
  );
}
