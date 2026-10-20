import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * Telas da bancada de trabalho. Esconder a aba no menu não basta: sem isto, digitar
 * /studio na barra de endereço abriria a tela do mesmo jeito.
 *
 * A sessão é lida do armazenamento do navegador de forma síncrona, então um F5 numa
 * dessas telas continua funcionando para quem está logado.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { session } = useAuth();
  const location = useLocation();

  if (!session) {
    return <Navigate to="/" replace state={{ from: location.pathname }} />;
  }

  return <>{children}</>;
}
