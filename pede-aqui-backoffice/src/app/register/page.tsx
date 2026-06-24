'use client';

import { useState } from 'react';
import Link from 'next/link';
import { registrationService } from '@/lib/api/services';
import type { MerchantRegistrationPayload } from '@/lib/api/types';

interface FormData extends Omit<MerchantRegistrationPayload, 'confirmPassword'> {
  confirmPassword: string;
}

const toSlug = (name: string) =>
  name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

const getStepForField = (fieldName: string): 1 | 2 | 3 => {
  const step1Fields = ['companyName','companySlug','legalName','taxNumber','businessType','industry','country','city','address','defaultCurrency','companyPhone','companyEmail'];
  const step2Fields = ['firstName','lastName','email','phone','password'];
  if (step1Fields.includes(fieldName)) return 1;
  if (step2Fields.includes(fieldName)) return 2;
  return 3;
};

const STEPS = [
  { n: 1, label: 'Empresa',      desc: 'Dados da sua empresa', icon: 'store' },
  { n: 2, label: 'Proprietário', desc: 'Conta de administrador', icon: 'person' },
  { n: 3, label: 'Confirmação',  desc: 'Reveja e submeta', icon: 'check_circle' },
];

const inputCls = 'auth-input-reg w-full';

export default function RegisterPage() {
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [form, setForm] = useState<FormData>({
    companyName: '', companySlug: '', legalName: '', taxNumber: '',
    businessType: '', industry: '', country: '', city: '', address: '',
    defaultCurrency: '', companyPhone: '', companyEmail: '',
    firstName: '', lastName: '', email: '', phone: '',
    password: '', confirmPassword: '', referralCode: '', promoCode: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [globalError, setGlobalError] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const updateForm = (field: keyof FormData, value: string) => {
    setForm(prev => ({ ...prev, [field]: value }));
    if (errors[field]) setErrors(prev => ({ ...prev, [field]: '' }));
    if (field === 'companyName') setForm(prev => ({ ...prev, companySlug: toSlug(value) }));
  };

  const validateStep1 = () => {
    const e: Record<string, string> = {};
    const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!form.companyName.trim()) e.companyName = 'Nome da empresa é obrigatório';
    if (!form.companySlug.trim()) e.companySlug = 'Slug é obrigatório';
    if (form.companySlug && !/^[a-z0-9-]+$/.test(form.companySlug))
      e.companySlug = 'Apenas letras minúsculas, números e hífens';
    if (!form.country.trim()) e.country = 'País é obrigatório';
    if (!form.defaultCurrency.trim()) e.defaultCurrency = 'Moeda é obrigatória';
    if (form.companyEmail && !emailRe.test(form.companyEmail))
      e.companyEmail = 'Email inválido';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const validateStep2 = () => {
    const e: Record<string, string> = {};
    const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!form.firstName.trim()) e.firstName = 'Primeiro nome é obrigatório';
    if (!form.lastName.trim()) e.lastName = 'Último nome é obrigatório';
    if (!form.email.trim()) e.email = 'Email é obrigatório';
    if (form.email && !emailRe.test(form.email)) e.email = 'Email inválido';
    if (!form.password.trim()) e.password = 'Palavra-passe é obrigatória';
    if (form.password && form.password.length < 8) e.password = 'Mínimo 8 caracteres';
    if (!form.confirmPassword.trim()) e.confirmPassword = 'Confirmação é obrigatória';
    if (form.password !== form.confirmPassword) e.confirmPassword = 'As palavras-passe não coincidem';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleNext = () => {
    if (step === 1 && validateStep1()) setStep(2);
    else if (step === 2 && validateStep2()) setStep(3);
  };

  const handleBack = () => {
    if (step > 1) { setStep((step - 1) as 1 | 2 | 3); setGlobalError(''); }
  };

  const handleSubmit = async () => {
    setLoading(true);
    setGlobalError('');
    try {
      const { confirmPassword, ...payload } = form;
      await registrationService.register(payload);
      setSuccess(true);
    } catch (error: any) {
      if (error.code === 'tenant_slug_exists') {
        setErrors({ companySlug: 'Este slug já está em uso.' }); setStep(1);
      } else if (error.code === 'email_exists') {
        setErrors({ email: 'Este email já está registado.' }); setStep(2);
      } else if (error.code === 'validation_failed' && error.fieldErrors) {
        const fe: Record<string, string> = {};
        let firstStep = 3;
        error.fieldErrors.forEach((f: any) => {
          fe[f.field] = f.message;
          const s = getStepForField(f.field);
          if (s < firstStep) firstStep = s;
        });
        setErrors(fe); setStep(firstStep as 1 | 2 | 3);
      } else {
        setGlobalError(error.message || 'Erro inesperado. Tente novamente.');
      }
    } finally {
      setLoading(false);
    }
  };

  /* ─── Success screen ─── */
  if (success) {
    return (
      <>
        <style>{`
          .reg-page { font-family: 'Plus Jakarta Sans','DM Sans',sans-serif; }
          @keyframes pa-fadeup { from{opacity:0;transform:translateY(14px)}to{opacity:1;transform:translateY(0)} }
          .pa-fadeup { animation: pa-fadeup 0.5s ease both; }
          @keyframes pa-pop { from{opacity:0;transform:scale(0.7)}to{opacity:1;transform:scale(1)} }
          .pa-pop { animation: pa-pop 0.45s cubic-bezier(.34,1.56,.64,1) both; }
        `}</style>
        <div className="reg-page min-h-screen flex items-center justify-center bg-white px-6">
          <div className="text-center pa-fadeup max-w-sm">
            <div className="pa-pop w-20 h-20 rounded-full flex items-center justify-center mx-auto mb-6"
                 style={{ background: '#FEF2F2' }}>
              <span className="material-symbols-outlined" style={{ fontSize: 40, color: '#B32700', fontVariationSettings: "'FILL' 1" }}>
                check_circle
              </span>
            </div>
            <h2 className="text-2xl font-bold text-gray-900 mb-2">Conta criada com sucesso!</h2>
            <p className="text-sm text-gray-500 mb-8 leading-relaxed">
              A sua empresa foi registada. Pode agora iniciar sessão com o seu email e palavra-passe.
            </p>
            <Link href="/login"
                  className="inline-flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-semibold text-white transition-all"
                  style={{ background: '#B32700' }}>
              <span className="material-symbols-outlined" style={{ fontSize: 16 }}>login</span>
              Ir para Login
            </Link>
          </div>
        </div>
      </>
    );
  }

  /* ─── Main register layout ─── */
  return (
    <>
      <style>{`
        .reg-page { font-family: 'Plus Jakarta Sans','DM Sans',sans-serif; }

        /* ---- left panel ---- */
        .auth-left-reg {
          background:
            radial-gradient(ellipse at 10% 90%, rgba(179,39,0,0.35) 0%, transparent 55%),
            radial-gradient(ellipse at 85% 15%, rgba(179,39,0,0.18) 0%, transparent 45%),
            #160A04;
        }
        .dot-grid-reg {
          background-image: radial-gradient(circle, rgba(255,255,255,0.18) 1px, transparent 1px);
          background-size: 28px 28px;
        }

        /* ---- step indicators ---- */
        .step-item { display:flex; align-items:flex-start; gap:12px; padding:10px 0; }
        .step-icon {
          width:36px; height:36px; border-radius:50%; display:flex; align-items:center; justify-content:center;
          font-size:16px; flex-shrink:0; border:1.5px solid rgba(255,255,255,0.12);
          transition: background 0.3s, border-color 0.3s;
        }
        .step-icon.active { background: #B32700; border-color: #B32700; }
        .step-icon.done   { background: rgba(179,39,0,0.3); border-color: rgba(179,39,0,0.5); }
        .step-icon.future { background: rgba(255,255,255,0.04); }
        .step-connector { width:1.5px; height:28px; margin-left:17px; background: rgba(255,255,255,0.1); }
        .step-connector.done { background: rgba(179,39,0,0.4); }

        /* ---- right panel ---- */
        .reg-right { overflow-y: auto; height: 100vh; }

        /* ---- inputs ---- */
        .auth-input-reg {
          height: 48px;
          padding: 0 14px;
          background: #F9FAFB;
          border: 1.5px solid #E5E7EB;
          border-radius: 10px;
          font-size: 14px;
          color: #111827;
          font-family: inherit;
          transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
          outline: none;
          width: 100%;
          display: block;
        }
        .auth-input-reg::placeholder { color: #9CA3AF; }
        .auth-input-reg:focus {
          border-color: #B32700;
          box-shadow: 0 0 0 3px rgba(179,39,0,0.10);
          background: #fff;
        }
        .auth-input-reg.has-error { border-color: #EF4444; }
        .auth-input-reg.has-error:focus { box-shadow: 0 0 0 3px rgba(239,68,68,0.10); }

        select.auth-input-reg { appearance: none; cursor: pointer; }

        /* password wrap */
        .pw-wrap { position: relative; }
        .pw-input { padding-right: 42px !important; }
        .pw-eye {
          position:absolute; right:13px; top:50%; transform:translateY(-50%);
          background:none; border:none; cursor:pointer; color:#9CA3AF; padding:0; display:flex;
          align-items:center; font-size:18px; transition: color 0.2s;
        }
        .pw-eye:hover { color: #6B7280; }

        /* ---- label ---- */
        .field-label { display:block; font-size:13px; font-weight:600; color:#374151; margin-bottom:5px; }
        .field-hint  { font-size:11px; color:#9CA3AF; margin-top:3px; }
        .field-error { font-size:11px; color:#DC2626; margin-top:3px; display:flex; align-items:center; gap:3px; }

        /* ---- review card ---- */
        .review-row { display:grid; grid-template-columns:1fr 1fr; gap:4px 12px; }
        .review-key { font-size:12px; color:#6B7280; padding:4px 0; }
        .review-val { font-size:12px; font-weight:600; color:#111827; padding:4px 0; word-break:break-all; }

        /* ---- buttons ---- */
        .btn-primary-reg {
          height:48px; background:#B32700; color:#fff; border:none; border-radius:10px;
          font-size:14px; font-weight:600; font-family:inherit; cursor:pointer;
          transition: background 0.2s, transform 0.15s, box-shadow 0.2s;
          display:flex; align-items:center; justify-content:center; gap:8px; width:100%;
        }
        .btn-primary-reg:hover:not(:disabled) {
          background:#961F00; transform:translateY(-1px);
          box-shadow: 0 6px 16px rgba(179,39,0,0.28);
        }
        .btn-primary-reg:active:not(:disabled) { transform:translateY(0); }
        .btn-primary-reg:disabled { opacity:0.6; cursor:not-allowed; }

        .btn-ghost-reg {
          height:48px; background:transparent; color:#6B7280; border:1.5px solid #E5E7EB;
          border-radius:10px; font-size:14px; font-weight:600; font-family:inherit; cursor:pointer;
          transition: border-color 0.2s, color 0.2s, background 0.2s;
          display:flex; align-items:center; justify-content:center; gap:6px; width:100%;
        }
        .btn-ghost-reg:hover:not(:disabled) { border-color:#B32700; color:#B32700; background:#FEF2F2; }
        .btn-ghost-reg:disabled { opacity:0.5; cursor:not-allowed; }

        /* ---- progress bar ---- */
        .prog-bar-track { height:3px; background:#F3F4F6; border-radius:99px; overflow:hidden; }
        .prog-bar-fill  { height:100%; background:#B32700; border-radius:99px; transition:width 0.4s ease; }

        /* ---- animations ---- */
        @keyframes pa-fadeup  { from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:translateY(0)} }
        @keyframes pa-spin    { to{transform:rotate(360deg)} }
        .pa-fadeup  { animation: pa-fadeup 0.38s ease both; }
        .pa-spinner { animation: pa-spin 0.75s linear infinite; }
        @keyframes pa-shake {
          0%,100%{transform:translateX(0)} 20%,60%{transform:translateX(-4px)} 40%,80%{transform:translateX(4px)}
        }
        .pa-shake { animation: pa-shake 0.35s ease; }
      `}</style>

      <div className="reg-page min-h-screen flex">

        {/* ═══ LEFT PANEL ═══ */}
        <div className="auth-left-reg hidden lg:flex lg:w-[38%] xl:w-[36%] relative flex-col justify-between p-10 xl:p-12 overflow-hidden shrink-0" style={{ position: 'sticky', top: 0, height: '100vh' }}>
          <div className="dot-grid-reg absolute inset-0 pointer-events-none" />
          <div className="absolute" style={{ width:380,height:380,bottom:-120,right:-100,borderRadius:'50%',border:'1px solid rgba(255,255,255,0.06)' }} />
          <div className="absolute" style={{ width:200,height:200,bottom:100,right:50,borderRadius:'50%',border:'1px solid rgba(255,255,255,0.06)' }} />

          {/* Logo */}
          <div className="relative z-10 flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl flex items-center justify-center" style={{ background:'#B32700' }}>
              <span className="material-symbols-outlined text-white" style={{ fontSize:18, fontVariationSettings:"'FILL' 1" }}>location_on</span>
            </div>
            <span className="text-white font-bold text-lg">Pede Aqui</span>
          </div>

          {/* Headline */}
          <div className="relative z-10">
            <p className="text-xs font-semibold tracking-widest uppercase mb-4" style={{ color:'#B32700' }}>Novo parceiro</p>
            <h1 className="text-white font-extrabold leading-tight mb-3" style={{ fontSize:32 }}>
              Junte-se à rede<br />de parceiros<br />
              <span style={{ color:'#FF6A3D' }}>Pede Aqui.</span>
            </h1>
            <p className="text-sm leading-relaxed" style={{ color:'rgba(255,255,255,0.45)', maxWidth:240 }}>
              Leva apenas alguns minutos para registar a sua empresa e começar a receber pedidos.
            </p>
          </div>

          {/* Step indicators */}
          <div className="relative z-10">
            <p className="text-xs font-semibold tracking-widest uppercase mb-5" style={{ color:'rgba(255,255,255,0.3)' }}>
              Progresso
            </p>
            {STEPS.map((s, i) => {
              const state = step > s.n ? 'done' : step === s.n ? 'active' : 'future';
              return (
                <div key={s.n}>
                  <div className="step-item">
                    <div className={`step-icon ${state}`}>
                      {state === 'done' ? (
                        <span className="material-symbols-outlined text-white" style={{ fontSize:16, fontVariationSettings:"'FILL' 1" }}>check</span>
                      ) : (
                        <span className="material-symbols-outlined" style={{ fontSize:16, fontVariationSettings:"'FILL' 1", color: state === 'active' ? '#fff' : 'rgba(255,255,255,0.35)' }}>
                          {s.icon}
                        </span>
                      )}
                    </div>
                    <div>
                      <div className="text-sm font-semibold" style={{ color: state === 'future' ? 'rgba(255,255,255,0.3)' : '#fff' }}>
                        {s.label}
                      </div>
                      <div className="text-xs" style={{ color:'rgba(255,255,255,0.28)' }}>{s.desc}</div>
                    </div>
                  </div>
                  {i < STEPS.length - 1 && <div className={`step-connector ${state === 'done' ? 'done' : ''}`} />}
                </div>
              );
            })}
          </div>

          {/* Bottom link */}
          <div className="relative z-10">
            <p className="text-xs" style={{ color:'rgba(255,255,255,0.3)' }}>
              Já tem conta?{' '}
              <Link href="/login" className="font-semibold" style={{ color:'rgba(255,107,61,0.8)' }}>
                Iniciar sessão
              </Link>
            </p>
          </div>
        </div>

        {/* ═══ RIGHT FORM PANEL ═══ */}
        <div className="reg-right flex-1 bg-white">
          <div className="max-w-xl mx-auto px-6 sm:px-10 py-10">

            {/* Mobile header */}
            <div className="lg:hidden flex items-center gap-2 mb-8">
              <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background:'#B32700' }}>
                <span className="material-symbols-outlined text-white" style={{ fontSize:16, fontVariationSettings:"'FILL' 1" }}>location_on</span>
              </div>
              <span className="font-bold text-gray-900">Pede Aqui</span>
            </div>

            {/* Step header + progress */}
            <div className="mb-7 pa-fadeup">
              <div className="flex items-center justify-between mb-1.5">
                <span className="text-xs font-semibold" style={{ color:'#B32700' }}>Passo {step} de 3</span>
                <span className="text-xs text-gray-400">{STEPS[step - 1].desc}</span>
              </div>
              <div className="prog-bar-track">
                <div className="prog-bar-fill" style={{ width:`${(step/3)*100}%` }} />
              </div>
            </div>

            {/* ─── STEP 1: Company info ─── */}
            {step === 1 && (
              <div className="pa-fadeup space-y-5">
                <h2 className="text-xl font-bold text-gray-900 mb-1">Informação da Empresa</h2>
                <p className="text-sm text-gray-500 mb-6">Preencha os dados da sua empresa para criar o perfil de parceiro.</p>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {/* Company name */}
                  <div className="sm:col-span-2">
                    <label className="field-label">Nome da Empresa <span style={{ color:'#B32700' }}>*</span></label>
                    <input type="text" value={form.companyName} onChange={e => updateForm('companyName', e.target.value)}
                      placeholder="Ex: Sabores do Sul Lda" className={`${inputCls} ${errors.companyName ? 'has-error' : ''}`} />
                    {errors.companyName && <p className="field-error"><span className="material-symbols-outlined" style={{fontSize:12,fontVariationSettings:"'FILL' 1"}}>error</span>{errors.companyName}</p>}
                  </div>

                  {/* Slug */}
                  <div className="sm:col-span-2">
                    <label className="field-label">Slug da Empresa <span style={{ color:'#B32700' }}>*</span></label>
                    <input type="text" value={form.companySlug} onChange={e => updateForm('companySlug', e.target.value)}
                      placeholder="sabores-do-sul" className={`${inputCls} ${errors.companySlug ? 'has-error' : ''}`} />
                    <p className="field-hint">Identificador único da empresa (gerado automaticamente)</p>
                    {errors.companySlug && <p className="field-error"><span className="material-symbols-outlined" style={{fontSize:12,fontVariationSettings:"'FILL' 1"}}>error</span>{errors.companySlug}</p>}
                  </div>

                  {/* Legal name */}
                  <div>
                    <label className="field-label">Nome Legal</label>
                    <input type="text" value={form.legalName} onChange={e => updateForm('legalName', e.target.value)}
                      placeholder="Sabores do Sul, Lda" className={`${inputCls} ${errors.legalName ? 'has-error' : ''}`} />
                    {errors.legalName && <p className="field-error">{errors.legalName}</p>}
                  </div>

                  {/* Tax number */}
                  <div>
                    <label className="field-label">NUIT / VAT</label>
                    <input type="text" value={form.taxNumber} onChange={e => updateForm('taxNumber', e.target.value)}
                      placeholder="400123456" className={`${inputCls} ${errors.taxNumber ? 'has-error' : ''}`} />
                    {errors.taxNumber && <p className="field-error">{errors.taxNumber}</p>}
                  </div>

                  {/* Business type */}
                  <div>
                    <label className="field-label">Tipo de Negócio</label>
                    <div style={{ position:'relative' }}>
                      <select value={form.businessType} onChange={e => updateForm('businessType', e.target.value)}
                        className={`${inputCls} ${errors.businessType ? 'has-error' : ''}`} style={{ paddingRight:36 }}>
                        <option value="">Selecione…</option>
                        <option value="Restaurant">Restaurante</option>
                        <option value="Grocery">Mercearia</option>
                        <option value="Pharmacy">Farmácia</option>
                        <option value="Other">Outro</option>
                      </select>
                      <span className="material-symbols-outlined" style={{ position:'absolute',right:12,top:'50%',transform:'translateY(-50%)',fontSize:16,color:'#9CA3AF',pointerEvents:'none' }}>expand_more</span>
                    </div>
                    {errors.businessType && <p className="field-error">{errors.businessType}</p>}
                  </div>

                  {/* Industry */}
                  <div>
                    <label className="field-label">Indústria</label>
                    <input type="text" value={form.industry} onChange={e => updateForm('industry', e.target.value)}
                      placeholder="Ex: Alimentação" className={`${inputCls} ${errors.industry ? 'has-error' : ''}`} />
                    {errors.industry && <p className="field-error">{errors.industry}</p>}
                  </div>

                  {/* Country */}
                  <div>
                    <label className="field-label">País <span style={{ color:'#B32700' }}>*</span></label>
                    <input type="text" value={form.country} onChange={e => updateForm('country', e.target.value)}
                      placeholder="Moçambique" className={`${inputCls} ${errors.country ? 'has-error' : ''}`} />
                    {errors.country && <p className="field-error"><span className="material-symbols-outlined" style={{fontSize:12,fontVariationSettings:"'FILL' 1"}}>error</span>{errors.country}</p>}
                  </div>

                  {/* City */}
                  <div>
                    <label className="field-label">Cidade</label>
                    <input type="text" value={form.city} onChange={e => updateForm('city', e.target.value)}
                      placeholder="Maputo" className={`${inputCls} ${errors.city ? 'has-error' : ''}`} />
                    {errors.city && <p className="field-error">{errors.city}</p>}
                  </div>

                  {/* Address */}
                  <div className="sm:col-span-2">
                    <label className="field-label">Endereço</label>
                    <input type="text" value={form.address} onChange={e => updateForm('address', e.target.value)}
                      placeholder="Av. Julius Nyerere, nº 123" className={`${inputCls} ${errors.address ? 'has-error' : ''}`} />
                    {errors.address && <p className="field-error">{errors.address}</p>}
                  </div>

                  {/* Currency */}
                  <div>
                    <label className="field-label">Moeda Padrão <span style={{ color:'#B32700' }}>*</span></label>
                    <div style={{ position:'relative' }}>
                      <select value={form.defaultCurrency} onChange={e => updateForm('defaultCurrency', e.target.value)}
                        className={`${inputCls} ${errors.defaultCurrency ? 'has-error' : ''}`} style={{ paddingRight:36 }}>
                        <option value="">Selecione…</option>
                        <option value="MZN">MZN — Metical</option>
                        <option value="USD">USD — Dólar</option>
                        <option value="EUR">EUR — Euro</option>
                        <option value="ZAR">ZAR — Rand</option>
                      </select>
                      <span className="material-symbols-outlined" style={{ position:'absolute',right:12,top:'50%',transform:'translateY(-50%)',fontSize:16,color:'#9CA3AF',pointerEvents:'none' }}>expand_more</span>
                    </div>
                    {errors.defaultCurrency && <p className="field-error"><span className="material-symbols-outlined" style={{fontSize:12,fontVariationSettings:"'FILL' 1"}}>error</span>{errors.defaultCurrency}</p>}
                  </div>

                  {/* Company phone */}
                  <div>
                    <label className="field-label">Telefone da Empresa</label>
                    <input type="tel" value={form.companyPhone} onChange={e => updateForm('companyPhone', e.target.value)}
                      placeholder="+258 21 000 000" className={`${inputCls} ${errors.companyPhone ? 'has-error' : ''}`} />
                    {errors.companyPhone && <p className="field-error">{errors.companyPhone}</p>}
                  </div>

                  {/* Company email */}
                  <div className="sm:col-span-2">
                    <label className="field-label">Email da Empresa</label>
                    <input type="email" value={form.companyEmail} onChange={e => updateForm('companyEmail', e.target.value)}
                      placeholder="geral@empresa.co.mz" className={`${inputCls} ${errors.companyEmail ? 'has-error' : ''}`} />
                    {errors.companyEmail && <p className="field-error">{errors.companyEmail}</p>}
                  </div>
                </div>

                <button type="button" onClick={handleNext} className="btn-primary-reg mt-2">
                  Seguinte
                  <span className="material-symbols-outlined" style={{ fontSize:16 }}>arrow_forward</span>
                </button>
              </div>
            )}

            {/* ─── STEP 2: Owner account ─── */}
            {step === 2 && (
              <div className="pa-fadeup space-y-5">
                <h2 className="text-xl font-bold text-gray-900 mb-1">Conta do Proprietário</h2>
                <p className="text-sm text-gray-500 mb-6">Crie o acesso de administrador para a sua conta.</p>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="field-label">Primeiro Nome <span style={{ color:'#B32700' }}>*</span></label>
                    <input type="text" value={form.firstName} onChange={e => updateForm('firstName', e.target.value)}
                      placeholder="João" className={`${inputCls} ${errors.firstName ? 'has-error' : ''}`} />
                    {errors.firstName && <p className="field-error"><span className="material-symbols-outlined" style={{fontSize:12,fontVariationSettings:"'FILL' 1"}}>error</span>{errors.firstName}</p>}
                  </div>

                  <div>
                    <label className="field-label">Último Nome <span style={{ color:'#B32700' }}>*</span></label>
                    <input type="text" value={form.lastName} onChange={e => updateForm('lastName', e.target.value)}
                      placeholder="Silva" className={`${inputCls} ${errors.lastName ? 'has-error' : ''}`} />
                    {errors.lastName && <p className="field-error"><span className="material-symbols-outlined" style={{fontSize:12,fontVariationSettings:"'FILL' 1"}}>error</span>{errors.lastName}</p>}
                  </div>

                  <div className="sm:col-span-2">
                    <label className="field-label">Email <span style={{ color:'#B32700' }}>*</span></label>
                    <input type="email" value={form.email} onChange={e => updateForm('email', e.target.value)}
                      placeholder="joao@empresa.co.mz" className={`${inputCls} ${errors.email ? 'has-error' : ''}`} autoComplete="email" />
                    {errors.email && <p className="field-error"><span className="material-symbols-outlined" style={{fontSize:12,fontVariationSettings:"'FILL' 1"}}>error</span>{errors.email}</p>}
                  </div>

                  <div className="sm:col-span-2">
                    <label className="field-label">Telefone</label>
                    <input type="tel" value={form.phone} onChange={e => updateForm('phone', e.target.value)}
                      placeholder="+258 84 000 0000" className={`${inputCls} ${errors.phone ? 'has-error' : ''}`} />
                    {errors.phone && <p className="field-error">{errors.phone}</p>}
                  </div>

                  <div>
                    <label className="field-label">Palavra-passe <span style={{ color:'#B32700' }}>*</span></label>
                    <div className="pw-wrap">
                      <input type={showPassword ? 'text' : 'password'} value={form.password}
                        onChange={e => updateForm('password', e.target.value)}
                        placeholder="Mínimo 8 caracteres"
                        className={`${inputCls} pw-input ${errors.password ? 'has-error' : ''}`}
                        autoComplete="new-password" />
                      <button type="button" className="pw-eye" onClick={() => setShowPassword(v => !v)} tabIndex={-1}>
                        <span className="material-symbols-outlined" style={{ fontSize:18 }}>
                          {showPassword ? 'visibility_off' : 'visibility'}
                        </span>
                      </button>
                    </div>
                    {errors.password && <p className="field-error"><span className="material-symbols-outlined" style={{fontSize:12,fontVariationSettings:"'FILL' 1"}}>error</span>{errors.password}</p>}
                  </div>

                  <div>
                    <label className="field-label">Confirmar Palavra-passe <span style={{ color:'#B32700' }}>*</span></label>
                    <div className="pw-wrap">
                      <input type={showConfirm ? 'text' : 'password'} value={form.confirmPassword}
                        onChange={e => updateForm('confirmPassword', e.target.value)}
                        placeholder="Repita a palavra-passe"
                        className={`${inputCls} pw-input ${errors.confirmPassword ? 'has-error' : ''}`}
                        autoComplete="new-password" />
                      <button type="button" className="pw-eye" onClick={() => setShowConfirm(v => !v)} tabIndex={-1}>
                        <span className="material-symbols-outlined" style={{ fontSize:18 }}>
                          {showConfirm ? 'visibility_off' : 'visibility'}
                        </span>
                      </button>
                    </div>
                    {errors.confirmPassword && <p className="field-error"><span className="material-symbols-outlined" style={{fontSize:12,fontVariationSettings:"'FILL' 1"}}>error</span>{errors.confirmPassword}</p>}
                  </div>
                </div>

                <div className="flex gap-3 mt-2">
                  <button type="button" onClick={handleBack} className="btn-ghost-reg">
                    <span className="material-symbols-outlined" style={{ fontSize:16 }}>arrow_back</span>
                    Anterior
                  </button>
                  <button type="button" onClick={handleNext} className="btn-primary-reg">
                    Seguinte
                    <span className="material-symbols-outlined" style={{ fontSize:16 }}>arrow_forward</span>
                  </button>
                </div>
              </div>
            )}

            {/* ─── STEP 3: Review ─── */}
            {step === 3 && (
              <div className="pa-fadeup space-y-6">
                <h2 className="text-xl font-bold text-gray-900 mb-1">Revisão e Confirmação</h2>
                <p className="text-sm text-gray-500 mb-4">Verifique os dados antes de submeter o registo.</p>

                {/* Summary card */}
                <div className="rounded-xl p-5 space-y-1" style={{ background:'#F9FAFB', border:'1px solid #E5E7EB' }}>
                  <p className="text-xs font-semibold tracking-widest uppercase mb-3" style={{ color:'#B32700' }}>
                    Empresa
                  </p>
                  <dl className="review-row">
                    {form.companyName && <><dt className="review-key">Nome</dt><dd className="review-val">{form.companyName}</dd></>}
                    {form.companySlug && <><dt className="review-key">Slug</dt><dd className="review-val">{form.companySlug}</dd></>}
                    {form.legalName && <><dt className="review-key">Nome legal</dt><dd className="review-val">{form.legalName}</dd></>}
                    {form.taxNumber && <><dt className="review-key">NUIT</dt><dd className="review-val">{form.taxNumber}</dd></>}
                    {form.businessType && <><dt className="review-key">Tipo</dt><dd className="review-val">{form.businessType}</dd></>}
                    {form.country && <><dt className="review-key">País</dt><dd className="review-val">{form.country}{form.city ? `, ${form.city}` : ''}</dd></>}
                    {form.defaultCurrency && <><dt className="review-key">Moeda</dt><dd className="review-val">{form.defaultCurrency}</dd></>}
                    {form.companyEmail && <><dt className="review-key">Email empresa</dt><dd className="review-val">{form.companyEmail}</dd></>}
                  </dl>

                  <div className="border-t my-3" style={{ borderColor:'#E5E7EB' }} />
                  <p className="text-xs font-semibold tracking-widest uppercase mb-3" style={{ color:'#B32700' }}>
                    Proprietário
                  </p>
                  <dl className="review-row">
                    {(form.firstName || form.lastName) && <><dt className="review-key">Nome</dt><dd className="review-val">{form.firstName} {form.lastName}</dd></>}
                    {form.email && <><dt className="review-key">Email</dt><dd className="review-val">{form.email}</dd></>}
                    {form.phone && <><dt className="review-key">Telefone</dt><dd className="review-val">{form.phone}</dd></>}
                    {form.password && <><dt className="review-key">Palavra-passe</dt><dd className="review-val">••••••••</dd></>}
                  </dl>
                </div>

                {/* Optional codes */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="field-label">Código de Referência</label>
                    <input type="text" value={form.referralCode} onChange={e => updateForm('referralCode', e.target.value)}
                      placeholder="REF-XXXXX" className={inputCls} />
                  </div>
                  <div>
                    <label className="field-label">Código Promocional</label>
                    <input type="text" value={form.promoCode} onChange={e => updateForm('promoCode', e.target.value)}
                      placeholder="PROMO-XXXXX" className={inputCls} />
                  </div>
                </div>

                {/* Terms notice */}
                <p className="text-xs text-gray-400 leading-relaxed">
                  Ao submeter, concorda com os{' '}
                  <span className="font-semibold" style={{ color:'#B32700' }}>Termos de Serviço</span>{' '}
                  e a <span className="font-semibold" style={{ color:'#B32700' }}>Política de Privacidade</span> da Pede Aqui.
                </p>

                {/* Global error */}
                {globalError && (
                  <div className="pa-shake flex items-start gap-2.5 rounded-xl px-4 py-3"
                       style={{ background:'#FEF2F2', border:'1px solid #FECACA' }}>
                    <span className="material-symbols-outlined text-red-500 shrink-0 mt-0.5" style={{ fontSize:16, fontVariationSettings:"'FILL' 1" }}>error</span>
                    <p className="text-sm text-red-600">{globalError}</p>
                  </div>
                )}

                {/* Actions */}
                <div className="flex gap-3">
                  <button type="button" onClick={handleBack} disabled={loading} className="btn-ghost-reg">
                    <span className="material-symbols-outlined" style={{ fontSize:16 }}>arrow_back</span>
                    Anterior
                  </button>
                  <button type="button" onClick={handleSubmit} disabled={loading} className="btn-primary-reg">
                    {loading ? (
                      <>
                        <svg className="pa-spinner w-4 h-4" viewBox="0 0 24 24" fill="none">
                          <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" strokeOpacity="0.3" />
                          <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
                        </svg>
                        A registar…
                      </>
                    ) : (
                      <>
                        Criar Conta
                        <span className="material-symbols-outlined" style={{ fontSize:16 }}>check</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}

            {/* Bottom link (mobile) */}
            <p className="lg:hidden mt-8 text-center text-xs text-gray-400">
              Já tem conta?{' '}
              <Link href="/login" className="font-semibold" style={{ color:'#B32700' }}>
                Iniciar sessão
              </Link>
            </p>
          </div>
        </div>
      </div>
    </>
  );
}
