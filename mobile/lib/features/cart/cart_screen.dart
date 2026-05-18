import 'package:flutter/material.dart';
import 'package:delivery_marketplace_mobile/core/formatters/currency_formatter.dart';

class CartScreen extends StatelessWidget {
  const CartScreen({super.key});

  @override
  Widget build(BuildContext context) {
    const subtotal = 1030.0;
    const taxas = 154.5;
    const entrega = 120.0;
    const total = subtotal + taxas + entrega;

    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Carrinho (1 fornecedor)', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 18)),
          const SizedBox(height: 12),
          const ListTile(title: Text('Arroz 5kg'), subtitle: Text('Qtd 1'), trailing: Text('MZN 690,00')),
          const ListTile(title: Text('Oleo 2L'), subtitle: Text('Qtd 1'), trailing: Text('MZN 340,00')),
          const Divider(),
          Text('Subtotal: ${formatMzn(subtotal)}'),
          Text('Taxas: ${formatMzn(taxas)}'),
          Text('Entrega: ${formatMzn(entrega)}'),
          const SizedBox(height: 8),
          Text('Total: ${formatMzn(total)}', style: const TextStyle(fontWeight: FontWeight.w700)),
          const Spacer(),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: () {},
              child: const Text('Continuar para checkout'),
            ),
          )
        ],
      ),
    );
  }
}
