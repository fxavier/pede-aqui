import { useState } from 'react'
import { Star } from 'lucide-react'
import type { OrderRating } from '@/lib/api/types'
import {
  RATING_COMMENT_MAX, ratingErrorMessage, ratingRequest, useMyOrderRating, useSaveMyOrderRating,
} from '@/lib/ratings'

/**
 * Rating block of a delivered order (LAC-28): a prompt (store stars, courier stars when a courier delivered, optional
 * comment) while the order can be rated, then the submitted rating with the store's reply; "Editar" during the first
 * 24 hours. Renders nothing before delivery or when the rating window closed without a rating.
 */
export function OrderRatingCard({ orderId, delivered }: { orderId: string; delivered: boolean }) {
  const { data: state } = useMyOrderRating(orderId, delivered)
  const [editing, setEditing] = useState(false)

  if (!delivered || !state) return null

  if (state.rating && !editing) {
    return (
      <SubmittedRating
        rating={state.rating}
        editable={state.editable}
        onEdit={() => setEditing(true)}
      />
    )
  }
  if (!state.rating && !state.rateable) return null

  return (
    <RatingForm
      orderId={orderId}
      courierRateable={state.courierRateable}
      initial={state.rating}
      onDone={() => setEditing(false)}
      onCancel={state.rating ? () => setEditing(false) : undefined}
    />
  )
}

function RatingForm({ orderId, courierRateable, initial, onDone, onCancel }: {
  orderId: string
  courierRateable: boolean
  initial: OrderRating | null
  onDone: () => void
  onCancel?: () => void
}) {
  const [vendorStars, setVendorStars] = useState(initial?.vendorStars ?? 0)
  const [courierStars, setCourierStars] = useState<number | null>(initial?.courierStars ?? null)
  const [comment, setComment] = useState(initial?.comment ?? '')
  const mutation = useSaveMyOrderRating(orderId, initial ? 'edit' : 'submit')

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (vendorStars < 1) return
    mutation.mutate(ratingRequest(vendorStars, courierRateable ? courierStars : null, comment), { onSuccess: onDone })
  }

  return (
    <form onSubmit={submit} className="bg-white rounded-3xl border border-amber-100 p-5 shadow-sm text-left space-y-4">
      <h3 className="font-display font-extrabold text-slate-800 text-base">
        {initial ? 'Editar avaliação' : 'Como correu a encomenda?'}
      </h3>
      <StarPicker label="Loja" value={vendorStars} onChange={setVendorStars} />
      {courierRateable && (
        <StarPicker label="Estafeta" value={courierStars ?? 0} onChange={setCourierStars} />
      )}
      <label className="block text-sm text-slate-600 space-y-1">
        <span>Comentário (opcional)</span>
        <textarea
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          maxLength={RATING_COMMENT_MAX}
          rows={3}
          className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
        />
        <span className="block text-right text-[11px] text-slate-400">{comment.length}/{RATING_COMMENT_MAX}</span>
      </label>
      {mutation.isError && (
        <p className="text-sm text-rose-700" role="alert">{ratingErrorMessage(mutation.error)}</p>
      )}
      <div className="flex gap-2">
        {onCancel && (
          <button type="button" onClick={onCancel}
            className="flex-1 px-4 py-2.5 rounded-xl border border-slate-200 text-slate-600 font-bold text-sm">
            Cancelar
          </button>
        )}
        <button
          type="submit"
          disabled={vendorStars < 1 || mutation.isPending}
          className="flex-1 px-4 py-2.5 rounded-xl bg-brand-600 text-white font-bold text-sm disabled:opacity-50"
        >
          {mutation.isPending ? 'A enviar…' : initial ? 'Guardar' : 'Enviar avaliação'}
        </button>
      </div>
    </form>
  )
}

function SubmittedRating({ rating, editable, onEdit }: { rating: OrderRating; editable: boolean; onEdit: () => void }) {
  return (
    <div className="bg-white rounded-3xl border border-slate-100 p-5 shadow-sm text-left space-y-3">
      <h3 className="font-display font-extrabold text-slate-800 text-base">A sua avaliação</h3>
      <Stars label="Loja" value={rating.vendorStars} />
      {rating.courierStars != null && <Stars label="Estafeta" value={rating.courierStars} />}
      {rating.comment && <p className="text-sm text-slate-600">“{rating.comment}”</p>}
      {rating.commentHidden && (
        <p className="text-xs text-slate-400">O comentário foi ocultado pela moderação; a nota continua a contar.</p>
      )}
      {rating.vendorReply && (
        <div className="rounded-2xl bg-slate-50 p-3 text-sm text-slate-600">
          <span className="block text-[11px] font-bold uppercase text-slate-400">Resposta da loja</span>
          {rating.vendorReply}
        </div>
      )}
      {editable && (
        <button type="button" onClick={onEdit}
          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 text-slate-700 font-bold text-sm hover:bg-slate-50">
          Editar
        </button>
      )}
    </div>
  )
}

function StarPicker({ label, value, onChange }: { label: string; value: number; onChange: (stars: number) => void }) {
  return (
    <div className="flex items-center justify-between gap-3" role="radiogroup" aria-label={label}>
      <span className="text-sm font-semibold text-slate-600">{label}</span>
      <div className="flex gap-1">
        {[1, 2, 3, 4, 5].map((stars) => (
          <button
            key={stars}
            type="button"
            role="radio"
            aria-checked={value === stars}
            aria-label={`${label}: ${stars} ${stars === 1 ? 'estrela' : 'estrelas'}`}
            onClick={() => onChange(stars)}
            className="p-0.5"
          >
            <Star className={`w-6 h-6 ${stars <= value ? 'fill-amber-400 text-amber-400' : 'text-slate-300'}`} />
          </button>
        ))}
      </div>
    </div>
  )
}

function Stars({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex items-center justify-between gap-3" aria-label={`${label}: ${value} de 5`}>
      <span className="text-sm font-semibold text-slate-600">{label}</span>
      <div className="flex gap-0.5">
        {[1, 2, 3, 4, 5].map((stars) => (
          <Star key={stars} className={`w-4 h-4 ${stars <= value ? 'fill-amber-400 text-amber-400' : 'text-slate-200'}`} />
        ))}
      </div>
    </div>
  )
}
