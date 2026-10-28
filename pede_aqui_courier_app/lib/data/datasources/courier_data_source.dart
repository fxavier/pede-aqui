import '../models/courier_profile.dart';
import '../models/delivery_models.dart';
import '../models/earnings_models.dart';

abstract interface class CourierDataSource {
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
