'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';

export default function RootPage() {
  const { isAuthenticated, profile } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isAuthenticated) {
      router.replace('/login');
    } else if (!profile) {
      router.replace('/profiles');
    } else {
      router.replace('/home');
    }
  }, [isAuthenticated, profile, router]);

  return (
    <div className="min-h-screen bg-sv-black flex items-center justify-center">
      <div className="w-8 h-8 border-2 border-sv-accent border-t-transparent rounded-full animate-spin" />
    </div>
  );
}
