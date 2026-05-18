import 'package:flutter/material.dart';

class OrdersScreen extends StatelessWidget {
  const OrdersScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: const [
        Card(
          child: ListTile(
            title: Text('Pedido PA-2026-00091'),
            subtitle: Text('Estado: A CAMINHO | Codigo de entrega: 483291'),
          ),
        ),
        Card(
          child: ListTile(
            title: Text('Pedido PA-2026-00076'),
            subtitle: Text('Estado: ENTREGUE'),
          ),
        ),
      ],
    );
  }
}
