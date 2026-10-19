import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { Bot, CheckCircle2, Instagram, LogOut, Save, TriangleAlert, User, X } from 'lucide-react';
import {
  aiApi,
  instagramApi,
  settingsApi,
  type AiUsage,
  type IntegrationSettings,
  type InstagramStatus,
} from '../api/services';
import { describeError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

/** Painel lateral: sessão, estado das conexões e as chaves das integrações. */
export function UserPanel({ onClose }: { onClose: () => void }) {
  const { session, signOut } = useAuth();
  const [settings, setSettings] = useState<IntegrationSettings | null>(null);
  const [instagram, setInstagram] = useState<InstagramStatus | null>(null);
  const [usage, setUsage] = useState<AiUsage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  useEffect(() => {
    Promise.all([settingsApi.getIntegrations(), instagramApi.status()])
      .then(([integrations, status]) => {
        setSettings(integrations);
        setInstagram(status);
      })
      .catch((err) => setError(describeError(err)))
      .finally(() => setLoading(false));
  }, []);

  // O consumo exige token: só busca quando há sessão, e falha em silêncio.
  useEffect(() => {
    if (!session) {
      setUsage(null);
      return;
    }
    aiApi.usage().then(setUsage).catch(() => setUsage(null));
  }, [session]);

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex justify-end bg-black/60 backdrop-blur-sm"
      onClick={(event) => event.target === event.currentTarget && onClose()}
    >
      <aside className="flex h-full w-full max-w-md flex-col overflow-y-auto border-l border-surface-high bg-surface-lowest">
        <header className="flex items-center justify-between border-b border-surface-high px-4 py-3">
          <div className="flex items-center gap-2">
            <User size={16} className="text-brand" />
            <span className="label-code text-brand-soft">Sua conta</span>
          </div>
          <button type="button" className="btn-ghost px-2" onClick={onClose} aria-label="Fechar painel">
            <X size={14} />
          </button>
        </header>

        <div className="flex flex-col gap-5 p-4">
          {/* Sessão */}
          <section className="panel flex flex-col gap-2 p-4">
            {session ? (
              <>
                <span className="text-sm font-semibold">{session.name}</span>
                <span className="label-code text-content-muted">{session.role}</span>
                <span className="label-code text-brand-soft">Sessão ativa</span>
                <button type="button" className="btn-ghost self-start" onClick={() => { signOut(); onClose(); }}>
                  <LogOut size={12} />
                  Sair
                </button>
              </>
            ) : (
              <span className="text-sm text-content-muted">
                Você não está conectado. Use o botão “Entrar” no topo para editar o planner.
              </span>
            )}
          </section>

          {loading && <p className="label-code text-content-muted">Carregando integrações…</p>}
          {error && <p className="text-sm text-brand-soft">{error}</p>}

          {settings && (
            <>
              <ConnectionRow
                icon={<Instagram size={14} />}
                label="Instagram"
                ok={Boolean(instagram?.connected)}
                detail={
                  instagram?.connected
                    ? `@${instagram.username ?? 'conta'} • ${instagram.pageName ?? 'página'}`
                    : settings.instagramReady
                      ? 'Chaves prontas, falta conectar a conta'
                      : 'Informe App ID e App Secret abaixo'
                }
              />
              <ConnectionRow
                icon={<Bot size={14} />}
                label="Assistente de IA"
                ok={settings.aiReady}
                detail={settings.aiReady ? `Ativo • ${settings.aiModel}` : 'Sem chave da Anthropic'}
              />

              {usage && <UsagePanel usage={usage} />}

              <IntegrationForm
                settings={settings}
                canEdit={Boolean(session)}
                onSaved={(updated) => {
                  setSettings(updated);
                  instagramApi.status().then(setInstagram).catch(() => undefined);
                }}
              />
            </>
          )}
        </div>
      </aside>
    </div>,
    document.body,
  );
}

function ConnectionRow({ icon, label, ok, detail }: { icon: React.ReactNode; label: string; ok: boolean; detail: string }) {
  return (
    <div className="flex items-center gap-3 rounded bg-surface-low px-3 py-2">
      <span className={ok ? 'text-brand' : 'text-content-muted'}>{icon}</span>
      <div className="flex min-w-0 flex-1 flex-col">
        <span className="text-sm">{label}</span>
        <span className="truncate text-xs text-content-muted">{detail}</span>
      </div>
      {ok ? (
        <CheckCircle2 size={16} className="shrink-0 text-brand" />
      ) : (
        <TriangleAlert size={16} className="shrink-0 text-content-muted" />
      )}
    </div>
  );
}

/** Consumo de tokens do mês e as últimas chamadas ao Claude. */
function UsagePanel({ usage }: { usage: AiUsage }) {
  const tokens = (value: number) => value.toLocaleString('pt-BR');

  return (
    <section className="panel flex flex-col gap-3 p-4">
      <span className="label-code text-brand-soft">Consumo da IA neste mês</span>
      <div className="grid grid-cols-3 gap-2">
        <Metric label="Chamadas" value={tokens(usage.calls)} />
        <Metric label="Tokens entrada" value={tokens(usage.inputTokens)} />
        <Metric label="Tokens saída" value={tokens(usage.outputTokens)} />
      </div>
      {usage.recent.length > 0 ? (
        <ul className="flex flex-col gap-1">
          {usage.recent.slice(0, 5).map((entry, index) => (
            <li key={`${entry.at}-${index}`} className="flex justify-between font-mono text-[11px] text-content-muted">
              <span>
                {new Date(entry.at).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })} • {entry.operation}
              </span>
              <span>
                {tokens(entry.inputTokens)} / {tokens(entry.outputTokens)}
              </span>
            </li>
          ))}
        </ul>
      ) : (
        <span className="text-xs text-content-muted">Nenhuma chamada registrada ainda.</span>
      )}
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col rounded bg-surface-low px-2 py-1">
      <span className="label-code text-content-muted">{label}</span>
      <span className="font-mono text-sm">{value}</span>
    </div>
  );
}

function IntegrationForm({
  settings,
  canEdit,
  onSaved,
}: {
  settings: IntegrationSettings;
  canEdit: boolean;
  onSaved: (updated: IntegrationSettings) => void;
}) {
  const [appId, setAppId] = useState(settings.instagramAppId);
  const [appSecret, setAppSecret] = useState('');
  const [redirectUri, setRedirectUri] = useState(settings.instagramRedirectUri);
  const [publicBaseUrl, setPublicBaseUrl] = useState(settings.instagramPublicBaseUrl);
  const [aiKey, setAiKey] = useState('');
  const [aiModel, setAiModel] = useState(settings.aiModel);
  const [aiBrandInstructions, setAiBrandInstructions] = useState(settings.aiBrandInstructions ?? '');
  const [aiEffort, setAiEffort] = useState(settings.aiEffort || 'medium');

  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    setBusy(true);
    setError(null);
    try {
      const payload: Record<string, string> = {
        instagramAppId: appId,
        instagramRedirectUri: redirectUri,
        instagramPublicBaseUrl: publicBaseUrl,
        aiModel,
        aiBrandInstructions,
        aiEffort,
      };
      // Segredos só vão quando preenchidos — campo em branco mantém o que já está salvo.
      if (appSecret.trim()) payload.instagramAppSecret = appSecret.trim();
      if (aiKey.trim()) payload.aiApiKey = aiKey.trim();

      const updated = await settingsApi.saveIntegrations(payload);
      onSaved(updated);
      setAppSecret('');
      setAiKey('');
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  async function clearSecret(field: 'instagramAppSecret' | 'aiApiKey') {
    setBusy(true);
    try {
      onSaved(await settingsApi.saveIntegrations({ [field]: '' }));
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel flex flex-col gap-4 p-4">
      <span className="label-code text-brand-soft">Chaves das integrações</span>
      <p className="text-xs text-content-muted">
        Guardadas no banco do planner e usadas na hora. Deixar em branco faz valer a variável de ambiente do backend.
      </p>

      <div className="flex flex-col gap-3">
        <span className="label-code text-content-muted">Instagram</span>
        <Field label="App ID" value={appId} onChange={setAppId} disabled={!canEdit} />
        <SecretField
          label="App Secret"
          value={appSecret}
          onChange={setAppSecret}
          defined={settings.instagramAppSecretDefined}
          disabled={!canEdit}
          onClear={() => clearSecret('instagramAppSecret')}
        />
        <Field label="URI de redirecionamento" value={redirectUri} onChange={setRedirectUri} disabled={!canEdit} />
        <Field
          label="URL pública do backend"
          value={publicBaseUrl}
          onChange={setPublicBaseUrl}
          disabled={!canEdit}
          hint="A Meta baixa as artes daqui — use ngrok ou seu domínio."
        />
      </div>

      <div className="flex flex-col gap-3">
        <span className="label-code text-content-muted">Assistente de IA</span>
        <SecretField
          label="Chave da Anthropic"
          value={aiKey}
          onChange={setAiKey}
          defined={settings.aiApiKeyDefined}
          disabled={!canEdit}
          onClear={() => clearSecret('aiApiKey')}
        />
        <Field label="Modelo" value={aiModel} onChange={setAiModel} disabled={!canEdit} />

        <label className="flex flex-col gap-1">
          <span className="label-code text-content-muted">Esforço do modelo</span>
          <select
            value={aiEffort}
            disabled={!canEdit}
            onChange={(event) => setAiEffort(event.target.value)}
            className="rounded bg-surface-lowest px-3 py-2 font-mono text-xs outline-none disabled:opacity-60"
          >
            <option value="low">low — rápido e barato</option>
            <option value="medium">medium — equilíbrio</option>
            <option value="high">high — mais criativo e caro</option>
          </select>
        </label>

        <label className="flex flex-col gap-1">
          <span className="label-code text-content-muted">Instruções extras da marca</span>
          <textarea
            value={aiBrandInstructions}
            disabled={!canEdit}
            rows={5}
            placeholder="Ex.: evite emojis no título; cite sempre o prazo médio de atendimento."
            onChange={(event) => setAiBrandInstructions(event.target.value)}
            className="rounded bg-surface-lowest px-3 py-2 font-mono text-xs outline-none disabled:opacity-60"
          />
          <span className="text-[11px] text-content-muted">
            Entram no prompt do sistema, depois do contexto da marca.
          </span>
        </label>
      </div>

      {error && <p className="text-sm text-brand-soft">{error}</p>}

      {canEdit ? (
        <button type="button" className="btn-primary self-start" onClick={save} disabled={busy}>
          <Save size={14} />
          {busy ? 'Salvando…' : saved ? 'Salvo!' : 'Salvar chaves'}
        </button>
      ) : (
        <span className="text-xs text-content-muted">Entre com sua conta para editar as chaves.</span>
      )}
    </section>
  );
}

function Field({
  label,
  value,
  onChange,
  disabled,
  hint,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  hint?: string;
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="label-code text-content-muted">{label}</span>
      <input
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        className="rounded bg-surface-lowest px-3 py-2 font-mono text-xs outline-none disabled:opacity-60"
      />
      {hint && <span className="text-[11px] text-content-muted">{hint}</span>}
    </label>
  );
}

function SecretField({
  label,
  value,
  onChange,
  defined,
  disabled,
  onClear,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  defined: boolean;
  disabled?: boolean;
  onClear: () => void;
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="label-code text-content-muted">
        {label} {defined && <span className="text-brand-soft">• já definido</span>}
      </span>
      <div className="flex items-center gap-2">
        <input
          type="password"
          value={value}
          disabled={disabled}
          placeholder={defined ? '•••••••• (deixe em branco para manter)' : 'cole a chave aqui'}
          onChange={(event) => onChange(event.target.value)}
          className="flex-1 rounded bg-surface-lowest px-3 py-2 font-mono text-xs outline-none disabled:opacity-60"
        />
        {defined && !disabled && (
          <button type="button" className="btn-ghost px-2" onClick={onClear} title="Apagar a chave salva">
            <X size={12} />
          </button>
        )}
      </div>
    </label>
  );
}
