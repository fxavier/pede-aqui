import 'package:flutter/material.dart';

class CheckoutScreen extends StatelessWidget {
  const CheckoutScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Checkout', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 18)),
          const SizedBox(height: 12),
          const ListTile(
            title: Text('Morada de entrega'),
            subtitle: Text('Av. Julius Nyerere, Maputo'),
          ),
          const ListTile(
            title: Text('Metodo de pagamento'),
            subtitle: Text('Pagamento local (mock)'),
          ),
          const TextField(
            maxLines: 3,
            decoration: InputDecoration(labelText: 'Instrucoes de entrega'),
          ),
          const Spacer(),
          SizedBox(
            width: double.infinity,
            child: FilledButton(onPressed: () {}, child: const Text('Confirmar encomenda')),
          )
        ],
      ),
    );
  }
}
