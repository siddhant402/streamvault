import { describe, it, expect, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

const { push, signIn } = vi.hoisted(() => ({ push: vi.fn(), signIn: vi.fn() }));
vi.mock('next/navigation', () => ({ useRouter: () => ({ push, replace: vi.fn() }) }));
vi.mock('@/lib/auth-context', () => ({ useAuth: () => ({ signIn }) }));
vi.mock('@/lib/api', () => ({ authApi: { register: vi.fn(), login: vi.fn() } }));

import LoginPage from '@/app/login/page';
import { authApi } from '@/lib/api';

const register = vi.mocked(authApi.register);
const login = vi.mocked(authApi.login);

function fill(email: string, password: string) {
  fireEvent.change(screen.getByPlaceholderText('you@example.com'), { target: { value: email } });
  fireEvent.change(screen.getByPlaceholderText('••••••••'), { target: { value: password } });
}

describe('login page', () => {
  it('V6: asks for an invite code only when creating an account', () => {
    render(<LoginPage />);
    expect(screen.queryByPlaceholderText('From the server owner')).toBeNull();

    fireEvent.click(screen.getByText('New to StreamVault? Register'));
    expect(screen.getByLabelText('Invite code')).toBeTruthy();
  });

  it('V6/V12: registering sends the invite code, then signs in with the cookie', async () => {
    register.mockResolvedValue({} as never);
    login.mockResolvedValue({ data: { email: 'a@b.c', role: 'USER' } } as never);
    render(<LoginPage />);
    fireEvent.click(screen.getByText('New to StreamVault? Register'));

    fill('a@b.c', 'Password123!');
    fireEvent.change(screen.getByLabelText('Invite code'), { target: { value: '  family-code ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => expect(push).toHaveBeenCalledWith('/profiles'));
    expect(register).toHaveBeenCalledWith('a@b.c', 'Password123!', 'family-code');
    expect(login).toHaveBeenCalledWith('a@b.c', 'Password123!');
    expect(signIn).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem('sv_token')).toBeNull();
  });

  it("V6: shows the server's message, such as the sign-in lock", async () => {
    login.mockRejectedValue({ response: { status: 429, data: { error: 'Too many failed sign-in attempts. Try again in 15 minutes.' } } });
    render(<LoginPage />);

    fill('a@b.c', 'wrong');
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));   // the heading also says "Sign in"

    expect(await screen.findByText('Too many failed sign-in attempts. Try again in 15 minutes.')).toBeTruthy();
    expect(signIn).not.toHaveBeenCalled();
  });
});
