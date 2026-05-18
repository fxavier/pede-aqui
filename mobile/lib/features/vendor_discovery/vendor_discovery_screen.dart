import 'package:flutter/material.dart';

class VendorDiscoveryScreen extends StatelessWidget {
  const VendorDiscoveryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final vendors = [
      {'name': 'Mercado da Baixa', 'eta': '35 min'},
      {'name': 'Farmacia Central', 'eta': '28 min'},
      {'name': 'Restaurante Costa do Sol', 'eta': '40 min'},
    ];
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: vendors.length,
      separatorBuilder: (_, __) => const SizedBox(height: 10),
      itemBuilder: (context, index) {
        final vendor = vendors[index];
        return ListTile(
          tileColor: const Color(0xFFE7F3F5),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          title: Text(vendor['name']!),
          subtitle: Text('Entrega estimada ${vendor['eta']}'),
          trailing: const Icon(Icons.chevron_right),
        );
      },
    );
  }
}
