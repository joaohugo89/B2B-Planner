import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { Activity, Menu, X } from 'lucide-react';
import { AuthControl } from './AuthControl';
import { useAuth } from '../auth/AuthContext';

/**
 * Só o Feed Grid é aberto. O resto é a bancada de trabalho — rascunhos, legendas, templates,
 * chaves das integrações — e só aparece para quem entra com a conta.
 */
const NAV = [
  { to: '/', label: 'Feed Grid', end: true, publico: true },
  { to: '/posts', label: 'Posts Detalhados', end: false, publico: false },
  { to: '/copy', label: 'Guia de Copy', end: false, publico: false },
  { to: '/templates', label: 'Exportador de Templates', end: false, publico: false },
  { to: '/studio', label: 'Editor de Posts', end: false, publico: false },
  { to: '/ai', label: 'Assistente IA', end: false, publico: false },
  { to: '/instagram', label: 'Instagram', end: false, publico: false },
];

export function AppShell() {
  const { session } = useAuth();
  const [menuAberto, setMenuAberto] = useState(false);
  const { pathname } = useLocation();

  const abas = NAV.filter((item) => session || item.publico);

  // Trocar de tela fecha o menu: no celular ele cobre o conteúdo.
  useEffect(() => setMenuAberto(false), [pathname]);

  return (
    <div className="flex min-h-full flex-col">
      <header className="fixed inset-x-0 top-0 z-50 border-b border-surface-high bg-surface-lowest/90 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-4 md:px-8">
          <div className="flex items-center gap-6">
            <div className="flex items-center gap-2">
              <div className="flex h-9 w-9 items-center justify-center rounded border border-outline-variant bg-surface-high font-display text-xl text-brand-soft">
                JH
              </div>
              <div className="flex flex-col leading-none">
                <span className="font-display text-base tracking-tight">JH.COMP</span>
                <span className="label-code mt-1 text-content-muted">Tecnologia Humana &amp; B2B</span>
              </div>
            </div>

            <nav className="hidden h-16 items-center lg:flex">
              {abas.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `flex h-full items-center px-4 text-sm transition-colors ${
                      isActive
                        ? 'border-b-2 border-brand bg-surface/50 text-brand-soft'
                        : 'text-content-muted hover:text-content'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>

          <div className="flex items-center gap-3">
            <div className="hidden items-center gap-2 rounded border border-outline-variant/40 bg-surface px-2 py-1 lg:flex">
              <Activity size={12} className="text-brand animate-pulse" />
              <span className="label-code text-content-variant">SYSTEM LIVE // IG-HUB</span>
            </div>
            <AuthControl />

            {/* Sem isto, em tela estreita não há como sair da primeira tela. */}
            <button
              type="button"
              onClick={() => setMenuAberto((aberto) => !aberto)}
              aria-expanded={menuAberto}
              aria-label={menuAberto ? 'Fechar menu' : 'Abrir menu'}
              className={`h-9 w-9 items-center justify-center rounded border border-outline-variant bg-surface text-content-variant transition-colors hover:text-content lg:hidden ${
                abas.length > 1 ? 'flex' : 'hidden'
              }`}
            >
              {menuAberto ? <X size={16} /> : <Menu size={16} />}
            </button>
          </div>
        </div>

        {menuAberto && (
          <nav className="max-h-[70vh] overflow-y-auto border-t border-surface-high bg-surface-lowest px-4 pb-3 lg:hidden">
            {abas.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `block border-b border-surface-high/60 py-3 text-sm transition-colors ${
                    isActive ? 'text-brand-soft' : 'text-content-muted'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        )}
      </header>

      <main className="flex-1 pt-16">
        <Outlet />
      </main>

      <footer className="border-t border-surface-high bg-surface-lowest py-8">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-3 px-4 md:flex-row md:px-8">
          <span className="label-code text-content-muted">JH.COMP ENGINEERING SYSTEM // TECH FOR HUMANS &amp; ENTERPRISE</span>
          <span className="label-code text-brand-soft">FORMAT 1080x1350 // GRID 4:5</span>
        </div>
      </footer>
    </div>
  );
}
