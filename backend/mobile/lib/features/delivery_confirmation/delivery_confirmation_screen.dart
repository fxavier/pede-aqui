import 'package:flutter/material.dart';

class DeliveryConfirmationScreen extends StatelessWidget {
  const DeliveryConfirmationScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Confirmacao de entrega', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 18)),
          const SizedBox(height: 12),
          const Text('Introduza o codigo de 6 digitos fornecido pelo cliente:'),
          const SizedBox(height: 8),
          const TextField(maxLength: 6, keyboardType: TextInputType.number),
          const SizedBox(height: 12),
          FilledButton(onPressed: () {}, child: const Text('Concluir entrega')),
          const SizedBox(height: 8),
          OutlinedButton(onPressed: () {}, child: const Text('Marcar falha de entrega')),
        ],
      ),
    );
  }
}
