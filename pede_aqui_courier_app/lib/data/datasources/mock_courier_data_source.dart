import 'courier_data_source.dart';
import '../models/courier_profile.dart';
import '../models/delivery_models.dart';
import '../models/earnings_models.dart';

class MockCourierDataSource implements CourierDataSource {
  static const _networkDelay = Duration(milliseconds: 250);

  @override
  Future<CourierProfile> getProfile() async {
    await Future<void>.delayed(_networkDelay);
    return const CourierProfile(
      id: 'courier-001',
      name: 'Félix Mabunda',
      phone: '+258 84 123 4567',
      city: 'Maputo, Moçambique',
      vehicle: 'Mota • MZ-12-34-MP',
      rating: 4.92,
      totalDeliveries: 1248,
      completedToday: 6,
    );
  }

  @override
  Future<Delivery> getActiveDelivery() async {
    await Future<void>.delayed(_networkDelay);
    return const Delivery(
      id: 'delivery-00891',
      reference: '#PA-2026-00891',
      customerName: 'João Silva',
      vendor: DeliveryLocation(
        name: 'Bella Pizza Polana',
        address: 'Av. Julius Nyerere, 124',
        district: 'Polana',
        phone: '+258 84 333 2211',
      ),
      destination: DeliveryLocation(
        name: 'João Silva',
        address: 'Av. Eduardo Mondlane, 456',
        district: 'Coop, Maputo',
        phone: '+258 82 777 9900',
        note: 'Entregar no 2.º andar, porta 24. Ligar ao chegar.',
      ),
      status: DeliveryStatus.goingToClient,
      estimatedMinutes: 12,
      expectedEarning: 320,
      paymentMethod: 'Pagar no destino',
      distanceKm: 4.2,
      pickupCompleted: true,
      verificationCode: '482913',
      items: [
        DeliveryItem(name: 'Pizza Familiar', quantity: 2),
        DeliveryItem(name: 'Coca-Cola 1.5L', quantity: 1),
      ],
    );
  }

  @override
  Future<List<AvailableJob>> getAvailableJobs() async {
    await Future<void>.delayed(_networkDelay);
    return const [
      AvailableJob(
        id: 'job-001',
        vendorName: 'Burger House',
        distanceKm: 4.2,
        pickupDistrict: 'Sommerschield',
        destinationDistrict: 'Baixa',
        estimatedEarning: 320,
        category: 'Restaurante',
      ),
      AvailableJob(
        id: 'job-002',
        vendorName: 'KFC 24 de Julho',
        distanceKm: 1.8,
        pickupDistrict: 'Alto Maé',
        destinationDistrict: 'Central',
        estimatedEarning: 210,
        category: 'Restaurante',
      ),
      AvailableJob(
        id: 'job-003',
        vendorName: 'Farmácia Avenida',
        distanceKm: 3.1,
        pickupDistrict: 'Malhangalene',
        destinationDistrict: 'Mafalala',
        estimatedEarning: 260,
        category: 'Farmácia',
      ),
    ];
  }

  @override
  Future<EarningSummary> getEarningSummary() async {
    await Future<void>.delayed(_networkDelay);
    return const EarningSummary(today: 1250, week: 8400, month: 32150, completedDeliveries: 42);
  }

  @override
  Future<List<WeeklyEarning>> getWeeklyEarnings() async {
    await Future<void>.delayed(_networkDelay);
    return const [
      WeeklyEarning(day: 'Dom', amount: 900),
      WeeklyEarning(day: 'Seg', amount: 1250),
      WeeklyEarning(day: 'Ter', amount: 1100),
      WeeklyEarning(day: 'Qua', amount: 1600),
      WeeklyEarning(day: 'Qui', amount: 1400),
      WeeklyEarning(day: 'Sex', amount: 1850),
      WeeklyEarning(day: 'Sáb', amount: 650, isToday: true),
    ];
  }

  @override
  Future<List<EarningRecord>> getEarningHistory() async {
    await Future<void>.delayed(_networkDelay);
    return const [
      EarningRecord(id: 'earn-1', vendorName: 'Piri-Piri Grill', timeLabel: 'Hoje, 13:45', amount: 320, status: 'Concluído', category: 'restaurant'),
      EarningRecord(id: 'earn-2', vendorName: 'Supermercado Recheio', timeLabel: 'Hoje, 11:20', amount: 450, status: 'Concluído', category: 'shopping_bag'),
      EarningRecord(id: 'earn-3', vendorName: 'Debonairs Pizza', timeLabel: 'Ontem, 20:15', amount: 280, status: 'Concluído', category: 'local_pizza'),
      EarningRecord(id: 'earn-4', vendorName: 'Farmácia 24h', timeLabel: 'Ontem, 18:30', amount: 200, status: 'Concluído', category: 'local_pharmacy'),
    ];
  }

  @override
  Future<List<NotificationItem>> getNotifications() async {
    await Future<void>.delayed(_networkDelay);
    return const [
      NotificationItem(id: 'n1', title: 'Bónus activo na Baixa', message: 'Ganhe 1.5x em todas as entregas das 18:00 às 21:00.', timeLabel: 'Agora', isRead: false),
      NotificationItem(id: 'n2', title: 'Nova entrega disponível', message: 'Pedido próximo de Alto Maé com pagamento estimado de 210 MT.', timeLabel: '5 min', isRead: false),
      NotificationItem(id: 'n3', title: 'Pagamento processado', message: 'Os seus ganhos de ontem foram processados para a carteira.', timeLabel: 'Ontem', isRead: true),
    ];
  }

  @override
  Future<void> acceptJob(String jobId) async => Future<void>.delayed(_networkDelay);

  @override
  Future<void> rejectJob(String jobId) async => Future<void>.delayed(_networkDelay);

  @override
  Future<void> updateAvailability(bool isAvailable) async => Future<void>.delayed(_networkDelay);

  @override
  Future<void> confirmDelivery({required String deliveryId, required String otpCode, required bool hasProofPhoto}) async {
    await Future<void>.delayed(_networkDelay);
  }
}
