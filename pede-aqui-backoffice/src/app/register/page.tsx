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
  const step1Fields = [
    'companyName', 'companySlug', 'legalName', 'taxNumber', 'businessType',
    'industry', 'country', 'city', 'address', 'defaultCurrency', 'companyPhone', 'companyEmail'
  ];
  const step2Fields = [
    'firstName', 'lastName', 'email', 'phone', 'password'
  ];
  
  if (step1Fields.includes(fieldName)) return 1;
  if (step2Fields.includes(fieldName)) return 2;
  return 3; // step 3 for referralCode, promoCode, or any other fields
};

export default function RegisterPage() {
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [form, setForm] = useState<FormData>({
    companyName: '',
    companySlug: '',
    legalName: '',
    taxNumber: '',
    businessType: '',
    industry: '',
    country: '',
    city: '',
    address: '',
    defaultCurrency: '',
    companyPhone: '',
    companyEmail: '',
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
    referralCode: '',
    promoCode: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [globalError, setGlobalError] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const updateForm = (field: keyof FormData, value: string) => {
    setForm(prev => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: '' }));
    }
    if (field === 'companyName') {
      setForm(prev => ({ ...prev, companySlug: toSlug(value) }));
    }
  };

  const validateStep1 = () => {
    const newErrors: Record<string, string> = {};
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    
    if (!form.companyName.trim()) newErrors.companyName = 'Nome da empresa é obrigatório';
    if (!form.companySlug.trim()) newErrors.companySlug = 'Slug da empresa é obrigatório';
    if (form.companySlug && !/^[a-z0-9-]+$/.test(form.companySlug)) {
      newErrors.companySlug = 'Slug deve conter apenas letras minúsculas, números e hífens';
    }
    if (!form.country.trim()) newErrors.country = 'País é obrigatório';
    if (!form.defaultCurrency.trim()) newErrors.defaultCurrency = 'Moeda padrão é obrigatória';
    if (form.companyEmail && !emailRegex.test(form.companyEmail)) {
      newErrors.companyEmail = 'Email da empresa deve ter um formato válido';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const validateStep2 = () => {
    const newErrors: Record<string, string> = {};
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    
    if (!form.firstName.trim()) newErrors.firstName = 'Primeiro nome é obrigatório';
    if (!form.lastName.trim()) newErrors.lastName = 'Último nome é obrigatório';
    if (!form.email.trim()) newErrors.email = 'Email é obrigatório';
    if (form.email && !emailRegex.test(form.email)) {
      newErrors.email = 'Email deve ter um formato válido';
    }
    if (!form.password.trim()) newErrors.password = 'Palavra-passe é obrigatória';
    if (form.password && form.password.length < 8) {
      newErrors.password = 'Palavra-passe deve ter pelo menos 8 caracteres';
    }
    if (!form.confirmPassword.trim()) newErrors.confirmPassword = 'Confirmação de palavra-passe é obrigatória';
    if (form.password !== form.confirmPassword) {
      newErrors.confirmPassword = 'Palavras-passe não coincidem';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleNext = () => {
    if (step === 1 && validateStep1()) {
      setStep(2);
    } else if (step === 2 && validateStep2()) {
      setStep(3);
    }
  };

  const handleBack = () => {
    if (step > 1) {
      setStep((step - 1) as 1 | 2 | 3);
      setGlobalError('');
    }
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
        setErrors({ companySlug: 'Este slug já está em uso. Escolha outro.' });
        setStep(1);
      } else if (error.code === 'email_exists') {
        setErrors({ email: 'Este email já está registado.' });
        setStep(2);
      } else if (error.code === 'validation_failed' && error.fieldErrors) {
        const fieldErrors: Record<string, string> = {};
        let firstFailingStep = 3; // Default to step 3
        
        error.fieldErrors.forEach((fe: any) => {
          fieldErrors[fe.field] = fe.message;
          const fieldStep = getStepForField(fe.field);
          if (fieldStep < firstFailingStep) {
            firstFailingStep = fieldStep;
          }
        });
        
        setErrors(fieldErrors);
        setStep(firstFailingStep);
      } else {
        setGlobalError(error.message || 'Erro inesperado. Tente novamente.');
      }
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <div className="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
        <div className="sm:mx-auto sm:w-full sm:max-w-md">
          <h1 className="text-center text-3xl font-extrabold text-gray-900">
            Pede Aqui Backoffice
          </h1>
          <p className="mt-2 text-center text-sm text-gray-600">
            Registe a sua empresa
          </p>
        </div>

        <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-lg">
          <div className="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10 text-center">
            <div className="text-green-600 text-4xl mb-4">✅</div>
            <h2 className="text-xl font-semibold text-gray-900 mb-2">
              Conta criada com sucesso!
            </h2>
            <p className="text-sm text-gray-600 mb-6">
              Pode agora iniciar sessão com o seu email e palavra-passe.
            </p>
            <Link
              href="/login"
              className="inline-flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
            >
              Ir para Login
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <h1 className="text-center text-3xl font-extrabold text-gray-900">
          Pede Aqui Backoffice
        </h1>
        <p className="mt-2 text-center text-sm text-gray-600">
          Registe a sua empresa
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-lg">
        <div className="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10">
          <div className="mb-6">
            <p className="text-xs font-medium text-indigo-600 mb-3">Passo {step} de 3</p>
            <div className="flex space-x-2">
              <div className={`h-2 flex-1 rounded ${step >= 1 ? 'bg-indigo-600' : 'bg-gray-200'}`}></div>
              <div className={`h-2 flex-1 rounded ${step >= 2 ? 'bg-indigo-600' : 'bg-gray-200'}`}></div>
              <div className={`h-2 flex-1 rounded ${step >= 3 ? 'bg-indigo-600' : 'bg-gray-200'}`}></div>
            </div>
          </div>

          {step === 1 && (
            <div className="space-y-6">
              <h2 className="text-lg font-medium text-gray-900">Informação da Empresa</h2>
              
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Nome da Empresa *
                </label>
                <input
                  type="text"
                  value={form.companyName}
                  onChange={(e) => updateForm('companyName', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.companyName && (
                  <p className="text-xs text-red-600 mt-1">{errors.companyName}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Slug da Empresa *
                </label>
                <input
                  type="text"
                  value={form.companySlug}
                  onChange={(e) => updateForm('companySlug', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                <p className="text-xs text-gray-500 mt-1">
                  Este slug será usado como identificador único da sua empresa.
                </p>
                {errors.companySlug && (
                  <p className="text-xs text-red-600 mt-1">{errors.companySlug}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Nome Legal
                </label>
                <input
                  type="text"
                  value={form.legalName}
                  onChange={(e) => updateForm('legalName', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.legalName && (
                  <p className="text-xs text-red-600 mt-1">{errors.legalName}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  NIF / VAT
                </label>
                <input
                  type="text"
                  value={form.taxNumber}
                  onChange={(e) => updateForm('taxNumber', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.taxNumber && (
                  <p className="text-xs text-red-600 mt-1">{errors.taxNumber}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Tipo de Negócio
                </label>
                <select
                  value={form.businessType}
                  onChange={(e) => updateForm('businessType', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                >
                  <option value="">Selecione...</option>
                  <option value="Restaurant">Restaurant</option>
                  <option value="Grocery">Grocery</option>
                  <option value="Pharmacy">Pharmacy</option>
                  <option value="Other">Other</option>
                </select>
                {errors.businessType && (
                  <p className="text-xs text-red-600 mt-1">{errors.businessType}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Indústria
                </label>
                <input
                  type="text"
                  value={form.industry}
                  onChange={(e) => updateForm('industry', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.industry && (
                  <p className="text-xs text-red-600 mt-1">{errors.industry}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  País *
                </label>
                <input
                  type="text"
                  value={form.country}
                  onChange={(e) => updateForm('country', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.country && (
                  <p className="text-xs text-red-600 mt-1">{errors.country}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Cidade
                </label>
                <input
                  type="text"
                  value={form.city}
                  onChange={(e) => updateForm('city', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.city && (
                  <p className="text-xs text-red-600 mt-1">{errors.city}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Endereço
                </label>
                <input
                  type="text"
                  value={form.address}
                  onChange={(e) => updateForm('address', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.address && (
                  <p className="text-xs text-red-600 mt-1">{errors.address}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Moeda Padrão *
                </label>
                <select
                  value={form.defaultCurrency}
                  onChange={(e) => updateForm('defaultCurrency', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                >
                  <option value="">Selecione...</option>
                  <option value="MZN">MZN</option>
                  <option value="USD">USD</option>
                  <option value="EUR">EUR</option>
                  <option value="ZAR">ZAR</option>
                </select>
                {errors.defaultCurrency && (
                  <p className="text-xs text-red-600 mt-1">{errors.defaultCurrency}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Telefone da Empresa
                </label>
                <input
                  type="tel"
                  value={form.companyPhone}
                  onChange={(e) => updateForm('companyPhone', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.companyPhone && (
                  <p className="text-xs text-red-600 mt-1">{errors.companyPhone}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Email da Empresa
                </label>
                <input
                  type="email"
                  value={form.companyEmail}
                  onChange={(e) => updateForm('companyEmail', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.companyEmail && (
                  <p className="text-xs text-red-600 mt-1">{errors.companyEmail}</p>
                )}
              </div>

              <button
                type="button"
                onClick={handleNext}
                className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
              >
                Seguinte →
              </button>
            </div>
          )}

          {step === 2 && (
            <div className="space-y-6">
              <h2 className="text-lg font-medium text-gray-900">Conta do Proprietário</h2>
              
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Primeiro Nome *
                </label>
                <input
                  type="text"
                  value={form.firstName}
                  onChange={(e) => updateForm('firstName', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.firstName && (
                  <p className="text-xs text-red-600 mt-1">{errors.firstName}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Último Nome *
                </label>
                <input
                  type="text"
                  value={form.lastName}
                  onChange={(e) => updateForm('lastName', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.lastName && (
                  <p className="text-xs text-red-600 mt-1">{errors.lastName}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Email *
                </label>
                <input
                  type="email"
                  value={form.email}
                  onChange={(e) => updateForm('email', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.email && (
                  <p className="text-xs text-red-600 mt-1">{errors.email}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Telefone
                </label>
                <input
                  type="tel"
                  value={form.phone}
                  onChange={(e) => updateForm('phone', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.phone && (
                  <p className="text-xs text-red-600 mt-1">{errors.phone}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Palavra-passe *
                </label>
                <input
                  type="password"
                  value={form.password}
                  onChange={(e) => updateForm('password', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.password && (
                  <p className="text-xs text-red-600 mt-1">{errors.password}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Confirmar Palavra-passe *
                </label>
                <input
                  type="password"
                  value={form.confirmPassword}
                  onChange={(e) => updateForm('confirmPassword', e.target.value)}
                  className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
                {errors.confirmPassword && (
                  <p className="text-xs text-red-600 mt-1">{errors.confirmPassword}</p>
                )}
              </div>

              <div className="flex space-x-4">
                <button
                  type="button"
                  onClick={handleBack}
                  className="flex-1 flex justify-center py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                >
                  ← Anterior
                </button>
                <button
                  type="button"
                  onClick={handleNext}
                  className="flex-1 flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                >
                  Seguinte →
                </button>
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="space-y-6">
              <h2 className="text-lg font-medium text-gray-900">Revisão & Confirmação</h2>
              
              <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                {form.companyName && (
                  <>
                    <dt className="text-gray-500">Nome da Empresa:</dt>
                    <dd className="text-gray-900 font-medium">{form.companyName}</dd>
                  </>
                )}
                {form.companySlug && (
                  <>
                    <dt className="text-gray-500">Slug:</dt>
                    <dd className="text-gray-900 font-medium">{form.companySlug}</dd>
                  </>
                )}
                {form.legalName && (
                  <>
                    <dt className="text-gray-500">Nome Legal:</dt>
                    <dd className="text-gray-900 font-medium">{form.legalName}</dd>
                  </>
                )}
                {form.taxNumber && (
                  <>
                    <dt className="text-gray-500">NIF:</dt>
                    <dd className="text-gray-900 font-medium">{form.taxNumber}</dd>
                  </>
                )}
                {form.businessType && (
                  <>
                    <dt className="text-gray-500">Tipo de Negócio:</dt>
                    <dd className="text-gray-900 font-medium">{form.businessType}</dd>
                  </>
                )}
                {form.industry && (
                  <>
                    <dt className="text-gray-500">Indústria:</dt>
                    <dd className="text-gray-900 font-medium">{form.industry}</dd>
                  </>
                )}
                {form.country && (
                  <>
                    <dt className="text-gray-500">País:</dt>
                    <dd className="text-gray-900 font-medium">{form.country}</dd>
                  </>
                )}
                {form.city && (
                  <>
                    <dt className="text-gray-500">Cidade:</dt>
                    <dd className="text-gray-900 font-medium">{form.city}</dd>
                  </>
                )}
                {form.address && (
                  <>
                    <dt className="text-gray-500">Endereço:</dt>
                    <dd className="text-gray-900 font-medium">{form.address}</dd>
                  </>
                )}
                {form.defaultCurrency && (
                  <>
                    <dt className="text-gray-500">Moeda:</dt>
                    <dd className="text-gray-900 font-medium">{form.defaultCurrency}</dd>
                  </>
                )}
                {form.companyPhone && (
                  <>
                    <dt className="text-gray-500">Telefone da Empresa:</dt>
                    <dd className="text-gray-900 font-medium">{form.companyPhone}</dd>
                  </>
                )}
                {form.companyEmail && (
                  <>
                    <dt className="text-gray-500">Email da Empresa:</dt>
                    <dd className="text-gray-900 font-medium">{form.companyEmail}</dd>
                  </>
                )}
                {form.firstName && (
                  <>
                    <dt className="text-gray-500">Primeiro Nome:</dt>
                    <dd className="text-gray-900 font-medium">{form.firstName}</dd>
                  </>
                )}
                {form.lastName && (
                  <>
                    <dt className="text-gray-500">Último Nome:</dt>
                    <dd className="text-gray-900 font-medium">{form.lastName}</dd>
                  </>
                )}
                {form.email && (
                  <>
                    <dt className="text-gray-500">Email:</dt>
                    <dd className="text-gray-900 font-medium">{form.email}</dd>
                  </>
                )}
                {form.phone && (
                  <>
                    <dt className="text-gray-500">Telefone:</dt>
                    <dd className="text-gray-900 font-medium">{form.phone}</dd>
                  </>
                )}
                {form.password && (
                  <>
                    <dt className="text-gray-500">Palavra-passe:</dt>
                    <dd className="text-gray-900 font-medium">••••••••</dd>
                  </>
                )}
              </div>

              <div className="space-y-4 border-t pt-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700">
                    Código de Referência
                  </label>
                  <input
                    type="text"
                    value={form.referralCode}
                    onChange={(e) => updateForm('referralCode', e.target.value)}
                    className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">
                    Código Promocional
                  </label>
                  <input
                    type="text"
                    value={form.promoCode}
                    onChange={(e) => updateForm('promoCode', e.target.value)}
                    className="mt-1 appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                  />
                </div>
              </div>

              {globalError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-md p-3">
                  {globalError}
                </div>
              )}

              <div className="flex space-x-4">
                <button
                  type="button"
                  onClick={handleBack}
                  disabled={loading}
                  className="flex-1 flex justify-center py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
                >
                  ← Anterior
                </button>
                <button
                  type="button"
                  onClick={handleSubmit}
                  disabled={loading}
                  className="flex-1 flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
                >
                  {loading ? 'A registar...' : 'Registar'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}