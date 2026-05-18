import 'package:flutter/material.dart';
import 'package:delivery_marketplace_mobile/core/formatters/currency_formatter.dart';

class CatalogScreen extends StatelessWidget {
  const CatalogScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final products = [
      {'name': 'Arroz 5kg', 'price': 690.0},
      {'name': 'Oleo 2L', 'price': 340.0},
      {'name': 'Paracetamol 500mg', 'price': 120.0},
    ];
    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: products.length,
      itemBuilder: (context, index) {
        final item = products[index];
        return Card(
          child: ListTile(
            title: Text(item['name'] as String),
            subtitle: Text(formatMzn(item['price'] as double)),
            trailing: const Icon(Icons.add_circle_outline),
          ),
        );
      },
    );
  }
}
