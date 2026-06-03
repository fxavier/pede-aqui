"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { TableSkeleton } from "@/components/ui/loading-skeleton";
import { ErrorState } from "@/components/ui/error-state";
import { EmptyState } from "@/components/ui/empty-state";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { courierService, uploadService, userService } from "@/lib/api/services";
import { formatDate, cn } from "@/lib/utils";
import type { Courier, CourierDocument, UserProfile } from "@/lib/api/types";
import { Upload, FileText, Trash2, User, Phone, Car } from "lucide-react";

type CourierRecord = {
  id: string;
  nome: string;
  estado: string;
  detalhe: string;
};

type CourierFormData = {
  id: string;
  fullName: string;
  phone: string;
  nif: string;
  vehicleType: string;
  vehiclePlate: string;
  dateOfBirth: string;
  userProfileId: string;
  operatingZoneId?: string;
  documents: CourierDocument[];
};


export default function CouriersPage() {
  const [couriers, setCouriers] = useState<CourierRecord[]>([]);
  const [users, setUsers] = useState<UserProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [usersLoading, setUsersLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [courierForm, setCourierForm] = useState<CourierFormData>({ 
    id: "", 
    fullName: "", 
    phone: "", 
    nif: "", 
    vehicleType: "", 
    vehiclePlate: "", 
    dateOfBirth: "", 
    userProfileId: "",
    documents: []
  });
  const [documentUploading, setDocumentUploading] = useState(false);
  const [selectedDocType, setSelectedDocType] = useState("ID_CARD");

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await courierService.list();
      setCouriers(data.map((courier) => ({
        id: courier.id,
        nome: courier.fullName || "-",
        estado: courier.available ? "Activo" : "Offline",
        detalhe: `Veiculo: ${courier.vehicleType || "-"}`,
      })));
    } catch (err) {
      setError("Erro ao carregar estafetas. Tente novamente.");
    } finally {
      setLoading(false);
    }
  };

  const fetchUsers = async () => {
    setUsersLoading(true);
    try {
      const data = await userService.list();
      setUsers(data);
    } catch (err) {
      console.error("Failed to load users:", err);
      setUsers([]);
    } finally {
      setUsersLoading(false);
    }
  };

  useEffect(() => { 
    fetchData();
    fetchUsers();
  }, []);

  const filteredCouriers = couriers.filter(courier => 
    courier.nome.toLowerCase().includes(searchQuery.toLowerCase()) ||
    courier.estado.toLowerCase().includes(searchQuery.toLowerCase()) ||
    courier.detalhe.toLowerCase().includes(searchQuery.toLowerCase())
  );

  function resetCourierForm() {
    setCourierForm({ 
      id: "", 
      fullName: "", 
      phone: "", 
      nif: "", 
      vehicleType: "", 
      vehiclePlate: "", 
      dateOfBirth: "", 
      userProfileId: "",
      documents: []
    });
  }

  async function handleDocumentUpload(file: File, documentType: string) {
    try {
      setDocumentUploading(true);
      const { uploadUrl, storageKey } = await uploadService.getDocumentPresignedUrl({
        purpose: "courier-document",
        fileName: file.name,
        contentType: file.type,
      });
      
      await uploadService.uploadToS3(uploadUrl, file);
      
      const newDocument: CourierDocument = {
        id: crypto.randomUUID(),
        courierId: courierForm.id,
        documentType,
        storageKey,
        status: "PENDING",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      
      setCourierForm(prev => ({ 
        ...prev, 
        documents: [...prev.documents, newDocument]
      }));
    } catch (error) {
      console.error("Document upload failed:", error);
    } finally {
      setDocumentUploading(false);
    }
  }

  function removeDocument(documentId: string) {
    setCourierForm(prev => ({
      ...prev,
      documents: prev.documents.filter(doc => doc.id !== documentId)
    }));
  }

  async function submitCourierForm(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!courierForm.fullName.trim() || !courierForm.userProfileId.trim()) {
      setError("Nome completo e perfil de utilizador são obrigatórios.");
      return;
    }

    try {
      const created = await courierService.create({
        userProfileId: courierForm.userProfileId,
        operatingZoneId: courierForm.operatingZoneId,
        fullName: courierForm.fullName,
        phone: courierForm.phone,
        nif: courierForm.nif,
        vehicleType: courierForm.vehicleType,
        vehiclePlate: courierForm.vehiclePlate,
        dateOfBirth: courierForm.dateOfBirth,
      });
      
      // Upload documents if any
      for (const doc of courierForm.documents) {
        await courierService.uploadDocument(created.id, {
          documentType: doc.documentType,
          storageKey: doc.storageKey,
        });
      }
      
      setCouriers((prev) => [{
        id: created.id,
        nome: created.fullName || "-",
        estado: created.available ? "Activo" : "Offline",
        detalhe: `Veiculo: ${created.vehicleType || "-"}`,
      }, ...prev]);
      setError(null);
    } catch (err) {
      console.error("Failed to create courier:", err);
      setError("Erro ao criar estafeta. Verificar se o perfil de utilizador existe.");
    }

    resetCourierForm();
  }

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div>
          <h1 className="font-headline-lg text-headline-lg text-on-surface">Estafetas</h1>
          <p className="mt-1 text-body-md text-on-surface-variant">
            Gestão de estafetas e documentação do sistema de entrega.
          </p>
        </div>

        {error && (
          <ErrorState
            message={error}
            onRetry={fetchData}
          />
        )}

        <section className="grid grid-cols-1 gap-5 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardHeader>
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <CardTitle>Lista de Estafetas</CardTitle>
                <Input
                  placeholder="Pesquisar estafetas..."
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                  className="sm:w-72"
                />
              </div>
            </CardHeader>
            <CardContent>
              {loading ? (
                <TableSkeleton />
              ) : filteredCouriers.length === 0 ? (
                <EmptyState message="Nenhum estafeta encontrado." />
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead>
                      <tr className="border-b border-outline-variant text-xs font-bold uppercase text-on-surface-variant">
                        <th className="pb-3 pr-4">Nome</th>
                        <th className="pb-3 pr-4">Estado</th>
                        <th className="pb-3 pr-4">Detalhe</th>
                        <th className="pb-3">Accoes</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredCouriers.map((courier) => (
                        <tr key={courier.id} className="border-b border-outline-variant/50 last:border-0">
                          <td className="py-3 pr-4 font-bold text-on-surface">{courier.nome}</td>
                          <td className="py-3 pr-4 text-on-surface-variant">{courier.estado}</td>
                          <td className="py-3 pr-4 text-on-surface-variant">{courier.detalhe}</td>
                          <td className="py-3">
                            <span className="text-xs text-on-surface-variant">
                              Edição não disponível
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Criar Estafeta</CardTitle>
            </CardHeader>
            <CardContent>
              <form className="space-y-4" onSubmit={submitCourierForm}>
                {/* User Profile Selection */}
                <div className="space-y-2">
                  <label className="text-sm font-bold text-on-surface flex items-center gap-2">
                    <User className="h-4 w-4" />
                    Perfil de Utilizador
                  </label>
                  <div className="space-y-1">
                    <select
                      value={courierForm.userProfileId}
                      onChange={(event) => setCourierForm((prev) => ({ ...prev, userProfileId: event.target.value }))}
                      className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-not-allowed disabled:opacity-50"
                      required
                      disabled={usersLoading}
                    >
                      <option value="">
                        {usersLoading ? "A carregar utilizadores..." : "Selecione um utilizador *"}
                      </option>
                      {users.map((user) => (
                        <option key={user.id} value={user.id}>
                          {user.displayName} ({user.email})
                        </option>
                      ))}
                    </select>
                    <p className="text-xs text-on-surface-variant">
                      Selecione o utilizador que será associado a este estafeta.
                    </p>
                  </div>
                </div>

                {/* Personal Information */}
                <div className="space-y-2">
                  <label className="text-sm font-bold text-on-surface">
                    Informações Pessoais
                  </label>
                  <div className="space-y-3">
                    <Input
                      placeholder="Nome completo *"
                      value={courierForm.fullName}
                      onChange={(event) => setCourierForm((prev) => ({ ...prev, fullName: event.target.value }))}
                      required
                    />
                    <Input
                      placeholder="NIF"
                      value={courierForm.nif}
                      onChange={(event) => setCourierForm((prev) => ({ ...prev, nif: event.target.value }))}
                    />
                    <Input
                      type="date"
                      placeholder="Data de nascimento"
                      value={courierForm.dateOfBirth}
                      onChange={(event) => setCourierForm((prev) => ({ ...prev, dateOfBirth: event.target.value }))}
                    />
                  </div>
                </div>

                {/* Contact Information */}
                <div className="space-y-2">
                  <label className="text-sm font-bold text-on-surface flex items-center gap-2">
                    <Phone className="h-4 w-4" />
                    Contacto
                  </label>
                  <Input
                    placeholder="Telefone"
                    value={courierForm.phone}
                    onChange={(event) => setCourierForm((prev) => ({ ...prev, phone: event.target.value }))}
                  />
                </div>

                {/* Vehicle Information */}
                <div className="space-y-2">
                  <label className="text-sm font-bold text-on-surface flex items-center gap-2">
                    <Car className="h-4 w-4" />
                    Informações do Veículo
                  </label>
                  <div className="space-y-3">
                    <select
                      value={courierForm.vehicleType}
                      onChange={(event) => setCourierForm((prev) => ({ ...prev, vehicleType: event.target.value }))}
                      className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <option value="">Tipo de veículo</option>
                      <option value="MOTORCYCLE">Motocicleta</option>
                      <option value="CAR">Carro</option>
                      <option value="BICYCLE">Bicicleta</option>
                      <option value="FOOT">A pé</option>
                    </select>
                    <Input
                      placeholder="Matrícula do veículo"
                      value={courierForm.vehiclePlate}
                      onChange={(event) => setCourierForm((prev) => ({ ...prev, vehiclePlate: event.target.value }))}
                    />
                  </div>
                </div>

                {/* Document Upload */}
                <div className="space-y-3">
                  <label className="text-sm font-bold text-on-surface">Documentos</label>
                  
                  {/* Document Upload Controls */}
                  <div className="space-y-3">
                    <select
                      value={selectedDocType}
                      onChange={(e) => setSelectedDocType(e.target.value)}
                      className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <option value="ID_CARD">Bilhete de Identidade</option>
                      <option value="DRIVING_LICENCE">Carta de Condução</option>
                      <option value="VEHICLE_REGISTRATION">Registo do Veículo</option>
                      <option value="OTHER">Outro</option>
                    </select>
                    
                    <div className="flex gap-2">
                      <input
                        type="file"
                        accept=".pdf,.jpeg,.jpg,.png,.webp"
                        id="courier-document-upload"
                        className="hidden"
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          if (file) handleDocumentUpload(file, selectedDocType);
                        }}
                      />
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => document.getElementById('courier-document-upload')?.click()}
                        disabled={documentUploading}
                      >
                        {documentUploading ? (
                          <>Enviando...</>
                        ) : (
                          <>
                            <FileText className="h-4 w-4 mr-2" />
                            Adicionar Documento
                          </>
                        )}
                      </Button>
                    </div>
                  </div>

                  {/* Document List */}
                  {courierForm.documents.length > 0 && (
                    <div className="space-y-2">
                      {courierForm.documents.map((doc) => (
                        <div key={doc.id} className="flex items-center justify-between p-3 border border-outline-variant rounded-lg">
                          <div className="flex items-center gap-2">
                            <FileText className="h-4 w-4 text-on-surface-variant" />
                            <span className="text-sm text-on-surface">{doc.documentType}</span>
                          </div>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => removeDocument(doc.id)}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div className="flex gap-2">
                  <Button type="submit" className="flex-1">Criar Estafeta</Button>
                  <Button type="button" variant="outline" onClick={resetCourierForm}>Limpar</Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </section>
      </main>
    </AppShell>
  );
}
