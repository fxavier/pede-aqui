import '../../../core/network/api_client.dart';
import 'order_models.dart';

abstract class OrderRepository {
  Future<DeliveryOrder> getActiveOrder();
}

class MockOrderRepository implements OrderRepository {
  @override
  Future<DeliveryOrder> getActiveOrder() async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
    return const DeliveryOrder(
      id: 'ord_00891',
      code: '#PA-2026-00891',
      customerName: 'João Silva',
      courierName: 'Kiala Miguel',
      address: 'Av. Julius Nyerere, 123, Polana',
      estimatedMinutes: 25,
      lines: [
        OrderLine(name: 'Muamba Tradicional', quantity: 1, price: 14500, emoji: '🍛'),
        OrderLine(name: 'Sumo de Maçã 500ml', quantity: 1, price: 2500, emoji: '🥤'),
      ],
      steps: [
        OrderStatusStep(title: 'Criado', description: 'Pedido recebido', completed: true),
        OrderStatusStep(title: 'Confirmado', description: 'Loja confirmou', completed: true),
        OrderStatusStep(title: 'Preparo', description: 'Em preparação', completed: true),
        OrderStatusStep(title: 'No caminho', description: 'Estafeta a caminho', completed: false),
        OrderStatusStep(title: 'Entregue', description: 'Aguardando entrega', completed: false),
      ],
      subtotal: 16500,
      deliveryFee: 500,
      total: 17000,
    );
  }
}

class ApiOrderRepository implements OrderRepository {
  ApiOrderRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<DeliveryOrder> getActiveOrder() async {
    final response = await _apiClient.get<Map<String, dynamic>>('/orders/ord_001/tracking');
    final data = response.data!;
    final orderStatus = data['orderStatus'] as String? ?? 'PENDING';
    return DeliveryOrder(
      id: data['id'] as String? ?? 'ord_001',
      code: data['reference'] as String? ?? '#PA-00001',
      customerName: data['customerName'] as String? ?? 'Cliente',
      courierName: data['courierName'] as String? ?? 'Estafeta',
      address: data['deliveryAddress'] as String? ?? '',
      estimatedMinutes: (data['estimatedMinutes'] as num?)?.toInt() ?? 20,
      lines: [],
      steps: _mapSteps(orderStatus),
      subtotal: (data['subtotal'] as num?)?.toDouble() ?? 0,
      deliveryFee: (data['deliveryFee'] as num?)?.toDouble() ?? 0,
      total: (data['total'] as num?)?.toDouble() ?? 0,
    );
  }

  List<OrderStatusStep> _mapSteps(String status) {
    const allSteps = [
      ('Criado', 'Pedido recebido'),
      ('Confirmado', 'Pagamento confirmado'),
      ('Preparo', 'Em preparação'),
      ('No caminho', 'Estafeta a caminho'),
      ('Entregue', 'Entregue com sucesso'),
    ];

    final completedCount = switch (status) {
      'DELIVERED' => 5,
      'PICKED_UP' || 'DELIVERING' => 4,
      'READY_FOR_PICKUP' || 'PREPARING' => 3,
      'PAYMENT_CONFIRMED' || 'ACCEPTED_BY_VENDOR' => 2,
      'PAYMENT_PENDING' || 'PENDING' => 1,
      _ => 1,
    };

    return allSteps.asMap().entries.map((e) {
      final index = e.key;
      final (title, description) = e.value;
      return OrderStatusStep(
        title: title,
        description: description,
        completed: index < completedCount,
      );
    }).toList();
  }
}
