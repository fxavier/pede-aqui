class CartItem {
  const CartItem({
    required this.productId,
    required this.name,
    required this.price,
    required this.quantity,
    required this.emoji,
  });

  final String productId;
  final String name;
  final double price;
  final int quantity;
  final String emoji;

  double get total => price * quantity;

  CartItem copyWith({int? quantity}) {
    return CartItem(
      productId: productId,
      name: name,
      price: price,
      quantity: quantity ?? this.quantity,
      emoji: emoji,
    );
  }
}

class CartSummary {
  const CartSummary({
    required this.items,
    required this.deliveryFee,
    required this.taxes,
    this.discount = 0,
  });

  final List<CartItem> items;
  final double deliveryFee;
  final double taxes;
  final double discount;

  double get subtotal => items.fold(0.0, (sum, item) => sum + item.total);
  double get total => subtotal + deliveryFee + taxes - discount;
}
