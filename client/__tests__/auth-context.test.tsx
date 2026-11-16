import { describe, it, expect, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';

vi.mock('@/lib/api', () => ({ authApi: { me: vi.fn(), logout: vi.fn() } }));
vi.mock('@/lib/navigation', () => ({ goTo: vi.fn() }));

import { AuthProvider, useAuth } from '@/lib/auth-context';
import { authApi } from '@/lib/api';
import { goTo } from '@/lib/navigation';

const me = vi.mocked(authApi.me);
const logoutCall = vi.mocked(authApi.logout);

function Probe() {
  const { isAuthenticated, profile, logout, signIn } = useAuth();
  return (
    <div>
      <span data-testid="auth">{String(isAuthenticated)}</span>
      <span data-testid="profile">{profile?.name ?? 'none'}</span>
      <button onClick={logout}>out</button>
      <button onClick={signIn}>in</button>
    </div>
  );
}

const renderApp = () => render(<AuthProvider><Probe /></AuthProvider>);

describe('V12 - sign-in state comes from the server, not storage', () => {
  it('is signed in when the server recognises the session cookie', async () => {
    me.mockResolvedValue({ data: { email: 'a@b.c', role: 'USER' } } as never);
    renderApp();
    expect((await screen.findByTestId('auth')).textContent).toBe('true');
  });

  it('is signed out when the server says 401', async () => {
    me.mockRejectedValue(new Error('401'));
    renderApp();
    expect((await screen.findByTestId('auth')).textContent).toBe('false');
  });

  it('deletes a token left in localStorage by the old version', async () => {
    localStorage.setItem('sv_token', 'old-token');
    me.mockRejectedValue(new Error('401'));
    renderApp();
    await screen.findByTestId('auth');
    expect(localStorage.getItem('sv_token')).toBeNull();
  });

  it('shows nothing until the sign-in check finishes', () => {
    me.mockReturnValue(new Promise(() => {}) as never);
    renderApp();
    expect(screen.queryByTestId('auth')).toBeNull();
  });

  it('sign-out calls the server, forgets the profile and goes to sign-in', async () => {
    localStorage.setItem('sv_profile', JSON.stringify({ id: 'p1', name: 'Kid' }));
    me.mockResolvedValue({ data: {} } as never);
    logoutCall.mockResolvedValue({} as never);
    renderApp();
    expect((await screen.findByTestId('profile')).textContent).toBe('Kid');

    await act(async () => { screen.getByText('out').click(); });

    expect(logoutCall).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem('sv_profile')).toBeNull();
    expect(screen.getByTestId('auth').textContent).toBe('false');
    expect(goTo).toHaveBeenCalledWith('/login');
  });

  it('signing in clears a profile left over from another account', async () => {
    localStorage.setItem('sv_profile', JSON.stringify({ id: 'p1', name: 'Someone else' }));
    me.mockRejectedValue(new Error('401'));
    renderApp();
    await screen.findByTestId('auth');

    await act(async () => { screen.getByText('in').click(); });

    expect(screen.getByTestId('auth').textContent).toBe('true');
    expect(screen.getByTestId('profile').textContent).toBe('none');
    expect(localStorage.getItem('sv_profile')).toBeNull();
  });
});
