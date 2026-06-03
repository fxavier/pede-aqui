"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { TableSkeleton } from "@/components/ui/loading-skeleton";
import { ErrorState } from "@/components/ui/error-state";
import { EmptyState } from "@/components/ui/empty-state";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { userService, uploadService } from "@/lib/api/services";
import { formatDate, cn } from "@/lib/utils";
import type { UserProfile } from "@/lib/api/types";
import { Upload, Image, Trash2, User, Mail, Phone, Shield } from "lucide-react";

type UserRecord = {
  id: string;
  nome: string;
  email: string;
  estado: string;
  cargo: string;
};

type UserFormData = {
  id: string;
  keycloakUserId: string;
  email: string;
  displayName: string;
  fullName: string;
  phone: string;
  nif: string;
  dateOfBirth: string;
  address: string;
  roles: string[];
  status: string;
  avatarStorageKey?: string;
  avatarPreview?: string;
};


const roles = ["CUSTOMER", "VENDOR_ADMIN", "VENDOR_STAFF", "COURIER", "OPERATIONS", "FINANCE", "SUPPORT", "ADMIN"];

export default function UsersPage() {
  const [users, setUsers] = useState<UserRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [userFormMode, setUserFormMode] = useState<"create" | "edit">("create");
  const [userForm, setUserForm] = useState<UserFormData>({ 
    id: "", 
    keycloakUserId: "",
    email: "",
    displayName: "", 
    fullName: "", 
    phone: "", 
    nif: "", 
    dateOfBirth: "", 
    address: "",
    roles: [],
    status: "ACTIVE"
  });
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [loadingUserEdit, setLoadingUserEdit] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await userService.list();
      setUsers(data.map((user) => ({
        id: user.id,
        nome: user.displayName,
        email: user.email,
        estado: user.status,
        cargo: user.roles.join(", "),
      })));
    } catch (error) {
      setError("Erro ao carregar utilizadores. Tente novamente.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const filteredUsers = users.filter(user => 
    user.nome.toLowerCase().includes(searchQuery.toLowerCase()) ||
    user.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
    user.estado.toLowerCase().includes(searchQuery.toLowerCase()) ||
    user.cargo.toLowerCase().includes(searchQuery.toLowerCase())
  );

  function resetUserForm() {
    setUserFormMode("create");
    setUserForm({ 
      id: "", 
      keycloakUserId: "",
      email: "",
      displayName: "", 
      fullName: "", 
      phone: "", 
      nif: "", 
      dateOfBirth: "", 
      address: "",
      roles: [],
      status: "ACTIVE"
    });
  }

  async function handleAvatarUpload(file: File) {
    try {
      setAvatarUploading(true);
      const { uploadUrl, storageKey } = await uploadService.getPresignedUrl({
        purpose: "user-avatar",
        fileName: file.name,
        contentType: file.type,
      });
      
      await uploadService.uploadToS3(uploadUrl, file);
      
      const avatarPreview = URL.createObjectURL(file);
      setUserForm(prev => ({ ...prev, avatarStorageKey: storageKey, avatarPreview }));
    } catch (error) {
      console.error("Avatar upload failed:", error);
    } finally {
      setAvatarUploading(false);
    }
  }

  function toggleRole(role: string) {
    setUserForm(prev => ({
      ...prev,
      roles: prev.roles.includes(role) 
        ? prev.roles.filter(r => r !== role)
        : [...prev.roles, role]
    }));
  }

  async function submitUserForm(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!userForm.displayName.trim() || !userForm.email.trim()) return;

    if (userFormMode === "create") {
      try {
        const created = await userService.create({
          keycloakUserId: userForm.keycloakUserId,
          email: userForm.email,
          displayName: userForm.displayName,
          fullName: userForm.fullName,
          phone: userForm.phone,
          nif: userForm.nif,
          dateOfBirth: userForm.dateOfBirth,
          address: userForm.address,
          roles: userForm.roles,
        });
        
        setUsers((prev) => [{
          id: created.id,
          nome: created.displayName,
          email: created.email,
          estado: created.status,
          cargo: created.roles.join(", "),
        }, ...prev]);
      } catch (error) {
        console.error("Erro ao criar utilizador:", error);
        // Show error to user instead of creating mock data
        setError("Erro ao criar utilizador. Tente novamente.");
        return;
      }
    }

    resetUserForm();
  }

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div>
          <h1 className="font-headline-lg text-headline-lg text-on-surface">Utilizadores</h1>
          <p className="mt-1 text-body-md text-on-surface-variant">
            Gestão de utilizadores do sistema e suas permissões.
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
                <CardTitle>Lista de Utilizadores</CardTitle>
                <Input
                  placeholder="Pesquisar utilizadores..."
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                  className="sm:w-72"
                />
              </div>
            </CardHeader>
            <CardContent>
              {loading ? (
                <TableSkeleton />
              ) : filteredUsers.length === 0 ? (
                <EmptyState message="Nenhum utilizador encontrado." />
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead>
                      <tr className="border-b border-outline-variant text-xs font-bold uppercase text-on-surface-variant">
                        <th className="pb-3 pr-4">Nome</th>
                        <th className="pb-3 pr-4">Email</th>
                        <th className="pb-3 pr-4">Estado</th>
                        <th className="pb-3 pr-4">Cargos</th>
                        <th className="pb-3">Acções</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredUsers.map((user) => (
                        <tr key={user.id} className="border-b border-outline-variant/50 last:border-0">
                          <td className="py-3 pr-4 font-bold text-on-surface">{user.nome}</td>
                          <td className="py-3 pr-4 text-on-surface-variant">{user.email}</td>
                          <td className="py-3 pr-4 text-on-surface-variant">{user.estado}</td>
                          <td className="py-3 pr-4 text-on-surface-variant">{user.cargo}</td>
                          <td className="py-3">
                            <Button
                              variant="outline"
                              size="sm"
                              disabled={loadingUserEdit}
                              onClick={async () => {
                                setLoadingUserEdit(true);
                                try {
                                  const userData = await userService.getById(user.id);
                                  setUserFormMode("edit");
                                  setUserForm({
                                    id: userData.id,
                                    keycloakUserId: userData.keycloakUserId,
                                    email: userData.email,
                                    displayName: userData.displayName,
                                    fullName: userData.fullName || "",
                                    phone: userData.phone || "",
                                    nif: userData.nif || "",
                                    dateOfBirth: userData.dateOfBirth || "",
                                    address: userData.address || "",
                                    roles: userData.roles,
                                    status: userData.status,
                                    avatarStorageKey: userData.avatarStorageKey,
                                  });
                                } catch (error) {
                                  console.error("Erro ao carregar utilizador:", error);
                                  setError("Erro ao carregar dados do utilizador. Tente novamente.");
                                } finally {
                                  setLoadingUserEdit(false);
                                }
                              }}
                            >
                              {loadingUserEdit ? "A carregar..." : "Editar"}
                            </Button>
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
              <CardTitle>{userFormMode === "create" ? "Criar Utilizador" : "Editar Utilizador"}</CardTitle>
            </CardHeader>
            <CardContent>
              <form className="space-y-4" onSubmit={submitUserForm}>
                {/* Avatar Upload */}
                <div className="space-y-2">
                  <label className="text-sm font-bold text-on-surface">Avatar</label>
                  <div className="flex items-center gap-3">
                    {userForm.avatarPreview ? (
                      <div className="relative">
                        <img 
                          src={userForm.avatarPreview} 
                          alt="Avatar preview" 
                          className="h-16 w-16 rounded-full object-cover border border-outline-variant"
                        />
                        <button
                          type="button"
                          onClick={() => setUserForm(prev => ({ ...prev, avatarStorageKey: undefined, avatarPreview: undefined }))}
                          className="absolute -top-2 -right-2 h-6 w-6 rounded-full bg-error text-white flex items-center justify-center"
                        >
                          <Trash2 className="h-3 w-3" />
                        </button>
                      </div>
                    ) : (
                      <div className="h-16 w-16 rounded-full border-2 border-dashed border-outline-variant flex items-center justify-center">
                        <User className="h-6 w-6 text-on-surface-variant" />
                      </div>
                    )}
                    <div className="flex-1">
                      <input
                        type="file"
                        accept="image/*"
                        id="avatar-upload"
                        className="hidden"
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          if (file) handleAvatarUpload(file);
                        }}
                      />
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => document.getElementById('avatar-upload')?.click()}
                        disabled={avatarUploading}
                      >
                        {avatarUploading ? (
                          <>Enviando...</>
                        ) : (
                          <>
                            <Upload className="h-4 w-4 mr-2" />
                            Escolher Avatar
                          </>
                        )}
                      </Button>
                    </div>
                  </div>
                </div>

                {/* Basic Information */}
                <div className="space-y-2">
                  <label className="text-sm font-bold text-on-surface flex items-center gap-2">
                    <User className="h-4 w-4" />
                    Informações Básicas
                  </label>
                  <div className="space-y-3">
                    {userFormMode === "create" && (
                      <Input
                        placeholder="Keycloak User ID *"
                        value={userForm.keycloakUserId}
                        onChange={(event) => setUserForm((prev) => ({ ...prev, keycloakUserId: event.target.value }))}
                        required
                      />
                    )}
                    <Input
                      placeholder="Nome de exibição *"
                      value={userForm.displayName}
                      onChange={(event) => setUserForm((prev) => ({ ...prev, displayName: event.target.value }))}
                      required
                    />
                    <Input
                      placeholder="Nome completo"
                      value={userForm.fullName}
                      onChange={(event) => setUserForm((prev) => ({ ...prev, fullName: event.target.value }))}
                    />
                    <Input
                      placeholder="NIF"
                      value={userForm.nif}
                      onChange={(event) => setUserForm((prev) => ({ ...prev, nif: event.target.value }))}
                    />
                    <Input
                      type="date"
                      placeholder="Data de nascimento"
                      value={userForm.dateOfBirth}
                      onChange={(event) => setUserForm((prev) => ({ ...prev, dateOfBirth: event.target.value }))}
                    />
                  </div>
                </div>

                {/* Contact Information */}
                <div className="space-y-2">
                  <label className="text-sm font-bold text-on-surface flex items-center gap-2">
                    <Mail className="h-4 w-4" />
                    Contacto
                  </label>
                  <div className="space-y-3">
                    <Input
                      type="email"
                      placeholder="Email *"
                      value={userForm.email}
                      onChange={(event) => setUserForm((prev) => ({ ...prev, email: event.target.value }))}
                      required
                    />
                    <Input
                      placeholder="Telefone"
                      value={userForm.phone}
                      onChange={(event) => setUserForm((prev) => ({ ...prev, phone: event.target.value }))}
                    />
                    <textarea
                      placeholder="Endereço"
                      value={userForm.address}
                      onChange={(event) => setUserForm((prev) => ({ ...prev, address: event.target.value }))}
                      className="flex min-h-[60px] w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm transition-colors placeholder:text-on-surface-variant focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-not-allowed disabled:opacity-50 resize-none"
                    />
                  </div>
                </div>

                {/* Roles */}
                <div className="space-y-2">
                  <label className="text-sm font-bold text-on-surface flex items-center gap-2">
                    <Shield className="h-4 w-4" />
                    Cargos e Permissões
                  </label>
                  <div className="grid grid-cols-2 gap-2">
                    {roles.map((role) => (
                      <label key={role} className="flex items-center gap-2 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={userForm.roles.includes(role)}
                          onChange={() => toggleRole(role)}
                          className="rounded border-outline-variant"
                        />
                        <span className="text-sm text-on-surface">{role}</span>
                      </label>
                    ))}
                  </div>
                </div>

                {/* Status */}
                <div className="space-y-2">
                  <label className="text-sm font-bold text-on-surface">Estado</label>
                  <select
                    value={userForm.status}
                    onChange={(event) => setUserForm((prev) => ({ ...prev, status: event.target.value }))}
                    className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <option value="ACTIVE">Activo</option>
                    <option value="SUSPENDED">Suspenso</option>
                    <option value="INACTIVE">Inactivo</option>
                  </select>
                </div>

                <div className="flex gap-2">
                  <Button type="submit" className="flex-1">{userFormMode === "create" ? "Criar" : "Guardar"}</Button>
                  {userFormMode === "edit" && (
                    <Button type="button" variant="outline" onClick={resetUserForm}>Cancelar</Button>
                  )}
                </div>
              </form>
            </CardContent>
          </Card>
        </section>
      </main>
    </AppShell>
  );
}