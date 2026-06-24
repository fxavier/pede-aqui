import { useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSelector, useDispatch } from 'react-redux'
import { checkoutService } from '@/lib/api/services'
import { clearCart, cartTotal } from '@/store/cart-slice'
import type { RootState, AppDispatch } from '@/store'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { formatMZN } from '@/lib/utils'

interface AddressForm {
  street: string
  number: string
  neighbourhood: string
  city: string
  notes: string
}

const EMPTY_ADDRESS: AddressForm = { street: '', number: '', neighbourhood: '', city: '', notes: '' }

export default function CheckoutPage() {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const cart = useSelector((s: RootState) => s.cart)
  const [step, setStep] = useState<'address' | 'summary'>('address')
  const [address, setAddress] = useState<AddressForm>(EMPTY_ADDRESS)
  const [errors, setErrors] = useState<Partial<AddressForm>>({})
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  // Idempotency key is generated once per checkout session
  const idempotencyKey = useRef(crypto.randomUUID())
  const total = cartTotal(cart.items)

  if (cart.items.length === 0) {
    navigate('/', { replace: true })
    return null
  }

  function validate(): boolean {
    const e: Partial<AddressForm> = {}
    if (!address.street.trim()) e.street = 'Obrigatório'
    if (!address.number.trim()) e.number = 'Obrigatório'
    if (!address.neighbourhood.trim()) e.neighbourhood = 'Obrigatório'
    if (!address.city.trim()) e.city = 'Obrigatório'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  function handleAddressSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (validate()) setStep('summary')
  }

  async function handleConfirm() {
    if (!cart.cartId) {
      setSubmitError('Carrinho inválido. Por favor recarrega a página.')
      return
    }
    setSubmitting(true)
    setSubmitError(null)
    try {
      const order = await checkoutService.checkout({
        cartId: cart.cartId,
        idempotencyKey: idempotencyKey.current,
      })
      // Store order in localStorage for history page
      const history: unknown[] = JSON.parse(localStorage.getItem('pede-aqui:orders') ?? '[]')
      history.unshift(order)
      localStorage.setItem('pede-aqui:orders', JSON.stringify(history.slice(0, 50)))
      dispatch(clearCart())
      navigate(`/orders/${order.id}/confirmation`, { replace: true })
    } catch {
      setSubmitError('Não foi possível finalizar o pedido. Por favor tenta novamente.')
    } finally {
      setSubmitting(false)
    }
  }

  if (step === 'address') {
    return (
      <div className="mx-auto max-w-lg space-y-6">
        <h1 className="text-2xl font-bold">Endereço de entrega</h1>
        <form onSubmit={handleAddressSubmit} className="space-y-4">
          <Field label="Rua / Avenida" error={errors.street}>
            <Input value={address.street} onChange={(e) => setAddress({ ...address, street: e.target.value })} placeholder="Av. Julius Nyerere" />
          </Field>
          <Field label="Número" error={errors.number}>
            <Input value={address.number} onChange={(e) => setAddress({ ...address, number: e.target.value })} placeholder="123" />
          </Field>
          <Field label="Bairro" error={errors.neighbourhood}>
            <Input value={address.neighbourhood} onChange={(e) => setAddress({ ...address, neighbourhood: e.target.value })} placeholder="Polana" />
          </Field>
          <Field label="Cidade" error={errors.city}>
            <Input value={address.city} onChange={(e) => setAddress({ ...address, city: e.target.value })} placeholder="Maputo" />
          </Field>
          <Field label="Notas (opcional)">
            <Input value={address.notes} onChange={(e) => setAddress({ ...address, notes: e.target.value })} placeholder="Andar, referência, etc." />
          </Field>
          <Button type="submit" className="w-full">Ver resumo do pedido</Button>
        </form>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <h1 className="text-2xl font-bold">Resumo do pedido</h1>

      <div className="rounded-lg border p-4 space-y-3">
        <h2 className="font-medium text-muted-foreground">Itens</h2>
        {cart.items.map((item) => (
          <div key={item.skuId} className="flex justify-between text-sm">
            <span>{item.quantity}× {item.productName}</span>
            <span>{formatMZN(item.unitPrice * item.quantity)}</span>
          </div>
        ))}
        <div className="border-t pt-2 flex justify-between font-semibold">
          <span>Total</span>
          <span>{formatMZN(total)}</span>
        </div>
      </div>

      <div className="rounded-lg border p-4 space-y-1 text-sm">
        <h2 className="font-medium text-muted-foreground mb-2">Endereço</h2>
        <p>{address.street}, {address.number}</p>
        <p>{address.neighbourhood}, {address.city}</p>
        {address.notes && <p className="text-muted-foreground">{address.notes}</p>}
      </div>

      {submitError && <p className="text-sm text-destructive">{submitError}</p>}

      <div className="flex gap-3">
        <Button variant="outline" onClick={() => setStep('address')} disabled={submitting}>Voltar</Button>
        <Button className="flex-1" onClick={handleConfirm} disabled={submitting}>
          {submitting ? 'A confirmar…' : 'Confirmar pedido'}
        </Button>
      </div>
    </div>
  )
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label className="text-sm font-medium">{label}</label>
      {children}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}
