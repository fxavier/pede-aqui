import { createBrowserRouter } from 'react-router-dom'
import { AppShell } from '@/components/layout/AppShell'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import HomePage from './page'
import LoginPage from './login/page'
import RegisterPage from './register/page'
import CallbackPage from './auth/callback/page'
import VendorPage from './vendor/page'
import CheckoutPage from './checkout/page'
import OrdersPage from './orders/page'
import OrderDetailPage from './orders/detail/page'
import OrderConfirmationPage from './orders/confirmation/page'
import CatalogoPage from './catalogo/page'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'vendor/:vendorId', element: <VendorPage /> },
      { path: 'catalogo/:verticalId', element: <CatalogoPage /> },
      {
        path: 'checkout',
        element: <ProtectedRoute><CheckoutPage /></ProtectedRoute>,
      },
      {
        path: 'orders',
        element: <ProtectedRoute><OrdersPage /></ProtectedRoute>,
      },
      {
        path: 'orders/:orderId',
        element: <ProtectedRoute><OrderDetailPage /></ProtectedRoute>,
      },
      {
        path: 'orders/:orderId/confirmation',
        element: <ProtectedRoute><OrderConfirmationPage /></ProtectedRoute>,
      },
    ],
  },
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/auth/callback', element: <CallbackPage /> },
])
