import 'package:flutter/material.dart';

class CourierJobsScreen extends StatelessWidget {
  const CourierJobsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        SwitchListTile(
          value: true,
          onChanged: (_) {},
          title: const Text('Disponivel para entregas'),
          subtitle: const Text('Zona: Maputo Cidade'),
        ),
        const SizedBox(height: 8),
        const Card(
          child: ListTile(
            title: Text('Job DJ-00312'),
            subtitle: Text('Recolha: Mercado da Baixa | Entrega: Sommerschield'),
            trailing: Icon(Icons.local_shipping_outlined),
          ),
        ),
      ],
    );
  }
}
