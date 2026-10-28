import 'package:flutter/material.dart';

import '../../../core/constants/app_colors.dart';
import '../../../core/network/api_client.dart';
import 'catalog_models.dart';

abstract class CatalogRepository {
  Future<List<Category>> getCategories();
  Future<List<Vendor>> getVendors();
  Future<List<Product>> getProductsByVendor(String vendorId);
}

class MockCatalogRepository implements CatalogRepository {
  static const _categories = [
    Category(id: 'restaurants', name: 'Restaurantes', icon: Icons.restaurant_rounded, color: AppColors.primary),
    Category(id: 'super', name: 'Super', icon: Icons.shopping_bag_rounded, color: AppColors.secondary),
    Category(id: 'pharmacy', name: 'Farmácias', icon: Icons.medication_rounded, color: AppColors.tertiary),
    Category(id: 'convenience', name: 'Conveniência', icon: Icons.local_cafe_rounded, color: AppColors.warning),
    Category(id: 'tech', name: 'Tecnologia', icon: Icons.phone_iphone_rounded, color: Color(0xFF1B1B1D)),
  ];

  static const _vendors = [
    Vendor(id: 'avenida-gourmet', name: 'Avenida Gourmet', category: 'Restaurante', description: 'Grelhados, comida contemporânea e pratos locais.', distanceKm: 2.1, deliveryMinutes: 32, rating: 4.8, deliveryFee: 150, isOpen: true, coverEmoji: '🍲'),
    Vendor(id: 'burger-house', name: 'Burger House', category: 'Hambúrgueres', description: 'Burgers artesanais, batatas crocantes e molhos caseiros.', distanceKm: 5.0, deliveryMinutes: 45, rating: 4.5, deliveryFee: 180, isOpen: true, coverEmoji: '🍔'),
    Vendor(id: 'green-garden-bowl', name: 'Green Garden Bowl', category: 'Saudável', description: 'Saladas, bowls e opções vegan.', distanceKm: 4.0, deliveryMinutes: 40, rating: 4.9, deliveryFee: 120, isOpen: true, coverEmoji: '🥗'),
    Vendor(id: 'sushi-zen-master', name: 'Sushi Zen Master', category: 'Japonês', description: 'Sushi, sashimi e pratos premium.', distanceKm: 3.5, deliveryMinutes: 47, rating: 4.7, deliveryFee: 160, isOpen: true, coverEmoji: '🍣'),
  ];

  static const _products = [
    Product(id: 'chamucas-carne', vendorId: 'avenida-gourmet', name: 'Chamuças de Carne', description: 'Conjunto de 4 unidades crocantes com molho especial.', price: 120, category: 'Entradas', emoji: '🥟', badge: 'Mais pedido'),
    Product(id: 'asas-peri-peri', vendorId: 'avenida-gourmet', name: 'Asas Peri-Peri', description: '6 peças marinadas no molho peri-peri da casa.', price: 450, category: 'Entradas', emoji: '🍗'),
    Product(id: 'muamba-chicken', vendorId: 'avenida-gourmet', name: 'Muamba de Galinha', description: 'Arroz branco, molho tradicional e legumes frescos.', price: 650, category: 'Pratos Principais', emoji: '🍛'),
    Product(id: 'amoxicilina', vendorId: 'avenida-gourmet', name: 'Amoxicilina 500mg', description: 'Antibiótico sujeito a confirmação farmacêutica.', price: 620, category: 'Suplementos e Saúde', emoji: '💊', badge: 'Receita necessária'),
    Product(id: 'multivitaminico', vendorId: 'avenida-gourmet', name: 'Multivitamínico', description: 'Suplemento diário para energia e imunidade.', price: 1200, category: 'Suplementos e Saúde', emoji: '🧴'),
  ];

  @override
  Future<List<Category>> getCategories() async {
    await Future<void>.delayed(const Duration(milliseconds: 250));
    return _categories;
  }

  @override
  Future<List<Vendor>> getVendors() async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
    return _vendors;
  }

  @override
  Future<List<Product>> getProductsByVendor(String vendorId) async {
    await Future<void>.delayed(const Duration(milliseconds: 250));
    return _products.where((p) => p.vendorId == vendorId).toList();
  }
}

class ApiCatalogRepository implements CatalogRepository {
  ApiCatalogRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<List<Category>> getCategories() async {
    final response = await _apiClient.get<List<dynamic>>('/search/vendors');
    final data = response.data as List;
    final categories = <String, IconData>{};
    for (final v in data) {
      final cat = (v as Map<String, dynamic>)['category'] as String?;
      if (cat != null) {
        categories[cat] = _iconForCategory(cat);
      }
    }
    return categories.entries.map((e) => Category(
      id: e.key.toLowerCase(),
      name: e.key,
      icon: e.value,
      color: AppColors.primary,
    ),).toList();
  }

  @override
  Future<List<Vendor>> getVendors() async {
    final response = await _apiClient.get<List<dynamic>>('/search/vendors');
    final data = response.data as List;
    return data.map((v) {
      final vendor = v as Map<String, dynamic>;
      return Vendor(
        id: vendor['id'] as String,
        name: vendor['name'] as String,
        category: vendor['category'] as String? ?? '',
        description: vendor['description'] as String? ?? '',
        distanceKm: (vendor['distanceKm'] as num?)?.toDouble() ?? 0,
        deliveryMinutes: (vendor['estimatedDeliveryMinutes'] as num?)?.toInt() ?? 30,
        rating: (vendor['rating'] as num?)?.toDouble() ?? 0,
        deliveryFee: (vendor['deliveryFee'] as num?)?.toDouble() ?? 0,
        isOpen: vendor['available'] as bool? ?? false,
        coverEmoji: '🏪',
      );
    }).toList();
  }

  @override
  Future<List<Product>> getProductsByVendor(String vendorId) async {
    final response = await _apiClient.get<List<dynamic>>('/catalog/vendors/$vendorId/products');
    final data = response.data as List;
    return data.map((p) {
      final product = p as Map<String, dynamic>;
      return Product(
        id: product['id'] as String,
        vendorId: vendorId,
        name: product['name'] as String,
        description: product['description'] as String? ?? '',
        price: (product['price'] as num?)?.toDouble() ?? 0,
        category: product['category'] as String? ?? 'Geral',
        emoji: '📦',
        badge: product['requiresPrescriptionMetadata'] == true ? 'Receita necessária' : null,
      );
    }).toList();
  }

  IconData _iconForCategory(String category) {
    switch (category.toLowerCase()) {
      case 'restaurants':
      case 'restaurante':
        return Icons.restaurant_rounded;
      case 'super':
      case 'grocery':
        return Icons.shopping_bag_rounded;
      case 'pharmacy':
      case 'farmácia':
        return Icons.medication_rounded;
      case 'convenience':
        return Icons.local_cafe_rounded;
      case 'electronics':
        return Icons.phone_iphone_rounded;
      default:
        return Icons.store_rounded;
    }
  }
}
