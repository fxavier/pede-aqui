import '../../core/network/api_client.dart';
import '../models/courier_profile.dart';
import '../models/delivery_models.dart';
import '../models/earnings_models.dart';
import 'courier_data_source.dart';

class RemoteCourierDataSource implements CourierDataSource {
  RemoteCourierDataSource(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<CourierProfile> getProfile() async {
    final response = await _apiClient.get<Map<String, dynamic>>('/couriers/me');
    final data = response.data!;
    return CourierProfile(
      id: data['id'] as String,
      name: data['displayName'] as String? ?? data['name'] as String,
      phone: data['phone'] as String? ?? '',
      city: data['city'] as String? ?? '',
      vehicle: data['vehicle'] as String? ?? '',
      rating: (data['rating'] as num?)?.toDouble() ?? 0,
      totalDeliveries: data['totalDeliveries'] as int? ?? 0,
      completedToday: data['completedToday'] as int? ?? 0,
    );
  }

  @override
  Future<Delivery> getActiveDelivery() async {
    final response = await _apiClient.get<List<dynamic>>('/dispatch-jobs');
    final jobs = response.data as List;
    if (jobs.isEmpty) throw Exception('Nenhuma entrega ativa');
    final job = jobs.first as Map<String, dynamic>;
    return _mapDispatchJobToDelivery(job);
  }

  @override
  Future<List<AvailableJob>> getAvailableJobs() async {
    final response = await _apiClient.get<List<dynamic>>('/dispatch-jobs');
    final jobs = response.data as List;
    return jobs
        .where((j) => (j as Map<String, dynamic>)['status'] == 'DISPATCH_PENDING')
        .map((j) => _mapToAvailableJob(j as Map<String, dynamic>))
        .toList();
  }

  @override
  Future<EarningSummary> getEarningSummary() async {
    // GET /couriers/me/earnings-summary returns
    // { completedDeliveries, failedDeliveries, earningsTotalMzn }.
    // There is no per-period (today/week/month) breakdown in the backend yet,
    // so those buckets stay at zero until the API provides them.
    final response = await _apiClient.get<Map<String, dynamic>>('/couriers/me/earnings-summary');
    final data = response.data!;
    return EarningSummary(
      today: (data['today'] as num?)?.toInt() ?? 0,
      week: (data['week'] as num?)?.toInt() ?? 0,
      month: (data['month'] as num?)?.toInt() ?? 0,
      completedDeliveries: (data['completedDeliveries'] as num?)?.toInt() ?? 0,
    );
  }

  @override
  Future<List<WeeklyEarning>> getWeeklyEarnings() async {
    // The earnings-summary endpoint returns an aggregate object, not a per-day
    // series (the previous cast of the Map to a List always threw and was
    // silently swallowed). Fetch the summary so connectivity/auth failures
    // surface to the UI, and return an honest empty series until the backend
    // exposes a per-day breakdown.
    await _apiClient.get<Map<String, dynamic>>('/couriers/me/earnings-summary');
    return const [];
  }

  @override
  Future<List<EarningRecord>> getEarningHistory() async {
    // No backend endpoint provides a per-delivery earnings history yet, and
    // /dispatch-jobs never returns DELIVERED jobs, so filtering it was
    // misleading. Return an honest empty state until such an endpoint exists.
    return const [];
  }

  @override
  Future<List<NotificationItem>> getNotifications() async {
    final response = await _apiClient.get<List<dynamic>>('/notifications');
    final data = response.data as List;
    return data.map((n) => NotificationItem(
      id: n['id'] as String,
      title: n['title'] as String,
      message: n['message'] as String,
      timeLabel: n['timeLabel'] as String? ?? '',
      isRead: n['read'] as bool? ?? n['readAt'] != null,
    )).toList();
  }

  @override
  Future<void> acceptJob(String jobId) {
    return _apiClient.post<void>('/dispatch-jobs/$jobId/accept');
  }

  @override
  Future<void> rejectJob(String jobId) {
    return _apiClient.post<void>('/dispatch-jobs/$jobId/reject', data: {'reason': 'Indisponível'});
  }

  @override
  Future<void> updateAvailability(bool isAvailable) {
    return _apiClient.patch<void>('/couriers/me/availability', data: {'available': isAvailable});
  }

  @override
  Future<void> confirmDelivery({required String deliveryId, required String otpCode, required bool hasProofPhoto}) {
    return _apiClient.post<void>('/deliveries/$deliveryId/complete', data: {
      'confirmationCode': otpCode,
    });
  }

  Delivery _mapDispatchJobToDelivery(Map<String, dynamic> job) {
    return Delivery(
      id: job['deliveryId'] as String? ?? job['id'] as String,
      reference: '#PA-${job['orderId']?.toString().substring(0, 5).toUpperCase() ?? '00000'}',
      customerName: job['customerName'] as String? ?? 'Cliente',
      vendor: DeliveryLocation(
        name: job['vendorName'] as String? ?? 'Vendedor',
        address: job['pickupAddress'] as String? ?? '',
        district: job['pickupDistrict'] as String? ?? '',
        phone: job['pickupPhone'] as String? ?? '',
        note: null,
      ),
      destination: DeliveryLocation(
        name: job['customerName'] as String? ?? 'Cliente',
        address: job['destinationAddress'] as String? ?? '',
        district: job['destinationDistrict'] as String? ?? '',
        phone: job['destinationPhone'] as String? ?? '',
        note: job['deliveryInstructions'] as String?,
      ),
      status: _mapStatus(job['status'] as String? ?? ''),
      estimatedMinutes: (job['estimatedMinutes'] as num?)?.toInt() ?? 15,
      expectedEarning: (job['estimatedEarning'] as num?)?.toInt() ?? 0,
      paymentMethod: job['paymentMethod'] as String? ?? 'Pagar no destino',
      items: [],
      distanceKm: (job['distanceKm'] as num?)?.toDouble() ?? 0,
      pickupCompleted: job['pickupCompleted'] as bool? ?? false,
      verificationCode: job['confirmationCode'] as String?,
    );
  }

  AvailableJob _mapToAvailableJob(Map<String, dynamic> job) {
    return AvailableJob(
      id: job['id'] as String,
      vendorName: job['vendorName'] as String? ?? 'Vendedor',
      distanceKm: (job['distanceKm'] as num?)?.toDouble() ?? 0,
      pickupDistrict: job['pickupDistrict'] as String? ?? '',
      destinationDistrict: job['destinationDistrict'] as String? ?? '',
      estimatedEarning: (job['estimatedEarning'] as num?)?.toInt() ?? 0,
      category: job['category'] as String? ?? 'Restaurante',
    );
  }

  DeliveryStatus _mapStatus(String status) {
    switch (status) {
      case 'ACCEPTED':
        return DeliveryStatus.accepted;
      case 'ARRIVED_AT_VENDOR':
        return DeliveryStatus.atVendor;
      case 'PICKED_UP':
      case 'ON_ROUTE_TO_CUSTOMER':
        return DeliveryStatus.goingToClient;
      case 'DELIVERED':
        return DeliveryStatus.delivered;
      default:
        return DeliveryStatus.goingToVendor;
    }
  }
}
