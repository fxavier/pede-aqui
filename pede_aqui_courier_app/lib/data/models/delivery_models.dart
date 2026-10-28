import 'package:equatable/equatable.dart';

enum DeliveryStatus {
  accepted,
  goingToVendor,
  atVendor,
  goingToClient,
  delivered,
}

extension DeliveryStatusLabel on DeliveryStatus {
  String get label => switch (this) {
        DeliveryStatus.accepted => 'Aceite',
        DeliveryStatus.goingToVendor => 'A caminho do vendedor',
        DeliveryStatus.atVendor => 'No vendedor',
        DeliveryStatus.goingToClient => 'A caminho do cliente',
        DeliveryStatus.delivered => 'Entregue',
      };

  String get shortLabel => switch (this) {
        DeliveryStatus.accepted => 'Aceite',
        DeliveryStatus.goingToVendor => 'A caminho do vendedor',
        DeliveryStatus.atVendor => 'No vendedor',
        DeliveryStatus.goingToClient => 'A caminho do cliente',
        DeliveryStatus.delivered => 'Entregue',
      };
}

class DeliveryLocation extends Equatable {
  const DeliveryLocation({
    required this.name,
    required this.address,
    required this.district,
    required this.phone,
    this.note,
  });

  final String name;
  final String address;
  final String district;
  final String phone;
  final String? note;

  @override
  List<Object?> get props => [name, address, district, phone, note];
}

class DeliveryItem extends Equatable {
  const DeliveryItem({required this.name, required this.quantity});

  final String name;
  final int quantity;

  String get label => '${quantity}x $name';

  @override
  List<Object?> get props => [name, quantity];
}

class Delivery extends Equatable {
  const Delivery({
    required this.id,
    required this.reference,
    required this.customerName,
    required this.vendor,
    required this.destination,
    required this.status,
    required this.estimatedMinutes,
    required this.expectedEarning,
    required this.paymentMethod,
    required this.items,
    required this.distanceKm,
    required this.pickupCompleted,
    this.verificationCode,
  });

  final String id;
  final String reference;
  final String customerName;
  final DeliveryLocation vendor;
  final DeliveryLocation destination;
  final DeliveryStatus status;
  final int estimatedMinutes;
  final int expectedEarning;
  final String paymentMethod;
  final List<DeliveryItem> items;
  final double distanceKm;
  final bool pickupCompleted;
  final String? verificationCode;

  Delivery copyWith({
    DeliveryStatus? status,
    int? estimatedMinutes,
    bool? pickupCompleted,
    String? verificationCode,
  }) {
    return Delivery(
      id: id,
      reference: reference,
      customerName: customerName,
      vendor: vendor,
      destination: destination,
      status: status ?? this.status,
      estimatedMinutes: estimatedMinutes ?? this.estimatedMinutes,
      expectedEarning: expectedEarning,
      paymentMethod: paymentMethod,
      items: items,
      distanceKm: distanceKm,
      pickupCompleted: pickupCompleted ?? this.pickupCompleted,
      verificationCode: verificationCode ?? this.verificationCode,
    );
  }

  @override
  List<Object?> get props => [
        id,
        reference,
        customerName,
        vendor,
        destination,
        status,
        estimatedMinutes,
        expectedEarning,
        paymentMethod,
        items,
        distanceKm,
        pickupCompleted,
        verificationCode,
      ];
}

class AvailableJob extends Equatable {
  const AvailableJob({
    required this.id,
    required this.vendorName,
    required this.distanceKm,
    required this.pickupDistrict,
    required this.destinationDistrict,
    required this.estimatedEarning,
    required this.category,
  });

  final String id;
  final String vendorName;
  final double distanceKm;
  final String pickupDistrict;
  final String destinationDistrict;
  final int estimatedEarning;
  final String category;

  @override
  List<Object?> get props => [id, vendorName, distanceKm, pickupDistrict, destinationDistrict, estimatedEarning, category];
}
