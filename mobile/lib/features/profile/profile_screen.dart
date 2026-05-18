import 'package:flutter/material.dart';
import 'package:delivery_marketplace_mobile/core/formatters/currency_formatter.dart';

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const ListTile(
          title: Text('Conta'),
          subtitle: Text('Utilizador de demonstração - Mocambique'),
        ),
        Card(
          child: ListTile(
            title: const Text('Ganhos da semana'),
            subtitle: Text(formatMzn(7340)),
          ),
        ),
        const Card(
          child: ListTile(
            title: Text('Entregas concluidas'),
            subtitle: Text('41'),
          ),
        ),
      ],
    );
  }
}
