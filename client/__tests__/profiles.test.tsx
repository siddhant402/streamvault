import { describe, it, expect, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));
vi.mock('@/lib/auth-context', () => ({
  useAuth: () => ({ setProfile: vi.fn(), logout: vi.fn(), isAuthenticated: true }),
}));
vi.mock('@/lib/api', () => ({ profilesApi: { list: vi.fn(), create: vi.fn() } }));

import ProfilesPage from '@/app/profiles/page';
import { profilesApi } from '@/lib/api';

describe('V10 - profile age limit', () => {
  it('a new profile can be created with an age limit', async () => {
    vi.mocked(profilesApi.list).mockResolvedValue({ data: [] } as never);
    vi.mocked(profilesApi.create).mockResolvedValue({
      data: { id: 'p1', name: 'Sam', ageRestriction: 7, createdAt: '2026-11-14T00:00:00Z' },
    } as never);
    render(<ProfilesPage />);

    fireEvent.click(await screen.findByText('Add Profile'));

    const who = screen.getByLabelText('Who is this profile for?') as HTMLSelectElement;
    const options = Array.from(who.options).map(o => o.textContent);
    expect(options).toEqual([
      'Adults (all movies)', 'Teens 16+', 'Teens 13+', 'Kids 7+', 'Young kids (U / G only)',
    ]);
    expect(who.value).toBe('18');   // adult by default

    fireEvent.change(screen.getByPlaceholderText('Profile name'), { target: { value: 'Sam' } });
    fireEvent.change(who, { target: { value: '7' } });
    fireEvent.click(screen.getByText('Create'));

    await waitFor(() => expect(profilesApi.create).toHaveBeenCalledWith('Sam', 7));
  });
});
