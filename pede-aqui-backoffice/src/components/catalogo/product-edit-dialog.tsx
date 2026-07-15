"use client";

import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { catalogService, uploadService } from "@/lib/api/services";
import type { Category, Product, UpdateProductRequest } from "@/lib/api/types";
import { formatCurrency } from "@/lib/utils";
import { activeSku, deltaPercent, errorCode, extractErrorMessage, productImageUrl, skuPendingPrice } from "./catalog-view";
import { ImageIcon, Trash2, Upload } from "lucide-react";

interface ProductEditDialogProps {
  product: Product | null;
  categories: Category[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** Attribute + price + image edit surface for a single product (US-1, US-2, US-3). */
export function ProductEditDialog({ product, categories, open, onOpenChange }: ProductEditDialogProps) {
  const queryClient = useQueryClient();

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [requiresPrescription, setRequiresPrescription] = useState(false);
  const [priceInput, setPriceInput] = useState("");
  const [priceNotice, setPriceNotice] = useState<{ currentPrice: number; pendingPrice: number } | null>(null);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [imageUploading, setImageUploading] = useState(false);

  const [attrError, setAttrError] = useState<string | null>(null);
  const [priceError, setPriceError] = useState<string | null>(null);
  const [imageError, setImageError] = useState<string | null>(null);

  useEffect(() => {
    if (!product) return;
    setName(product.name);
    setDescription(product.description ?? "");
    setCategoryId(product.categoryId);
    setRequiresPrescription(product.requiresPrescriptionMetadata);

    const sku = activeSku(product);
    setPriceInput(sku ? String(sku.price) : "");
    const pending = sku ? skuPendingPrice(sku) : null;
    setPriceNotice(pending !== null && sku ? { currentPrice: sku.price, pendingPrice: pending } : null);

    setImageUrl(productImageUrl(product));
    setAttrError(null);
    setPriceError(null);
    setImageError(null);
  }, [product]);

  function invalidateProducts() {
    if (!product) return;
    queryClient.invalidateQueries({ queryKey: ["catalog", "products", product.vendorId] });
    queryClient.invalidateQueries({ queryKey: ["catalog", "pending-price-changes"] });
  }

  const updateAttributesMutation = useMutation({
    mutationFn: (payload: UpdateProductRequest) => catalogService.updateProduct(product!.id, payload),
    onSuccess: () => {
      setAttrError(null);
      invalidateProducts();
    },
    onError: (err) => setAttrError(extractErrorMessage(err, "Erro ao guardar alterações do produto.")),
  });

  const updatePriceMutation = useMutation({
    mutationFn: (price: number) => catalogService.updateProductPrice(product!.id, price),
    onSuccess: (res) => {
      setPriceError(null);
      if (res.reviewRequired) {
        setPriceNotice({ currentPrice: res.currentPrice, pendingPrice: res.pendingPrice ?? 0 });
      } else {
        setPriceNotice(null);
        setPriceInput(String(res.currentPrice));
      }
      invalidateProducts();
    },
    onError: (err) => {
      const code = errorCode(err);
      if (code === "price_change_pending") {
        setPriceError(
          "Já existe uma alteração de preço pendente de aprovação para este produto. Aguarde a decisão de moderação antes de submeter outra.",
        );
      } else {
        setPriceError(extractErrorMessage(err, "Erro ao atualizar o preço."));
      }
    },
  });

  const setImageMutation = useMutation({
    mutationFn: (storageKey: string) => catalogService.setProductImage(product!.id, storageKey),
    onSuccess: (res) => {
      setImageError(null);
      setImageUrl(res.imageUrl);
      invalidateProducts();
    },
    onError: (err) => setImageError(extractErrorMessage(err, "Erro ao definir a imagem do produto.")),
  });

  const deleteImageMutation = useMutation({
    mutationFn: () => catalogService.deleteProductImage(product!.id),
    onSuccess: () => {
      setImageError(null);
      setImageUrl(null);
      invalidateProducts();
    },
    onError: (err) => setImageError(extractErrorMessage(err, "Erro ao remover a imagem do produto.")),
  });

  async function handleImageSelect(file: File) {
    if (!product) return;
    setImageUploading(true);
    setImageError(null);
    try {
      const { uploadUrl, storageKey } = await uploadService.getPresignedUrl({
        purpose: "product_image",
        fileName: file.name,
        contentType: file.type,
      });
      await uploadService.uploadToS3(uploadUrl, file);
      await setImageMutation.mutateAsync(storageKey);
    } catch (err) {
      setImageError(extractErrorMessage(err, "Erro ao enviar a imagem."));
    } finally {
      setImageUploading(false);
    }
  }

  function handleSaveAttributes(e: React.FormEvent) {
    e.preventDefault();
    if (!product) return;
    const payload: UpdateProductRequest = {};
    if (name !== product.name) payload.name = name;
    if ((description || "") !== (product.description || "")) payload.description = description;
    if (categoryId !== product.categoryId) payload.categoryId = categoryId;
    if (requiresPrescription !== product.requiresPrescriptionMetadata) payload.requiresPrescription = requiresPrescription;
    if (Object.keys(payload).length === 0) {
      setAttrError("Não há alterações para guardar.");
      return;
    }
    setAttrError(null);
    updateAttributesMutation.mutate(payload);
  }

  function handleSavePrice(e: React.FormEvent) {
    e.preventDefault();
    const parsed = Number(priceInput);
    if (!Number.isFinite(parsed) || parsed < 0) {
      setPriceError("Indique um preço válido (maior ou igual a zero).");
      return;
    }
    setPriceError(null);
    updatePriceMutation.mutate(parsed);
  }

  if (!product) return null;

  const pendingDelta = priceNotice ? deltaPercent(priceNotice.currentPrice, priceNotice.pendingPrice) : null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] max-w-xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Editar Produto</DialogTitle>
          <DialogDescription>{product.name}</DialogDescription>
        </DialogHeader>

        <form className="space-y-4" onSubmit={handleSaveAttributes}>
          <div>
            <Label htmlFor="edit-product-name">Nome</Label>
            <Input id="edit-product-name" value={name} onChange={(e) => setName(e.target.value)} required />
          </div>
          <div>
            <Label htmlFor="edit-product-description">Descrição</Label>
            <Textarea
              id="edit-product-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
          <div>
            <Label htmlFor="edit-product-category">Categoria</Label>
            <select
              id="edit-product-category"
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              required
            >
              {categories.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>
          <div className="flex items-center gap-3">
            <Switch
              id="edit-product-prescription"
              checked={requiresPrescription}
              onCheckedChange={setRequiresPrescription}
            />
            <Label htmlFor="edit-product-prescription">Requer receita médica</Label>
          </div>
          {attrError && <p className="text-xs text-error">{attrError}</p>}
          <div className="flex justify-end">
            <Button type="submit" size="sm" disabled={updateAttributesMutation.isPending}>
              {updateAttributesMutation.isPending ? "A guardar..." : "Guardar Detalhes"}
            </Button>
          </div>
        </form>

        <div className="space-y-3 border-t border-outline-variant pt-4">
          <form className="space-y-3" onSubmit={handleSavePrice}>
            <Label htmlFor="edit-product-price">Preço (MZN)</Label>
            <div className="flex gap-2">
              <Input
                id="edit-product-price"
                type="number"
                min="0"
                step="0.01"
                value={priceInput}
                onChange={(e) => setPriceInput(e.target.value)}
                disabled={priceNotice !== null}
                required
              />
              <Button type="submit" size="sm" disabled={updatePriceMutation.isPending || priceNotice !== null}>
                {updatePriceMutation.isPending ? "A enviar..." : "Atualizar Preço"}
              </Button>
            </div>
            {priceNotice && (
              <div className="rounded-lg border border-yellow-200 bg-yellow-50 p-3 text-xs text-yellow-800">
                <p className="font-bold">Alteração de preço pendente de aprovação</p>
                <p className="mt-1">
                  Preço atual: {formatCurrency(priceNotice.currentPrice)} → Proposto: {formatCurrency(priceNotice.pendingPrice)}
                  {pendingDelta !== null && ` (${pendingDelta > 0 ? "+" : ""}${pendingDelta.toFixed(1)}%)`}
                </p>
                <p className="mt-1">O produto continua à venda pelo preço atual até haver uma decisão de moderação.</p>
              </div>
            )}
            {priceError && <p className="text-xs text-error">{priceError}</p>}
          </form>
        </div>

        <div className="space-y-3 border-t border-outline-variant pt-4">
          <Label>Imagem do Produto</Label>
          <div className="flex items-center gap-3">
            {imageUrl ? (
              // eslint-disable-next-line @next/next/no-img-element -- remote presigned/public storage URL, not a static asset next/image can optimize
              <img
                src={imageUrl}
                alt={product.name}
                className="h-20 w-20 rounded-lg border border-outline-variant object-cover"
              />
            ) : (
              <div className="flex h-20 w-20 items-center justify-center rounded-lg border-2 border-dashed border-outline-variant">
                <ImageIcon className="h-6 w-6 text-on-surface-variant" />
              </div>
            )}
            <div className="flex flex-col gap-2">
              <input
                type="file"
                accept="image/*"
                id="edit-product-image-upload"
                className="hidden"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) handleImageSelect(f);
                }}
              />
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={imageUploading || setImageMutation.isPending}
                onClick={() => document.getElementById("edit-product-image-upload")?.click()}
              >
                <Upload className="mr-2 h-4 w-4" />
                {imageUploading ? "A enviar..." : "Substituir Imagem"}
              </Button>
              {imageUrl && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="text-red-600 hover:text-red-700"
                  disabled={deleteImageMutation.isPending}
                  onClick={() => deleteImageMutation.mutate()}
                >
                  <Trash2 className="mr-2 h-4 w-4" />
                  {deleteImageMutation.isPending ? "A remover..." : "Remover Imagem"}
                </Button>
              )}
            </div>
          </div>
          {imageError && <p className="text-xs text-error">{imageError}</p>}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
