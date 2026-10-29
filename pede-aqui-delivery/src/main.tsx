import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { RouterProvider } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { store } from './store'
import { initAuth } from './store/auth-slice'
import { router } from './app/router'
import { purgeLegacyOrderCache } from './lib/orderHistory'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 30_000, retry: 1 } },
})

store.dispatch(initAuth())
// Older builds cached the order history (with addresses and PINs) in localStorage; it now lives only in the API.
purgeLegacyOrderCache()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </Provider>
  </StrictMode>
)
