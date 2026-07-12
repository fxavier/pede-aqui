import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSelector, useDispatch } from 'react-redux'
import {
  ChevronLeft, MapPin, CreditCard, Smartphone, Coins,
  MessageSquare, ShieldCheck, ShoppingBag, Sparkles,
} from 'lucide-react'
import { checkoutService } from '@/lib/api/services'
import { clearCart, cartTotal, cartItemCount } from '@/store/cart-slice'
import type { RootState, AppDispatch } from '@/store'
import { formatMZN } from '@/lib/utils'

interface AddressForm { street: string; number: string; neighbourhood: string; city: string; notes: string }
const EMPTY: AddressForm = { street: '', number: '', neighbourhood: '', city: '', notes: '' }

/* Cosmetic: no payment integration on the backend yet. */
const PAYMENTS = [
  { id: 'mpesa', label: 'M-Pesa',  detail: 'Pagamento móvel Vodacom', icon: Smartphone },
  { id: 'card',  label: 'Cartão',  detail: 'Visa / Mastercard',       icon: CreditCard },
  { id: 'cash',  label: 'Numerário', detail: 'Pagar na entrega',       icon: Coins },
] as const

export default function CheckoutPage() {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const cart = useSelector((s: RootState) => s.cart)
  const [address, setAddress] = useState<AddressForm>(EMPTY)
  const [payment, setPayment] = useState<string>('mpesa')
  const [errors, setErrors] = useState<Partial<AddressForm>>({})
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const idempotencyKey = useRef(crypto.randomUUID())

  const subtotal = cartTotal(cart.items)
  const count = cartItemCount(cart.items)

  // Redirect away from an empty-cart checkout in an effect (never during render).
  const isEmpty = cart.items.length === 0
  useEffect(() => {
    if (isEmpty) navigate('/', { replace: true })
  }, [isEmpty, navigate])

  if (isEmpty) return null

  function validate(): boolean {
    const e: Partial<AddressForm> = {}
    if (!address.street.trim()) e.street = 'Obrigatório'
    if (!address.number.trim()) e.number = 'Obrigatório'
    if (!address.neighbourhood.trim()) e.neighbourhood = 'Obrigatório'
    if (!address.city.trim()) e.city = 'Obrigatório'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  async function handleConfirm() {
    if (!validate()) return
    if (!cart.cartId) { setSubmitError('Carrinho inválido. Recarregue a página.'); return }
    setSubmitting(true)
    setSubmitError(null)
    try {
      // Backend checkout only needs cartId + idempotencyKey. Address/payment are
      // collected for UX and future support but not sent in the current API.
      const order = await checkoutService.checkout({ cartId: cart.cartId, idempotencyKey: idempotencyKey.current })
      const history: unknown[] = JSON.parse(localStorage.getItem('pede-aqui:orders') ?? '[]')
      history.unshift(order)
      localStorage.setItem('pede-aqui:orders', JSON.stringify(history.slice(0, 50)))
      dispatch(clearCart())
      navigate(`/orders/${order.id}/confirmation`, { replace: true })
    } catch {
      setSubmitError('Não foi possível finalizar o pedido. Tente novamente.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-6 py-6 sm:py-10">
      <div className="flex items-center gap-3 text-left">
        <button onClick={() => navigate(-1)} className="p-2.5 bg-white hover:bg-slate-50 border border-slate-100 rounded-full shadow-sm transition-all">
          <ChevronLeft className="w-5 h-5 text-slate-700" />
        </button>
        <div>
          <h1 className="font-display font-extrabold text-2xl sm:text-3xl text-slate-800 tracking-tight leading-none">Finalizar Encomenda</h1>
          <p className="text-xs sm:text-sm text-slate-400 font-semibold uppercase mt-0.5 tracking-wide">Checkout seguro</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Left */}
        <div className="lg:col-span-2 space-y-6">
          {/* Address */}
          <div className="bg-white rounded-3xl border border-slate-100 p-5 sm:p-6 shadow-sm space-y-4 text-left">
            <div className="flex items-center gap-2 pb-3 border-b border-slate-50">
              <div className="w-8 h-8 rounded-lg bg-brand-50 flex items-center justify-center text-brand-600"><MapPin className="w-4 h-4" /></div>
              <h2 className="font-display font-extrabold text-slate-800 text-base">1. Morada de Entrega</h2>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <TextField label="Rua / Avenida" value={address.street} error={errors.street} onChange={(v) => setAddress({ ...address, street: v })} placeholder="Av. Julius Nyerere" />
              <TextField label="Número" value={address.number} error={errors.number} onChange={(v) => setAddress({ ...address, number: v })} placeholder="1200" />
              <TextField label="Bairro" value={address.neighbourhood} error={errors.neighbourhood} onChange={(v) => setAddress({ ...address, neighbourhood: v })} placeholder="Polana" />
              <TextField label="Cidade" value={address.city} error={errors.city} onChange={(v) => setAddress({ ...address, city: v })} placeholder="Maputo" />
            </div>
          </div>

          {/* Payment (cosmetic) */}
          <div className="bg-white rounded-3xl border border-slate-100 p-5 sm:p-6 shadow-sm space-y-4 text-left">
            <div className="flex items-center gap-2 pb-3 border-b border-slate-50">
              <div className="w-8 h-8 rounded-lg bg-brand-50 flex items-center justify-center text-brand-600"><CreditCard className="w-4 h-4" /></div>
              <h2 className="font-display font-extrabold text-slate-800 text-base">2. Método de Pagamento</h2>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              {PAYMENTS.map((p) => {
                const Icon = p.icon
                const active = payment === p.id
                return (
                  <button
                    key={p.id}
                    onClick={() => setPayment(p.id)}
                    className={`p-4 rounded-2xl border transition-all text-left relative flex flex-col justify-between h-32 ${
                      active ? 'border-brand-500 bg-brand-50/40 ring-2 ring-brand-500/10' : 'border-slate-100 hover:border-slate-200 bg-white'
                    }`}
                  >
                    {active && <span className="absolute top-3 right-3 bg-brand-600 text-white w-4 h-4 rounded-full flex items-center justify-center text-[10px] font-bold">✓</span>}
                    <div>
                      <Icon className="w-5 h-5 text-slate-700 mb-2" />
                      <h4 className="font-display font-bold text-slate-800 text-xs sm:text-sm">{p.label}</h4>
                    </div>
                    <p className="text-slate-400 text-[11px] font-medium leading-tight">{p.detail}</p>
                  </button>
                )
              })}
            </div>
          </div>

          {/* Notes */}
          <div className="bg-white rounded-3xl border border-slate-100 p-5 sm:p-6 shadow-sm space-y-4 text-left">
            <div className="flex items-center gap-2 pb-3 border-b border-slate-50">
              <div className="w-8 h-8 rounded-lg bg-brand-50 flex items-center justify-center text-brand-600"><MessageSquare className="w-4 h-4" /></div>
              <h2 className="font-display font-extrabold text-slate-800 text-base">3. Instruções Adicionais</h2>
            </div>
            <textarea
              rows={3}
              placeholder="Ex: campainha avariada, ligar ao chegar à porta..."
              value={address.notes}
              onChange={(e) => setAddress({ ...address, notes: e.target.value })}
              className="w-full border border-slate-200 rounded-2xl p-3 text-xs focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 text-slate-700"
            />
          </div>
        </div>

        {/* Right — summary (real) */}
        <div className="space-y-6">
          <div className="bg-white rounded-3xl border border-slate-100 p-6 shadow-sm space-y-5 text-left sticky top-24">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <h3 className="font-display font-extrabold text-slate-800 text-base">Resumo da Compra</h3>
              <span className="px-2.5 py-0.5 bg-brand-50 text-brand-600 text-[10px] font-extrabold rounded-full flex items-center gap-0.5">
                <ShoppingBag className="w-3 h-3" /> {count} {count === 1 ? 'ITEM' : 'ITENS'}
              </span>
            </div>

            <div className="space-y-3 max-h-40 overflow-y-auto pr-1">
              {cart.items.map((item) => (
                <div key={item.skuId} className="flex justify-between text-xs font-semibold text-slate-600">
                  <span className="truncate max-w-[180px]">{item.quantity}× {item.productName}</span>
                  <span className="text-slate-800">{formatMZN(item.unitPrice * item.quantity)}</span>
                </div>
              ))}
            </div>

            <hr className="border-slate-100" />

            <div className="space-y-2 text-xs text-slate-500 font-semibold">
              <div className="flex justify-between"><span>Subtotal</span><span className="text-slate-800 font-bold">{formatMZN(subtotal)}</span></div>
              <div className="flex justify-between"><span>Taxa de entrega</span><span className="text-slate-400">Confirmada no pedido</span></div>
              <div className="flex justify-between text-base text-slate-800 font-extrabold pt-3 border-t border-slate-100">
                <span>Total</span><span className="text-brand-600 text-lg">{formatMZN(subtotal)}</span>
              </div>
            </div>

            <div className="p-3 bg-slate-50 rounded-2xl flex items-center gap-2 border border-slate-100 text-slate-500">
              <ShieldCheck className="w-5 h-5 text-emerald-600 flex-shrink-0" />
              <p className="text-[10px] leading-tight font-medium">A sua transação é processada de forma segura.</p>
            </div>

            {submitError && <p className="text-xs text-destructive font-medium">{submitError}</p>}

            <button
              onClick={handleConfirm}
              disabled={submitting}
              className={`w-full bg-brand-600 hover:bg-brand-500 text-white font-bold h-12 rounded-xl flex items-center justify-center gap-2 shadow-lg shadow-brand-500/20 transition-all ${submitting ? 'opacity-70 cursor-not-allowed' : 'active:scale-[0.98]'}`}
            >
              {submitting ? (
                <><span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> A processar...</>
              ) : (
                <><Sparkles className="w-4 h-4 text-brand-200" /> Confirmar e Pagar</>
              )}
            </button>
            <button onClick={() => navigate(-1)} className="w-full text-center text-xs font-bold text-slate-400 hover:text-slate-600 transition-colors py-1">Voltar ao menu</button>
          </div>
        </div>
      </div>
    </div>
  )
}

function TextField({ label, value, error, onChange, placeholder }: { label: string; value: string; error?: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <div className="space-y-1">
      <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">{label}</label>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className={`w-full border rounded-xl p-3 text-xs focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 text-slate-700 ${error ? 'border-red-300' : 'border-slate-200'}`}
      />
      {error && <p className="text-[10px] text-red-500 font-semibold">{error}</p>}
    </div>
  )
}
