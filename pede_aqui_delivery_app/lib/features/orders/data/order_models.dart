class OrderLine {
  const OrderLine({
    required this.name,
    required this.quantity,
    required this.price,
    required this.emoji,
  });

  final String name;
  final int quantity;
  final double price;
  final String emoji;
}

class OrderStatusStep {
  const OrderStatusStep({
    required this.title,
    required this.description,
    required this.completed,
  });

  final String title;
  final String description;
  final bool completed;
}

class DeliveryOrder {
  const DeliveryOrder({
    required this.id,
    required this.code,
    required this.customerName,
    required this.courierName,
    required this.address,
    required this.estimatedMinutes,
    required this.lines,
    required this.steps,
    required this.subtotal,
    required this.deliveryFee,
    required this.total,
  });

  final String id;
  final String code;
  final String customerName;
  final String courierName;
  final String address;
  final int estimatedMinutes;
  final List<OrderLine> lines;
  final List<OrderStatusStep> steps;
  final double subtotal;
  final double deliveryFee;
  final double total;
}
