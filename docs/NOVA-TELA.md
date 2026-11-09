# Criando uma tela nova no planner

Receita para acrescentar um módulo — calendário editorial, relatórios, biblioteca de mídia —
sem reinventar o que já existe.

## As peças prontas

Antes de escrever marcação, veja o que já está feito. Quase toda tela se monta com isto:

| Componente | Para quê | Onde |
|---|---|---|
| `PageHeader` | título, subtítulo e ações no topo | `components/primitives` |
| `Section` | bloco de conteúdo com cabeçalho e ícone | `components/ui` |
| `Field` · `TextArea` · `Select` | campos de formulário com rótulo | `components/ui` |
| `Button` | botão com estado de espera | `components/ui` |
| `ConfirmAction` | exclusão com confirmação no lugar | `components/ui` |
| `LoadingState` · `ErrorState` · `EmptyState` | os três estados de uma lista | `components/primitives` |
| `MetricCard` · `StatusTag` | números do topo, etiqueta de situação | `components/primitives` |

Regra prática: **se você está escrevendo `className="rounded bg-surface px-3 py-2..."`, pare.**
Ou o componente existe, ou vale criá-lo em `ui.tsx` — foi assim que 38 campos acabaram com a
mesma classe copiada à mão.

## O esqueleto

```tsx
export function CalendarioPage() {
  const { session } = useAuth();
  // A lista muda conforme ter conta ou não: entrar/sair refaz a busca.
  const itens = useApi(() => calendarioApi.list(), [Boolean(session)]);

  return (
    <>
      <PageHeader
        eyebrow="PLANEJAMENTO"
        title="Calendário editorial"
        description="O que vai ao ar em cada semana."
        actions={session && <Button variant="primary" icon={<Plus size={14} />}>Nova data</Button>}
      />

      <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-10 md:px-8">
        {itens.loading && !itens.data && <LoadingState />}
        {itens.error && <ErrorState message={itens.error} onRetry={itens.reload} />}
        {itens.data?.length === 0 && <EmptyState message="Nada agendado ainda." />}

        {itens.data?.map((item) => (
          <Section key={item.id} title={item.titulo} icon={<CalendarDays size={14} />}>
            …
          </Section>
        ))}
      </div>
    </>
  );
}
```

O invólucro `mx-auto flex max-w-7xl flex-col gap-6 px-4 py-10 md:px-8` é o mesmo em todas as
telas — é o que alinha a largura do conteúdo com o cabeçalho.

## Ligando a tela ao resto

**1. A rota**, em `App.tsx`. Telas da bancada entram protegidas e sob demanda:

```tsx
const CalendarioPage = lazy(() => import('./pages/CalendarioPage').then((m) => ({ default: m.CalendarioPage })));
…
<Route path="calendario" element={<Protegida><CalendarioPage /></Protegida>} />
```

**2. A aba**, no `NAV` de `AppShell.tsx`. O campo `publico` decide quem vê:

```tsx
{ to: '/calendario', label: 'Calendário', end: false, publico: false },
```

**3. As chamadas**, em `api/services.ts`, junto das outras.

**4. A permissão**, em `SecurityConfig.java`. Sem conta, só o Feed Grid é aberto; qualquer rota
nova cai no `anyRequest().authenticated()` e já nasce protegida. Só mexa ali se a tela precisar
ser pública — e aí pense duas vezes.

## Três armadilhas que já custaram caro aqui

**A lista não recarrega ao entrar na conta.** O `useApi` recebe dependências; passe
`[Boolean(session)]` quando o que a API devolve depender de estar logado. Sem isso, você faz
login e continua vendo a versão de visitante.

**O valor vindo da IA não é o valor do banco.** Modelos respondem "Carrossel" e
"B2B / Gestores de PMEs" onde o planner espera `CARROSSEL` e `B2B`. Normalize antes de gravar,
como faz `AiStudioPage`.

**URL de imagem passa por `assetUrl`.** Nunca jogue direto num `src` ou `href`: a função filtra
esquemas executáveis e resolve o caminho relativo contra a origem do backend.

## Depois de criar

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.local.yml -f docker-compose.dominio.yml --env-file .env up -d --build
```

E rode `mvn test` no backend se tiver mexido nele. O hook de `pre-push` faz as duas coisas
quando você envia, mas não espere até lá para descobrir que quebrou.
