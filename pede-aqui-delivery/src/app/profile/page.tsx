import { useSelector } from 'react-redux'
import { useNavigate } from 'react-router-dom'
import {
  Award, MapPin, CreditCard, ChevronRight, Sparkles, LogOut,
  CheckCircle, Settings, HelpCircle, Gift,
} from 'lucide-react'
import type { RootState } from '@/store'
import { logout } from '@/features/auth/logout'

function initials(name: string | null): string {
  if (!name) return 'PA'
  const p = name.trim().split(/\s+/)
  return ((p[0]?.[0] ?? '') + (p[1]?.[0] ?? '')).toUpperCase() || 'PA'
}

export default function ProfilePage() {
  const navigate = useNavigate()
  const { status, displayName, email } = useSelector((s: RootState) => s.auth)

  if (status !== 'authenticated') {
    return (
      <div className="max-w-md mx-auto px-4 py-20 text-center space-y-4">
        <div className="w-16 h-16 rounded-full bg-slate-50 flex items-center justify-center mx-auto text-3xl">👤</div>
        <h1 className="font-display font-bold text-slate-800 text-lg">Inicie sessão</h1>
        <p className="text-slate-400 text-sm">Entre para ver o seu perfil, encomendas e benefícios.</p>
        <button onClick={() => navigate('/login?redirect=/profile')} className="px-5 py-2.5 bg-brand-600 hover:bg-brand-500 text-white font-bold text-xs rounded-full shadow-md shadow-brand-500/10 transition-all">Entrar</button>
      </div>
    )
  }

  async function handleLogout() {
    const redirecting = await logout()
    if (!redirecting) navigate('/')
  }

  const name = displayName ?? 'Cliente Pede Aqui'

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8 py-6 sm:py-10 text-left">
      <div className="bg-white rounded-3xl border border-slate-100 overflow-hidden shadow-sm">
        <div className="h-32 bg-gradient-to-r from-brand-600 via-rose-500 to-accent-orange" />
        <div className="px-6 pb-6 relative flex flex-col sm:flex-row items-center sm:items-end gap-5 -mt-12 sm:-mt-8">
          <div className="w-24 h-24 rounded-2xl bg-gradient-to-tr from-brand-600 to-accent-orange text-white text-3xl font-extrabold flex items-center justify-center border-4 border-white shadow-md">
            {initials(name)}
          </div>
          <div className="flex-1 text-center sm:text-left space-y-1">
            <div className="flex flex-wrap items-center justify-center sm:justify-start gap-2">
              <h2 className="font-display font-extrabold text-2xl text-slate-800 tracking-tight leading-none">{name}</h2>
              <span className="px-2.5 py-0.5 rounded-full text-[9px] font-extrabold tracking-wider uppercase bg-amber-100 text-amber-800 flex items-center gap-0.5">
                <Award className="w-3 h-3 text-amber-600 fill-amber-500" /> Membro
              </span>
            </div>
            {email && <p className="text-xs text-slate-400 font-medium">{email}</p>}
          </div>
        </div>
      </div>

      {/* Rewards — cosmetic: no loyalty backend yet */}
      <div className="bg-gradient-to-tr from-slate-900 to-slate-950 text-white rounded-3xl p-6 shadow-xl relative overflow-hidden">
        <div className="absolute right-0 bottom-0 w-64 h-64 bg-brand-500 rounded-full blur-3xl opacity-15" />
        <div className="relative z-10 space-y-6">
          <div className="flex items-center gap-2">
            <div className="w-9 h-9 rounded-xl bg-amber-400/10 flex items-center justify-center text-amber-400"><Sparkles className="w-5 h-5" /></div>
            <div>
              <h3 className="font-display font-extrabold text-white text-base">Clube de Benefícios Pede Aqui</h3>
              <p className="text-[10px] text-slate-400 font-bold tracking-wider uppercase">Programa de fidelização em breve</p>
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2 border-t border-white/10">
            {[
              ['Cashback', 'Ganhe pontos em cada pedido.'],
              ['Entregas grátis', 'Vantagens para membros frequentes.'],
              ['Suporte prioritário', 'Atendimento mais rápido.'],
            ].map(([t, d]) => (
              <div key={t} className="flex items-start gap-2 text-xs">
                <CheckCircle className="w-4 h-4 text-emerald-400 flex-shrink-0 mt-0.5" />
                <div><p className="font-bold text-slate-200">{t}</p><p className="text-[11px] text-slate-400">{d}</p></div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-white rounded-3xl border border-slate-100 p-6 shadow-sm space-y-3">
          <h3 className="font-display font-extrabold text-slate-800 text-base pb-2 border-b border-slate-50 flex items-center gap-1.5"><MapPin className="w-4 h-4 text-brand-500" /> Moradas Guardadas</h3>
          <p className="text-slate-400 text-xs font-medium">Ainda não tem moradas guardadas. Serão memorizadas a partir das suas encomendas.</p>
        </div>
        <div className="bg-white rounded-3xl border border-slate-100 p-6 shadow-sm space-y-3">
          <h3 className="font-display font-extrabold text-slate-800 text-base pb-2 border-b border-slate-50 flex items-center gap-1.5"><CreditCard className="w-4 h-4 text-brand-500" /> Formas de Pagamento</h3>
          <p className="text-slate-400 text-xs font-medium">Gerido no momento do checkout (M-Pesa, cartão ou numerário).</p>
        </div>
      </div>

      <div className="bg-white rounded-3xl border border-slate-100 p-6 shadow-sm space-y-2">
        {([[Settings, 'Configurações da Conta'], [HelpCircle, 'Ajuda & Suporte'], [Gift, 'Convidar amigos']] as const).map(([Icon, label]) => (
          <button key={label} className="w-full flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 transition-colors text-slate-700">
            <span className="flex items-center gap-2.5 text-xs font-semibold"><Icon className="w-4 h-4 text-slate-400" /> {label}</span>
            <ChevronRight className="w-4 h-4 text-slate-400" />
          </button>
        ))}
        <hr className="border-slate-50 my-2" />
        <button onClick={handleLogout} className="w-full flex items-center justify-between p-3 rounded-xl hover:bg-red-50 text-red-600 transition-colors">
          <span className="flex items-center gap-2.5 text-xs font-bold"><LogOut className="w-4 h-4 text-red-500" /> Terminar Sessão</span>
          <ChevronRight className="w-4 h-4 text-red-400" />
        </button>
      </div>
    </div>
  )
}
