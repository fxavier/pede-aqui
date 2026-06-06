'use client';

import { useState, useEffect } from 'react';
import { Plus, Edit, Trash2, Eye, Tag, GitBranch } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogDescription,
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
import { categoryService } from '@/lib/api/services';
import type { Category } from '@/lib/api/types';

export default function CategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingCategory, setEditingCategory] = useState<Category | null>(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editDialogOpen, setEditDialogOpen] = useState(false);

  // Form state
  const [formData, setFormData] = useState({
    name: '',
    vertical: '',
    parentId: 'none',
    active: true
  });

  const verticals = [
    { value: 'food', label: 'Alimentação' },
    { value: 'pharmacy', label: 'Farmácia' },
    { value: 'grocery', label: 'Mercearia' },
    { value: 'retail', label: 'Retalho' },
    { value: 'services', label: 'Serviços' }
  ];

  useEffect(() => {
    loadCategories();
  }, []);

  const loadCategories = async () => {
    try {
      setLoading(true);
      const data = await categoryService.listHierarchical();
      setCategories(data);
    } catch (error) {
      console.error('Erro ao carregar categorias:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await categoryService.create({
        name: formData.name,
        vertical: formData.vertical,
        parentId: formData.parentId === "none" ? undefined : formData.parentId
      });
      setCreateDialogOpen(false);
      setFormData({ name: '', vertical: '', parentId: 'none', active: true });
      loadCategories();
    } catch (error) {
      console.error('Erro ao criar categoria:', error);
    }
  };

  const handleEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingCategory) return;

    try {
      await categoryService.update(editingCategory.id, {
        name: formData.name,
        vertical: formData.vertical,
        parentId: formData.parentId === "none" ? undefined : formData.parentId,
        active: formData.active
      });
      setEditDialogOpen(false);
      setEditingCategory(null);
      setFormData({ name: '', vertical: '', parentId: 'none', active: true });
      loadCategories();
    } catch (error) {
      console.error('Erro ao editar categoria:', error);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Tem a certeza que deseja eliminar esta categoria?')) return;

    try {
      await categoryService.delete(id);
      loadCategories();
    } catch (error) {
      console.error('Erro ao eliminar categoria:', error);
      alert('Erro ao eliminar categoria. Verifique se não tem produtos ou subcategorias associadas.');
    }
  };

  const openEditDialog = (category: Category) => {
    setEditingCategory(category);
    setFormData({
      name: category.name,
      vertical: category.vertical,
      parentId: category.parentId || 'none',
      active: category.active
    });
    setEditDialogOpen(true);
  };

  const renderCategoryTree = (categoryList: Category[], level = 0) => {
    return categoryList.map((category) => (
      <div key={category.id} className="space-y-2">
        <Card className={`${level > 0 ? 'ml-8 border-l-4 border-l-blue-200' : ''}`}>
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-3">
                {level > 0 && <GitBranch className="h-4 w-4 text-muted-foreground" />}
                <div>
                  <CardTitle className="text-lg flex items-center gap-2">
                    <Tag className="h-4 w-4" />
                    {category.name}
                    <Badge variant={category.active ? 'default' : 'secondary'}>
                      {category.active ? 'Ativa' : 'Inativa'}
                    </Badge>
                  </CardTitle>
                  <CardDescription>
                    Vertical: {verticals.find(v => v.value === category.vertical)?.label || category.vertical}
                    {category.parentId && ` • Subcategoria`}
                  </CardDescription>
                </div>
              </div>
              <div className="flex space-x-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => openEditDialog(category)}
                >
                  <Edit className="h-4 w-4" />
                  Editar
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleDelete(category.id)}
                  className="text-red-600 hover:text-red-700"
                >
                  <Trash2 className="h-4 w-4" />
                  Eliminar
                </Button>
              </div>
            </div>
          </CardHeader>
        </Card>
        
        {category.children && category.children.length > 0 && (
          <div className="space-y-2">
            {renderCategoryTree(category.children, level + 1)}
          </div>
        )}
      </div>
    ));
  };

  const flatCategories = (categoryList: Category[]): Category[] => {
    let flat: Category[] = [];
    categoryList.forEach(category => {
      flat.push(category);
      if (category.children) {
        flat = flat.concat(flatCategories(category.children));
      }
    });
    return flat;
  };

  const CategoryForm = ({ onSubmit, submitLabel }: { onSubmit: (e: React.FormEvent) => void; submitLabel: string }) => (
    <form onSubmit={onSubmit} className="space-y-4">
      <div>
        <Label htmlFor="name">Nome da Categoria</Label>
        <Input
          id="name"
          value={formData.name}
          onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          required
        />
      </div>

      <div>
        <Label htmlFor="vertical">Vertical</Label>
        <Select value={formData.vertical} onValueChange={(value) => setFormData({ ...formData, vertical: value })}>
          <SelectTrigger>
            <SelectValue placeholder="Selecione uma vertical" />
          </SelectTrigger>
          <SelectContent>
            {verticals.map((vertical) => (
              <SelectItem key={vertical.value} value={vertical.value}>
                {vertical.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div>
        <Label htmlFor="parentId">Categoria Pai (Opcional)</Label>
        <Select value={formData.parentId} onValueChange={(value) => setFormData({ ...formData, parentId: value })}>
          <SelectTrigger>
            <SelectValue placeholder="Selecione uma categoria pai" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="none">Nenhuma (Categoria raiz)</SelectItem>
            {flatCategories(categories).map((category) => (
              <SelectItem 
                key={category.id} 
                value={category.id}
                disabled={editingCategory?.id === category.id}
              >
                {category.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {editingCategory && (
        <div className="flex items-center space-x-2">
          <Switch
            id="active"
            checked={formData.active}
            onCheckedChange={(checked) => setFormData({ ...formData, active: checked })}
          />
          <Label htmlFor="active">Categoria ativa</Label>
        </div>
      )}

      <div className="flex justify-end space-x-2 pt-4">
        <Button type="submit">
          {submitLabel}
        </Button>
      </div>
    </form>
  );

  if (loading) {
    return (
      <div className="p-6 space-y-6">
        <div className="flex justify-between items-center">
          <h1 className="text-3xl font-bold">Gestão de Categorias</h1>
        </div>
        <div>A carregar...</div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">Gestão de Categorias</h1>
          <p className="text-muted-foreground">
            Gerir as categorias de produtos organizadas hierarquicamente
          </p>
        </div>
        <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
          <DialogTrigger asChild>
            <Button>
              <Plus className="h-4 w-4 mr-2" />
              Nova Categoria
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Criar Nova Categoria</DialogTitle>
              <DialogDescription>
                Preencha os campos abaixo para criar uma nova categoria de produto.
              </DialogDescription>
            </DialogHeader>
            <CategoryForm onSubmit={handleCreate} submitLabel="Criar Categoria" />
          </DialogContent>
        </Dialog>
      </div>

      <div className="space-y-4">
        {categories.length === 0 ? (
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-12">
              <Tag className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-medium">Nenhuma categoria encontrada</h3>
              <p className="text-muted-foreground mb-4">Comece por criar a primeira categoria</p>
              <Button onClick={() => setCreateDialogOpen(true)}>
                <Plus className="h-4 w-4 mr-2" />
                Criar Primeira Categoria
              </Button>
            </CardContent>
          </Card>
        ) : (
          renderCategoryTree(categories)
        )}
      </div>

      {/* Edit Dialog */}
      <Dialog open={editDialogOpen} onOpenChange={setEditDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Editar Categoria</DialogTitle>
            <DialogDescription>
              Modifique os campos desejados para atualizar a categoria.
            </DialogDescription>
          </DialogHeader>
          <CategoryForm onSubmit={handleEdit} submitLabel="Guardar Alterações" />
        </DialogContent>
      </Dialog>
    </div>
  );
}