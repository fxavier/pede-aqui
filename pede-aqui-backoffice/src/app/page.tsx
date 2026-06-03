'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useAppSelector } from '@/store/hooks';

export default function HomePage() {
  const router = useRouter();
  const { user, isAuthenticated, isLoading } = useAppSelector((state) => state.auth);

  useEffect(() => {
    if (isLoading) return;

    if (!isAuthenticated) {
      router.replace('/login');
      return;
    }

    const userRole = user?.role;
    
    switch (userRole) {
      case 'ADMIN':
        router.replace('/admin');
        break;
      case 'VENDOR_ADMIN':
        router.replace('/empresa');
        break;
      case 'OPS':
        router.replace('/catalogo');
        break;
      case 'FINANCE':
        router.replace('/finance');
        break;
      case 'SUPPORT':
        router.replace('/support');
        break;
      case 'COURIER':
        router.replace('/orders');
        break;
      default:
        // For unknown roles, redirect to login to prevent access
        router.replace('/login');
        break;
    }
  }, [isLoading, isAuthenticated, user?.role, router]);

  if (isLoading) {
    return null;
  }

  return null;
}