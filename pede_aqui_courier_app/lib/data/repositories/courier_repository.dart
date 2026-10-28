import '../datasources/courier_data_source.dart';
import '../models/courier_profile.dart';
import '../models/delivery_models.dart';
import '../models/earnings_models.dart';

abstract interface class CourierRepository {
  Future<CourierProfile> getProfile();
  Future<Delivery> getActiveDelivery();
  Future<List<AvailableJob>> getAvailableJobs();
  Future<EarningSummary> getEarningSummary();
  Future<List<WeeklyEarning>> getWeeklyEarnings();
  Future<List<EarningRecord>> getEarningHistory();
  Future<List<NotificationItem>> getNotifications();
  Future<void> acceptJob(String jobId);
  Future<void> rejectJob(String jobId);
  Future<void> updateAvailability(bool isAvailable);
  Future<void> confirmDelivery({required String deliveryId, required String otpCode, required bool hasProofPhoto});
}

class CourierRepositoryImpl implements CourierRepository {
  CourierRepositoryImpl(this._dataSource);

  final CourierDataSource _dataSource;

  @override
  Future<CourierProfile> getProfile() => _dataSource.getProfile();

  @override
  Future<Delivery> getActiveDelivery() => _dataSource.getActiveDelivery();

  @override
  Future<List<AvailableJob>> getAvailableJobs() => _dataSource.getAvailableJobs();

  @override
  Future<EarningSummary> getEarningSummary() => _dataSource.getEarningSummary();

  @override
  Future<List<WeeklyEarning>> getWeeklyEarnings() => _dataSource.getWeeklyEarnings();

  @override
  Future<List<EarningRecord>> getEarningHistory() => _dataSource.getEarningHistory();

  @override
  Future<List<NotificationItem>> getNotifications() => _dataSource.getNotifications();

  @override
  Future<void> acceptJob(String jobId) => _dataSource.acceptJob(jobId);

  @override
  Future<void> rejectJob(String jobId) => _dataSource.rejectJob(jobId);

  @override
  Future<void> updateAvailability(bool isAvailable) => _dataSource.updateAvailability(isAvailable);

  @override
  Future<void> confirmDelivery({required String deliveryId, required String otpCode, required bool hasProofPhoto}) {
    return _dataSource.confirmDelivery(deliveryId: deliveryId, otpCode: otpCode, hasProofPhoto: hasProofPhoto);
  }
}
