import { useState, type FormEvent } from 'react';
import { Upload } from 'lucide-react';
import { assetsApi } from '../api/services';
import { describeError } from '../api/client';

const KINDS = [
  { value: 'BACKGROUND', label: 'Background' },
  { value: 'LOGO', label: 'Logo' },
  { value: 'GUIDELINE', label: 'Diretriz' },
];

const RATIOS = [
  { value: 'RATIO_4_5', label: '4:5 — 1080×1350' },
  { value: 'RATIO_1_1', label: '1:1 — 1080×1080' },
  { value: 'VECTOR', label: 'Vetorial / outro' },
];

/** Sobe um arquivo de template e já registra o asset no catálogo. */
export function AssetUploadForm({ onUploaded }: { onUploaded: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [kind, setKind] = useState('BACKGROUND');
  const [ratio, setRatio] = useState('RATIO_4_5');
  const [theme, setTheme] = useState('DARK');
  const [composition, setComposition] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  const [fileInputKey, setFileInputKey] = useState(0);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!file || !code.trim() || !name.trim() || busy) return;

    setBusy(true);
    setError(null);
    setDone(null);
    try {
      const asset = await assetsApi.upload(file, {
        code: code.trim().toUpperCase(),
        name: name.trim(),
        kind,
        ratio,
        theme,
        composition: composition.trim() || undefined,
      });
      setDone(`${asset.code} enviado.`);
      setFile(null);
      setCode('');
      setName('');
      setComposition('');
      setFileInputKey((key) => key + 1); // remonta o input para limpar o arquivo escolhido
      onUploaded();
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="panel flex flex-col gap-3 p-4">
      <div className="flex items-center gap-2">
        <Upload size={16} className="text-brand" />
        <span className="label-code text-brand-soft">Enviar template</span>
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
        <input
          key={fileInputKey}
          type="file"
          accept=".png,.jpg,.jpeg,.webp,.svg,.zip,.afdesign,.json"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          className="rounded bg-surface-lowest px-3 py-2 text-xs file:mr-3 file:rounded file:border-0 file:bg-surface-high file:px-3 file:py-1 file:text-content md:col-span-2"
        />
        <input
          value={code}
          onChange={(event) => setCode(event.target.value)}
          placeholder="Código (ex.: BG-V11)"
          className="rounded bg-surface-lowest px-3 py-2 text-sm outline-none placeholder:text-content-muted/60"
        />
        <input
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Nome do template"
          className="rounded bg-surface-lowest px-3 py-2 text-sm outline-none placeholder:text-content-muted/60"
        />
      </div>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <select value={kind} onChange={(e) => setKind(e.target.value)} className="rounded bg-surface-lowest px-3 py-2 font-mono text-xs">
          {KINDS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
        <select value={ratio} onChange={(e) => setRatio(e.target.value)} className="rounded bg-surface-lowest px-3 py-2 font-mono text-xs">
          {RATIOS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
        <select value={theme} onChange={(e) => setTheme(e.target.value)} className="rounded bg-surface-lowest px-3 py-2 font-mono text-xs">
          <option value="DARK">Dark</option>
          <option value="LIGHT">Light</option>
        </select>
        <input
          value={composition}
          onChange={(event) => setComposition(event.target.value)}
          placeholder="Composição (opcional)"
          className="rounded bg-surface-lowest px-3 py-2 text-sm outline-none placeholder:text-content-muted/60"
        />
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="label-code text-content-muted">
          PNG, JPG, WEBP, SVG, ZIP, AFDESIGN ou JSON — até 25 MB.
        </span>
        <button type="submit" className="btn-primary" disabled={busy || !file || !code.trim() || !name.trim()}>
          <Upload size={14} />
          {busy ? 'Enviando…' : 'Enviar'}
        </button>
      </div>

      {error && <p className="text-sm text-brand-soft">{error}</p>}
      {done && <p className="text-sm text-content-muted">{done}</p>}
    </form>
  );
}
