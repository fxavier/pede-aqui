import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:pede_aqui_delivery_app/core/api/api_client.dart';

class OrdersState {
  const OrdersState({
    this.loading = false,
    this.items = const [],
    this.error,
    this.forbidden = false,
  });

  final bool loading;
  final List<Map<String, dynamic>> items;
  final String? error;
  final bool forbidden;
}

class OrdersCubit extends Cubit<OrdersState> {
  OrdersCubit(this.apiClient) : super(const OrdersState());

  final ApiClient apiClient;

  Future<void> loadById(String orderId) async {
    if (orderId.isEmpty) {
      emit(const OrdersState(items: []));
      return;
    }
    emit(const OrdersState(loading: true));
    final result = await apiClient.getJson('/orders/$orderId/tracking');
    if (result.forbidden) {
      emit(const OrdersState(forbidden: true));
      return;
    }
    if (!result.ok) {
      emit(OrdersState(error: result.error));
      return;
    }
    final current = Map<String, dynamic>.from(result.data as Map);
    final history = [current, ...state.items.where((it) => it['id'] != current['id'])];
    emit(OrdersState(items: history));
  }
}

class OrdersScreen extends StatelessWidget {
  const OrdersScreen({super.key, required this.cubit});

  final OrdersCubit cubit;

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<OrdersCubit, OrdersState>(
      bloc: cubit,
      builder: (context, state) {
        if (state.loading) return const Center(child: CircularProgressIndicator());
        if (state.forbidden) return const Center(child: Text('Sem permissao para ver pedidos.'));
        if (state.error != null) return Center(child: Text('Erro: ${state.error}'));
        if (state.items.isEmpty) return const Center(child: Text('Sem pedidos para mostrar.'));
        final item = state.items.first;
        final status = (item['orderStatus'] ?? '-').toString();
        final code = (item['deliveryCode'] ?? '-').toString();
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              child: ListTile(
                title: Text((item['reference'] ?? 'Pedido').toString()),
                subtitle: Text('Estado: $status | Codigo de entrega: $code'),
              ),
            ),
            const SizedBox(height: 12),
            const Text('Timeline de estado', style: TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            ListTile(
              leading: const Icon(Icons.receipt_long_outlined),
              title: const Text('Pedido criado'),
              subtitle: Text((item['createdAt'] ?? 'Agora').toString()),
            ),
            ListTile(
              leading: const Icon(Icons.local_shipping_outlined),
              title: const Text('Em progresso'),
              subtitle: Text(status),
            ),
            ListTile(
              leading: const Icon(Icons.pin_outlined),
              title: const Text('Confirmacao de entrega'),
              subtitle: Text('Codigo de 6 digitos: $code'),
            ),
            const Divider(),
            const Text('Historico', style: TextStyle(fontWeight: FontWeight.w700)),
            ...state.items.map(
              (entry) => ListTile(
                dense: true,
                title: Text((entry['reference'] ?? 'Pedido').toString()),
                subtitle: Text((entry['orderStatus'] ?? '-').toString()),
              ),
            ),
          ],
        );
      },
    );
  }
}
