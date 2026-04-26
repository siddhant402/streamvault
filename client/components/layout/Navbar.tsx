'use client';

import Link from 'next/link';
import { useRouter, usePathname } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';

export default function Navbar() {
  const { profile, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const isPlayer = pathname?.startsWith('/player');
  if (isPlayer) return null;

  return (
    <nav className="fixed top-0 left-0 right-0 z-50 h-16">
      <div className="absolute inset-0 bg-gradient-to-b from-black/80 to-transparent pointer-events-none" />
      <div className="relative flex items-center justify-between px-8 md:px-16 h-full">
        <Link href="/home" className="font-light text-xl tracking-[0.15em] text-white hover:text-white/80 transition-colors">
          STREAM<span className="text-red-500">VAULT</span>
        </Link>

        <div className="flex items-center gap-6">
          {profile && (
            <button onClick={() => router.push('/profiles')}
                    className="flex items-center gap-2.5 text-white/50 hover:text-white/80 transition-colors group">
              <div className="w-7 h-7 rounded-md bg-red-500/80 group-hover:bg-red-500 flex items-center justify-center text-xs font-semibold text-white transition-colors">
                {profile.name.charAt(0).toUpperCase()}
              </div>
              <span className="hidden sm:block text-sm">{profile.name}</span>
            </button>
          )}
          <button onClick={logout} className="text-white/25 hover:text-white/60 text-xs tracking-[0.15em] uppercase transition-colors">
            Sign out
          </button>
        </div>
      </div>
    </nav>
  );
}
