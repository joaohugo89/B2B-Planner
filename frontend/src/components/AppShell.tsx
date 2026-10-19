import { NavLink, Outlet } from 'react-router-dom';
import { Activity } from 'lucide-react';
import { AuthControl } from './AuthControl';

const NAV = [
  { to: '/', label: 'Feed Grid', end: true },
  { to: '/posts', label: 'Posts Detalhados', end: false },
  { to: '/copy', label: 'Guia de Copy', end: false },
  { to: '/templates', label: 'Exportador de Templates', end: false },
  { to: '/studio', label: 'Editor de Posts', end: false },
  { to: '/ai', label: 'Assistente IA', end: false },
  { to: '/instagram', label: 'Instagram', end: false },
];

export function AppShell() {
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
              {NAV.map((item) => (
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
          </div>
        </div>
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
