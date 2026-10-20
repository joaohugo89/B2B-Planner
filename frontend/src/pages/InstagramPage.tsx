import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Activity, CheckCircle2, Instagram, Link2Off, Loader2, TriangleAlert } from 'lucide-react';
import { instagramApi, type InstagramStatus } from '../api/services';
import { describeError } from '../api/client';
import { useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { ErrorState, LoadingState, PageHeader } from '../components/primitives';

const STATE_KEY = 'jhcomp.igState';

export function InstagramPage() {
  const status = useApi(() => instagramApi.status(), []);
  const { session } = useAuth();
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  // Volta do Facebook com ?code=… — troca pelo token e grava a conexão.
  useEffect(() => {
    const code = params.get('code');
    const returnedState = params.get('state');
    const denied = params.get('error');

    if (denied) {
      setError('Autorização cancelada no Facebook.');
      setParams({}, { replace: true });
      return;
    }
    if (!code) return;

    const expected = sessionStorage.getItem(STATE_KEY);
    if (!returnedState || (expected && expected !== returnedState)) {
      setError('A resposta da Meta não bate com o pedido feito por este navegador. Tente conectar de novo.');
      setParams({}, { replace: true });
      return;
    }

    setBusy(true);
    instagramApi
      .connect(code, returnedState)
      .then((connected) => {
        setMessage(`Conta @${connected.username ?? 'instagram'} conectada.`);
        status.reload();
      })
      .catch((err) => setError(describeError(err)))
      .finally(() => {
        setBusy(false);
        sessionStorage.removeItem(STATE_KEY);
        setParams({}, { replace: true });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params]);

  async function startConnect() {
    setBusy(true);
    setError(null);
    try {
      const { url, state } = await instagramApi.authorizeUrl();
      sessionStorage.setItem(STATE_KEY, state);
      window.location.href = url;
    } catch (err) {
      setError(describeError(err));
      setBusy(false);
    }
  }

  /** Health check: pergunta à Meta se o token ainda vale. */
  async function testarToken() {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const resultado = await instagramApi.health();
      status.setData(resultado);
      setMessage(resultado.tokenValid ? 'Conexão ativa.' : 'A Meta não aceitou o token atual.');
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  async function disconnect() {
    setBusy(true);
    setError(null);
    try {
      await instagramApi.disconnect();
      setMessage('Conta desconectada.');
      status.reload();
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="PUBLICAÇÃO DIRETA"
        title="Conexão com o Instagram"
        description="Conecte a conta profissional da JH.COMP para publicar os posts aprovados sem sair do planner."
      />

      <div className="mx-auto flex max-w-3xl flex-col gap-6 px-4 py-10 md:px-8">
        {status.loading && !status.data && <LoadingState label="Verificando conexão…" />}
        {status.error && <ErrorState message={status.error} onRetry={status.reload} />}

        {status.data && (
          <>
            <Checklist status={status.data} />

            <section className="panel flex flex-col gap-4 p-5">
              {status.data.connected ? (
                <>
                  <div className="flex items-center gap-2 text-brand-soft">
                    <CheckCircle2 size={16} />
                    <span className="label-code">Conectado</span>
                  </div>
                  <div className="flex flex-col gap-1 text-sm">
                    <span className="font-semibold">@{status.data.username ?? 'conta'}</span>
                    <span className="text-content-muted">Página: {status.data.pageName ?? '—'}</span>
                    {status.data.tokenExpires && (
                      <span className="label-code text-content-muted">
                        Token válido até {new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(new Date(status.data.tokenExpires))}
                      </span>
                    )}
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <button type="button" className="btn-ghost" onClick={testarToken} disabled={busy}>
                      <Activity size={12} />
                      {busy ? 'Testando…' : 'Testar conexão'}
                    </button>
                    {session && (
                      <button type="button" className="btn-ghost" onClick={disconnect} disabled={busy}>
                        <Link2Off size={12} />
                        Desconectar
                      </button>
                    )}
                  </div>

                  {status.data.tokenValid === true && (
                    <p className="text-sm text-content-muted">Token respondendo normalmente na Meta.</p>
                  )}
                  {status.data.tokenValid === false && (
                    <p className="text-sm text-brand-soft">
                      O token não respondeu — reconecte a conta para voltar a publicar.
                    </p>
                  )}
                  <p className="text-sm text-content-muted">
                    Agora é só abrir um post em{' '}
                    <button type="button" className="text-brand-soft underline" onClick={() => navigate('/posts')}>
                      Posts Detalhados
                    </button>{' '}
                    e usar <strong>Publicar no Instagram</strong>.
                  </p>
                </>
              ) : (
                <>
                  <p className="text-sm text-content-muted">
                    Você será levado ao Facebook para autorizar o app. Precisa estar logado na conta que administra
                    a Página ligada ao Instagram da JH.COMP.
                  </p>
                  <button
                    type="button"
                    className="btn-primary self-start"
                    onClick={startConnect}
                    disabled={busy || !status.data.configured || !session}
                  >
                    {busy ? <Loader2 size={14} className="animate-spin" /> : <Instagram size={14} />}
                    {busy ? 'Conectando…' : 'Conectar conta do Instagram'}
                  </button>
                  {!session && <span className="text-xs text-content-muted">Entre com sua conta para conectar.</span>}
                </>
              )}

              {error && <p className="text-sm text-brand-soft">{error}</p>}
              {message && <p className="text-sm text-content-muted">{message}</p>}
            </section>
          </>
        )}
      </div>
    </>
  );
}

/** O que precisa estar pronto antes de publicar. */
function Checklist({ status }: { status: InstagramStatus }) {
  const items = [
    {
      ok: status.configured,
      label: 'App da Meta configurado no backend',
      hint: 'Defina INSTAGRAM_APP_ID e INSTAGRAM_APP_SECRET e reinicie o backend.',
    },
    {
      ok: status.publicUrlReady,
      label: 'Backend acessível por URL pública',
      hint: 'Defina INSTAGRAM_PUBLIC_BASE_URL (ngrok ou domínio) — a Meta baixa as artes de lá.',
    },
    {
      ok: status.connected,
      label: 'Conta profissional conectada',
      hint: 'Conta Comercial ou Criador, vinculada a uma Página do Facebook que você administra.',
    },
  ];

  return (
    <section className="panel flex flex-col gap-3 p-5">
      <span className="label-code text-brand-soft">Pré-requisitos</span>
      {items.map((item) => (
        <div key={item.label} className="flex items-start gap-3">
          {item.ok ? (
            <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-brand" />
          ) : (
            <TriangleAlert size={16} className="mt-0.5 shrink-0 text-content-muted" />
          )}
          <div className="flex flex-col">
            <span className={`text-sm ${item.ok ? 'text-content' : 'text-content-muted'}`}>{item.label}</span>
            {!item.ok && <span className="text-xs text-content-muted">{item.hint}</span>}
          </div>
        </div>
      ))}
    </section>
  );
}
