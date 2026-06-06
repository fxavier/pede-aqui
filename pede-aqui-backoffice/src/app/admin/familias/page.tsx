"use client";

import { useState, useEffect } from 'react';
import { Plus, Edit, Trash2, Package, Settings, ArrowLeft } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ErrorState } from '@/components/ui/error-state';
import { TableSkeleton } from '@/components/ui/loading-skeleton';
import { catalogService, productFamilyService, productVariationOptionService, vendorService } from '@/lib/api/services';
import type { Product, ProductVariationGroup, ProductVariationOption, Vendor } from '@/lib/api/types';

export default function AdminProductFamiliesPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [vendors, setVendors] = useState<Vendor[]>([]);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [variationGroups, setVariationGroups] = useState<ProductVariationGroup[]>([]);
  const [selectedGroup, setSelectedGroup] = useState<ProductVariationGroup | null>(null);
  const [options, setOptions] = useState<ProductVariationOption[]>([]);
  
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingGroup, setEditingGroup] = useState<ProductVariationGroup | null>(null);
  const [editingOption, setEditingOption] = useState<ProductVariationOption | null>(null);
  const [createGroupDialogOpen, setCreateGroupDialogOpen] = useState(false);
  const [editGroupDialogOpen, setEditGroupDialogOpen] = useState(false);
  const [createOptionDialogOpen, setCreateOptionDialogOpen] = useState(false);
  const [editOptionDialogOpen, setEditOptionDialogOpen] = useState(false);

  // Form states
  const [groupFormData, setGroupFormData] = useState({
    name: '',
    description: '',
    required: false,
    minSelections: 0,
    maxSelections: 1,
    displayOrder: 0
  });

  const [optionFormData, setOptionFormData] = useState({
    name: '',
    description: '',
    priceModifier: 0,
    available: true,
    displayOrder: 0
  });

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    if (selectedProduct) {
      loadVariationGroups();
    }
  }, [selectedProduct]);

  useEffect(() => {
    if (selectedGroup) {
      loadOptions();
    }
  }, [selectedGroup]);

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [vendorsData] = await Promise.all([
        vendorService.list()
      ]);
      setVendors(vendorsData);

      // Load products for the first vendor if available
      if (vendorsData.length > 0) {
        const productsData = await catalogService.listVendorProducts(vendorsData[0].id);
        setProducts(productsData);
      }
    } catch (err) {
      setError('Erro ao carregar dados');
      console.error('Erro ao carregar dados:', err);
    } finally {
      setLoading(false);
    }
  };

  const loadProductsForVendor = async (vendorId: string) => {
    try {
      setError(null);
      const productsData = await catalogService.listVendorProducts(vendorId);
      setProducts(productsData);
      setSelectedProduct(null);
      setSelectedGroup(null);
    } catch (err) {
      setError('Erro ao carregar produtos');
      console.error('Erro ao carregar produtos:', err);
    }
  };

  const loadVariationGroups = async () => {
    if (!selectedProduct) return;
    try {
      setError(null);
      const groupsData = await productFamilyService.listForProduct(selectedProduct.id);
      setVariationGroups(groupsData);
      setSelectedGroup(null);
    } catch (err) {
      setError('Erro ao carregar grupos de variação');
      console.error('Erro ao carregar grupos de variação:', err);
    }
  };

  const loadOptions = async () => {
    if (!selectedGroup) return;
    try {
      setError(null);
      const optionsData = await productVariationOptionService.listForGroup(selectedGroup.id);
      setOptions(optionsData);
    } catch (err) {
      setError('Erro ao carregar opções de variação');
      console.error('Erro ao carregar opções de variação:', err);
    }
  };

  const handleCreateGroup = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProduct) return;

    try {
      await productFamilyService.create({
        productId: selectedProduct.id,
        ...groupFormData
      });
      setCreateGroupDialogOpen(false);
      setGroupFormData({ name: '', description: '', required: false, minSelections: 0, maxSelections: 1, displayOrder: 0 });
      loadVariationGroups();
    } catch (err) {
      setError('Erro ao criar grupo de variação');
      console.error('Erro ao criar grupo de variação:', err);
    }
  };

  const handleEditGroup = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingGroup) return;

    try {
      await productFamilyService.update(editingGroup.id, groupFormData);
      setEditGroupDialogOpen(false);
      setEditingGroup(null);
      setGroupFormData({ name: '', description: '', required: false, minSelections: 0, maxSelections: 1, displayOrder: 0 });
      loadVariationGroups();
    } catch (err) {
      setError('Erro ao editar grupo de variação');
      console.error('Erro ao editar grupo de variação:', err);
    }
  };

  const handleDeleteGroup = async (id: string) => {
    if (!confirm('Tem a certeza que deseja eliminar esta família de produtos?')) return;

    try {
      await productFamilyService.delete(id);
      loadVariationGroups();
      if (selectedGroup?.id === id) {
        setSelectedGroup(null);
      }
    } catch (err) {
      setError('Erro ao eliminar grupo de variação');
      console.error('Erro ao eliminar grupo de variação:', err);
    }
  };

  const handleCreateOption = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedGroup) return;

    try {
      await productVariationOptionService.create({
        groupId: selectedGroup.id,
        ...optionFormData
      });
      setCreateOptionDialogOpen(false);
      setOptionFormData({ name: '', description: '', priceModifier: 0, available: true, displayOrder: 0 });
      loadOptions();
    } catch (err) {
      setError('Erro ao criar opção de variação');
      console.error('Erro ao criar opção de variação:', err);
    }
  };

  const handleEditOption = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingOption) return;

    try {
      await productVariationOptionService.update(editingOption.id, optionFormData);
      setEditOptionDialogOpen(false);
      setEditingOption(null);
      setOptionFormData({ name: '', description: '', priceModifier: 0, available: true, displayOrder: 0 });
      loadOptions();
    } catch (err) {
      setError('Erro ao editar opção de variação');
      console.error('Erro ao editar opção de variação:', err);
    }
  };

  const handleDeleteOption = async (id: string) => {
    if (!confirm('Tem a certeza que deseja eliminar esta opção?')) return;

    try {
      await productVariationOptionService.delete(id);
      loadOptions();
    } catch (err) {
      setError('Erro ao eliminar opção de variação');
      console.error('Erro ao eliminar opção de variação:', err);
    }
  };

  const openEditGroupDialog = (group: ProductVariationGroup) => {
    setEditingGroup(group);
    setGroupFormData({
      name: group.name,
      description: group.description || '',
      required: group.required,
      minSelections: group.minSelections,
      maxSelections: group.maxSelections,
      displayOrder: group.displayOrder
    });
    setEditGroupDialogOpen(true);
  };

  const openEditOptionDialog = (option: ProductVariationOption) => {
    setEditingOption(option);
    setOptionFormData({
      name: option.name,
      description: option.description || '',
      priceModifier: option.priceModifier,
      available: option.available,
      displayOrder: option.displayOrder
    });
    setEditOptionDialogOpen(true);
  };

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div className="flex justify-between items-center">
          <div>
            <h1 className="font-headline-lg text-headline-lg text-on-surface">
              Gestão de Famílias de Produtos (Admin)
            </h1>
            <p className="mt-1 text-body-md text-on-surface-variant">
              Administrar grupos de variações e opções para todos os produtos
            </p>
          </div>
          {selectedProduct && (
            <Button onClick={() => setSelectedProduct(null)} variant="outline">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Voltar aos Produtos
            </Button>
          )}
        </div>

        {error && <ErrorState message={error} onRetry={loadData} />}

        {loading ? (
          <TableSkeleton />
        ) : !selectedProduct ? (
          // Product Selection
          <div className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle>Selecionar Produto</CardTitle>
                <CardDescription>
                  Escolha um produto para gerir as suas famílias de variações
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {/* Vendor Selection */}
                  <div>
                    <Label htmlFor="vendor">Vendedor</Label>
                    <Select onValueChange={loadProductsForVendor}>
                      <SelectTrigger>
                        <SelectValue placeholder="Selecione um vendedor" />
                      </SelectTrigger>
                      <SelectContent>
                        {vendors.map((vendor) => (
                          <SelectItem key={vendor.id} value={vendor.id}>
                            {vendor.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>

                  {/* Product List */}
                  {products.length > 0 && (
                    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                      {products.map((product) => (
                        <Card 
                          key={product.id} 
                          className="cursor-pointer hover:shadow-md transition-shadow"
                          onClick={() => setSelectedProduct(product)}
                        >
                          <CardHeader>
                            <CardTitle className="flex items-center gap-2">
                              <Package className="h-4 w-4" />
                              {product.name}
                            </CardTitle>
                            <CardDescription>
                              {product.description || 'Sem descrição'}
                            </CardDescription>
                          </CardHeader>
                        </Card>
                      ))}
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>
          </div>
        ) : (
          // Product Variation Groups Management
          <div className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Package className="h-4 w-4" />
                  {selectedProduct.name}
                </CardTitle>
                <CardDescription>
                  {selectedProduct.description || 'Sem descrição'}
                </CardDescription>
              </CardHeader>
            </Card>

            <Tabs defaultValue="groups" className="space-y-4">
              <TabsList>
                <TabsTrigger value="groups">Famílias de Produtos</TabsTrigger>
                {selectedGroup && (
                  <TabsTrigger value="options">
                    Opções: {selectedGroup.name}
                  </TabsTrigger>
                )}
              </TabsList>

              <TabsContent value="groups" className="space-y-4">
                <div className="flex justify-between items-center">
                  <h2 className="text-xl font-semibold">Famílias de Produtos</h2>
                  <Dialog open={createGroupDialogOpen} onOpenChange={setCreateGroupDialogOpen}>
                    <DialogTrigger asChild>
                      <Button>
                        <Plus className="h-4 w-4 mr-2" />
                        Nova Família
                      </Button>
                    </DialogTrigger>
                    <DialogContent>
                      <DialogHeader>
                        <DialogTitle>Criar Nova Família de Produtos</DialogTitle>
                      </DialogHeader>
                      <form onSubmit={handleCreateGroup} className="space-y-4">
                        <div>
                          <Label htmlFor="name">Nome</Label>
                          <Input
                            id="name"
                            value={groupFormData.name}
                            onChange={(e) => setGroupFormData({ ...groupFormData, name: e.target.value })}
                            required
                          />
                        </div>
                        <div>
                          <Label htmlFor="description">Descrição</Label>
                          <Textarea
                            id="description"
                            value={groupFormData.description}
                            onChange={(e) => setGroupFormData({ ...groupFormData, description: e.target.value })}
                          />
                        </div>
                        <div className="flex items-center space-x-2">
                          <Switch
                            id="required"
                            checked={groupFormData.required}
                            onCheckedChange={(checked) => setGroupFormData({ ...groupFormData, required: checked })}
                          />
                          <Label htmlFor="required">Seleção obrigatória</Label>
                        </div>
                        <div className="grid grid-cols-2 gap-4">
                          <div>
                            <Label htmlFor="minSelections">Mín. Seleções</Label>
                            <Input
                              id="minSelections"
                              type="number"
                              min="0"
                              value={groupFormData.minSelections}
                              onChange={(e) => setGroupFormData({ ...groupFormData, minSelections: parseInt(e.target.value) || 0 })}
                            />
                          </div>
                          <div>
                            <Label htmlFor="maxSelections">Máx. Seleções</Label>
                            <Input
                              id="maxSelections"
                              type="number"
                              min="1"
                              value={groupFormData.maxSelections}
                              onChange={(e) => setGroupFormData({ ...groupFormData, maxSelections: parseInt(e.target.value) || 1 })}
                            />
                          </div>
                        </div>
                        <div>
                          <Label htmlFor="displayOrder">Ordem de Exibição</Label>
                          <Input
                            id="displayOrder"
                            type="number"
                            min="0"
                            value={groupFormData.displayOrder}
                            onChange={(e) => setGroupFormData({ ...groupFormData, displayOrder: parseInt(e.target.value) || 0 })}
                          />
                        </div>
                        <div className="flex justify-end space-x-2 pt-4">
                          <Button type="submit">Criar Família</Button>
                        </div>
                      </form>
                    </DialogContent>
                  </Dialog>
                </div>

                <div className="grid gap-4">
                  {variationGroups.map((group) => (
                    <Card key={group.id}>
                      <CardHeader>
                        <div className="flex items-center justify-between">
                          <div>
                            <CardTitle className="flex items-center gap-2">
                              <Settings className="h-4 w-4" />
                              {group.name}
                              <Badge variant={group.required ? 'default' : 'secondary'}>
                                {group.required ? 'Obrigatória' : 'Opcional'}
                              </Badge>
                            </CardTitle>
                            <CardDescription>
                              {group.description || 'Sem descrição'} • 
                              Seleções: {group.minSelections} - {group.maxSelections} • 
                              Ordem: {group.displayOrder}
                            </CardDescription>
                          </div>
                          <div className="flex space-x-2">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => setSelectedGroup(group)}
                            >
                              Gerir Opções
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => openEditGroupDialog(group)}
                            >
                              <Edit className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleDeleteGroup(group.id)}
                              className="text-red-600 hover:text-red-700"
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </div>
                        </div>
                      </CardHeader>
                    </Card>
                  ))}
                  
                  {variationGroups.length === 0 && (
                    <Card>
                      <CardContent className="flex flex-col items-center justify-center py-12">
                        <Settings className="h-12 w-12 text-muted-foreground mb-4" />
                        <h3 className="text-lg font-medium">Nenhuma família encontrada</h3>
                        <p className="text-muted-foreground mb-4">Comece por criar a primeira família de produtos</p>
                        <Button onClick={() => setCreateGroupDialogOpen(true)}>
                          <Plus className="h-4 w-4 mr-2" />
                          Criar Primeira Família
                        </Button>
                      </CardContent>
                    </Card>
                  )}
                </div>
              </TabsContent>

              {selectedGroup && (
                <TabsContent value="options" className="space-y-4">
                  <div className="flex justify-between items-center">
                    <div>
                      <h2 className="text-xl font-semibold">Opções de {selectedGroup.name}</h2>
                      <p className="text-muted-foreground">
                        {selectedGroup.description}
                      </p>
                    </div>
                    <Dialog open={createOptionDialogOpen} onOpenChange={setCreateOptionDialogOpen}>
                      <DialogTrigger asChild>
                        <Button>
                          <Plus className="h-4 w-4 mr-2" />
                          Nova Opção
                        </Button>
                      </DialogTrigger>
                      <DialogContent>
                        <DialogHeader>
                          <DialogTitle>Criar Nova Opção</DialogTitle>
                        </DialogHeader>
                        <form onSubmit={handleCreateOption} className="space-y-4">
                          <div>
                            <Label htmlFor="optionName">Nome</Label>
                            <Input
                              id="optionName"
                              value={optionFormData.name}
                              onChange={(e) => setOptionFormData({ ...optionFormData, name: e.target.value })}
                              required
                            />
                          </div>
                          <div>
                            <Label htmlFor="optionDescription">Descrição</Label>
                            <Textarea
                              id="optionDescription"
                              value={optionFormData.description}
                              onChange={(e) => setOptionFormData({ ...optionFormData, description: e.target.value })}
                            />
                          </div>
                          <div>
                            <Label htmlFor="priceModifier">Modificador de Preço (€)</Label>
                            <Input
                              id="priceModifier"
                              type="number"
                              step="0.01"
                              value={optionFormData.priceModifier}
                              onChange={(e) => setOptionFormData({ ...optionFormData, priceModifier: parseFloat(e.target.value) || 0 })}
                            />
                          </div>
                          <div className="flex items-center space-x-2">
                            <Switch
                              id="optionAvailable"
                              checked={optionFormData.available}
                              onCheckedChange={(checked) => setOptionFormData({ ...optionFormData, available: checked })}
                            />
                            <Label htmlFor="optionAvailable">Disponível</Label>
                          </div>
                          <div>
                            <Label htmlFor="optionDisplayOrder">Ordem de Exibição</Label>
                            <Input
                              id="optionDisplayOrder"
                              type="number"
                              min="0"
                              value={optionFormData.displayOrder}
                              onChange={(e) => setOptionFormData({ ...optionFormData, displayOrder: parseInt(e.target.value) || 0 })}
                            />
                          </div>
                          <div className="flex justify-end space-x-2 pt-4">
                            <Button type="submit">Criar Opção</Button>
                          </div>
                        </form>
                      </DialogContent>
                    </Dialog>
                  </div>

                  <div className="grid gap-4">
                    {options.map((option) => (
                      <Card key={option.id}>
                        <CardHeader>
                          <div className="flex items-center justify-between">
                            <div>
                              <CardTitle className="flex items-center gap-2">
                                {option.name}
                                <Badge variant={option.available ? 'default' : 'secondary'}>
                                  {option.available ? 'Disponível' : 'Indisponível'}
                                </Badge>
                                {option.priceModifier !== 0 && (
                                  <Badge variant="outline">
                                    {option.priceModifier > 0 ? '+' : ''}€{option.priceModifier.toFixed(2)}
                                  </Badge>
                                )}
                              </CardTitle>
                              <CardDescription>
                                {option.description || 'Sem descrição'} • Ordem: {option.displayOrder}
                              </CardDescription>
                            </div>
                            <div className="flex space-x-2">
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => openEditOptionDialog(option)}
                              >
                                <Edit className="h-4 w-4" />
                              </Button>
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => handleDeleteOption(option.id)}
                                className="text-red-600 hover:text-red-700"
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            </div>
                          </div>
                        </CardHeader>
                      </Card>
                    ))}

                    {options.length === 0 && (
                      <Card>
                        <CardContent className="flex flex-col items-center justify-center py-12">
                          <Package className="h-12 w-12 text-muted-foreground mb-4" />
                          <h3 className="text-lg font-medium">Nenhuma opção encontrada</h3>
                          <p className="text-muted-foreground mb-4">Adicione opções para esta família de produtos</p>
                          <Button onClick={() => setCreateOptionDialogOpen(true)}>
                            <Plus className="h-4 w-4 mr-2" />
                            Criar Primeira Opção
                          </Button>
                        </CardContent>
                      </Card>
                    )}
                  </div>
                </TabsContent>
              )}
            </Tabs>
          </div>
        )}

        {/* Edit Group Dialog */}
        <Dialog open={editGroupDialogOpen} onOpenChange={setEditGroupDialogOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Editar Família de Produtos</DialogTitle>
            </DialogHeader>
            <form onSubmit={handleEditGroup} className="space-y-4">
              <div>
                <Label htmlFor="editName">Nome</Label>
                <Input
                  id="editName"
                  value={groupFormData.name}
                  onChange={(e) => setGroupFormData({ ...groupFormData, name: e.target.value })}
                  required
                />
              </div>
              <div>
                <Label htmlFor="editDescription">Descrição</Label>
                <Textarea
                  id="editDescription"
                  value={groupFormData.description}
                  onChange={(e) => setGroupFormData({ ...groupFormData, description: e.target.value })}
                />
              </div>
              <div className="flex items-center space-x-2">
                <Switch
                  id="editRequired"
                  checked={groupFormData.required}
                  onCheckedChange={(checked) => setGroupFormData({ ...groupFormData, required: checked })}
                />
                <Label htmlFor="editRequired">Seleção obrigatória</Label>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <Label htmlFor="editMinSelections">Mín. Seleções</Label>
                  <Input
                    id="editMinSelections"
                    type="number"
                    min="0"
                    value={groupFormData.minSelections}
                    onChange={(e) => setGroupFormData({ ...groupFormData, minSelections: parseInt(e.target.value) || 0 })}
                  />
                </div>
                <div>
                  <Label htmlFor="editMaxSelections">Máx. Seleções</Label>
                  <Input
                    id="editMaxSelections"
                    type="number"
                    min="1"
                    value={groupFormData.maxSelections}
                    onChange={(e) => setGroupFormData({ ...groupFormData, maxSelections: parseInt(e.target.value) || 1 })}
                  />
                </div>
              </div>
              <div>
                <Label htmlFor="editDisplayOrder">Ordem de Exibição</Label>
                <Input
                  id="editDisplayOrder"
                  type="number"
                  min="0"
                  value={groupFormData.displayOrder}
                  onChange={(e) => setGroupFormData({ ...groupFormData, displayOrder: parseInt(e.target.value) || 0 })}
                />
              </div>
              <div className="flex justify-end space-x-2 pt-4">
                <Button type="submit">Guardar Alterações</Button>
              </div>
            </form>
          </DialogContent>
        </Dialog>

        {/* Edit Option Dialog */}
        <Dialog open={editOptionDialogOpen} onOpenChange={setEditOptionDialogOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Editar Opção</DialogTitle>
            </DialogHeader>
            <form onSubmit={handleEditOption} className="space-y-4">
              <div>
                <Label htmlFor="editOptionName">Nome</Label>
                <Input
                  id="editOptionName"
                  value={optionFormData.name}
                  onChange={(e) => setOptionFormData({ ...optionFormData, name: e.target.value })}
                  required
                />
              </div>
              <div>
                <Label htmlFor="editOptionDescription">Descrição</Label>
                <Textarea
                  id="editOptionDescription"
                  value={optionFormData.description}
                  onChange={(e) => setOptionFormData({ ...optionFormData, description: e.target.value })}
                />
              </div>
              <div>
                <Label htmlFor="editPriceModifier">Modificador de Preço (€)</Label>
                <Input
                  id="editPriceModifier"
                  type="number"
                  step="0.01"
                  value={optionFormData.priceModifier}
                  onChange={(e) => setOptionFormData({ ...optionFormData, priceModifier: parseFloat(e.target.value) || 0 })}
                />
              </div>
              <div className="flex items-center space-x-2">
                <Switch
                  id="editOptionAvailable"
                  checked={optionFormData.available}
                  onCheckedChange={(checked) => setOptionFormData({ ...optionFormData, available: checked })}
                />
                <Label htmlFor="editOptionAvailable">Disponível</Label>
              </div>
              <div>
                <Label htmlFor="editOptionDisplayOrder">Ordem de Exibição</Label>
                <Input
                  id="editOptionDisplayOrder"
                  type="number"
                  min="0"
                  value={optionFormData.displayOrder}
                  onChange={(e) => setOptionFormData({ ...optionFormData, displayOrder: parseInt(e.target.value) || 0 })}
                />
              </div>
              <div className="flex justify-end space-x-2 pt-4">
                <Button type="submit">Guardar Alterações</Button>
              </div>
            </form>
          </DialogContent>
        </Dialog>
      </main>
    </AppShell>
  );
}