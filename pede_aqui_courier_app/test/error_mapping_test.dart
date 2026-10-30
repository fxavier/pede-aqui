import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_courier_app/app.dart';
import 'package:pede_aqui_courier_app/core/location/location_buffer_store.dart';
import 'package:pede_aqui_courier_app/core/location/location_sender.dart';
import 'package:pede_aqui_courier_app/core/network/api_client.dart';
import 'package:pede_aqui_courier_app/data/models/courier_profile.dart';
import 'package:pede_aqui_courier_app/data/models/delivery_models.dart';
import 'package:pede_aqui_courier_app/data/models/earnings_models.dart';
import 'package:pede_aqui_courier_app/data/repositories/courier_repository.dart';
import 'package:pede_aqui_courier_app/presentation/app_event_handlers.dart';
import 'package:pede_aqui_courier_app/presentation/cubits/dashboard/dashboard_cubit.dart';
import 'package:pede_aqui_courier_app/presentation/cubits/delivery/delivery_cubit.dart';
import 'package:pede_aqui_courier_app/presentation/cubits/location_consent/location_consent_cubit.dart';

import 'support/location_fakes.dart';

const _delivery = Delivery(
  id: 'delivery-1',
  reference: '#PA-1',
  customerName: 'Cliente',
  vendor: DeliveryLocation(name: 'Loja', address: 'Rua', district: 'Polana', phone: ''),
  destination: DeliveryLocation(name: 'Cliente', address: 'Av.', district: 'Baixa', phone: ''),
  status: DeliveryStatus.goingToClient,
  estimatedMinutes: 10,
  expectedEarning: 100,
  paymentMethod: 'Pago',
  items: [],
  distanceKm: 1,
  pickupCompleted: true,
);

/// Repository fake: [confirmError]/[acceptError] are thrown by the matching calls.
class FakeCourierRepository implements CourierRepository {
  Object? confirmError;
  Object? acceptError;
  String status = 'AVAILABLE';
  final List<bool> availabilityCalls = [];

  @override
  Future<CourierProfile> getProfile() async => CourierProfile(
        id: 'c1',
        name: 'Estafeta',
        phone: '',
        city: '',
        vehicle: '',
        rating: 0,
        totalDeliveries: 0,
        completedToday: 0,
        status: status,
      );

  @override
  Future<Delivery> getActiveDelivery() async => _delivery;

  @override
  Future<List<AvailableJob>> getAvailableJobs() async => const [
        AvailableJob(
          id: 'job-1',
          vendorName: 'Loja',
          distanceKm: 1,
          pickupDistrict: 'A',
          destinationDistrict: 'B',
          estimatedEarning: 10,
          category: 'Restaurante',
        ),
      ];

  @override
  Future<void> confirmDelivery({required String deliveryId, required String otpCode, required bool hasProofPhoto}) async {
    final error = confirmError;
    if (error != null) throw error;
  }

  @override
  Future<void> acceptJob(String jobId) async {
    final error = acceptError;
    if (error != null) throw error;
  }

  @override
  Future<void> updateAvailability(bool isAvailable) async => availabilityCalls.add(isAvailable);

  @override
  Future<EarningSummary> getEarningSummary() async => throw UnimplementedError();
  @override
  Future<List<WeeklyEarning>> getWeeklyEarnings() async => const [];
  @override
  Future<List<EarningRecord>> getEarningHistory() async => const [];
  @override
  Future<List<NotificationItem>> getNotifications() async => const [];
  @override
  Future<void> rejectJob(String jobId) async {}
}

Future<DeliveryCubit> readyToConfirm(FakeCourierRepository repo) async {
  final cubit = DeliveryCubit(repo);
  await cubit.loadActiveDelivery();
  cubit
    ..updateOtp('123456')
    ..toggleProofPhoto();
  return cubit;
}

void main() {
  group('delivery completion errors (by problem code)', () {
    test('409 order_not_deliverable flags the delivery and explains it', () async {
      final repo = FakeCourierRepository()..confirmError = problem(409, 'order_not_deliverable');
      final cubit = await readyToConfirm(repo);

      await cubit.confirmDelivery();

      expect(cubit.state.isNotDeliverable, isTrue);
      expect(cubit.state.isConfirmed, isFalse);
      expect(cubit.state.errorMessage, contains('cancelada ou reembolsada'));
      expect(cubit.state.canConfirm, isFalse);
    });

    test('423 delivery_code_locked tells the courier to contact support', () async {
      final repo = FakeCourierRepository()..confirmError = problem(423, 'delivery_code_locked');
      final cubit = await readyToConfirm(repo);

      await cubit.confirmDelivery();

      expect(cubit.state.errorMessage, contains('Contacte o suporte'));
    });

    test('400 invalid_delivery_code asks to check the PIN', () async {
      final repo = FakeCourierRepository()..confirmError = problem(400, 'invalid_delivery_code');
      final cubit = await readyToConfirm(repo);

      await cubit.confirmDelivery();

      expect(cubit.state.errorMessage, contains('Código inválido'));
    });

    test('429 shows the Retry-After wait', () async {
      final repo = FakeCourierRepository()
        ..confirmError = problem(429, 'rate_limited', headers: {
          'retry-after': ['12'],
        });
      final cubit = await readyToConfirm(repo);

      await cubit.confirmDelivery();

      expect(cubit.state.errorMessage, contains('12 s'));
    });
  });

  group('dashboard', () {
    late LocationSender sender;

    setUp(() {
      sender = LocationSender(source: FakeLocationSource(), api: FakeLocationApi(), store: InMemoryLocationBufferStore());
    });
    tearDown(() => sender.dispose());

    test('removeDelivery drops the non-deliverable job from the list', () async {
      final cubit = DashboardCubit(FakeCourierRepository(), sender: sender);
      await cubit.loadDashboard();
      expect(cubit.state.activeDelivery, isNotNull);

      cubit.removeDelivery('delivery-1', message: DashboardCubit.notDeliverableMessage);

      expect(cubit.state.activeDelivery, isNull);
      expect(cubit.state.errorMessage, DashboardCubit.notDeliverableMessage);
    });

    test('accepting a job that became non-deliverable removes it', () async {
      final repo = FakeCourierRepository()..acceptError = problem(409, 'order_not_deliverable');
      final cubit = DashboardCubit(repo, sender: sender);
      await cubit.loadDashboard();

      await cubit.acceptJob('job-1');

      expect(cubit.state.availableJobs, isEmpty);
    });

    test('duty follows the server status / active delivery', () async {
      final repo = FakeCourierRepository()..status = 'OFFLINE';
      final cubit = DashboardCubit(repo, sender: sender);
      await cubit.loadDashboard();
      expect(cubit.state.isAvailable, isFalse);
      expect(sender.onDuty, isTrue, reason: 'an active delivery keeps the courier on duty');

      cubit.removeDelivery('delivery-1');
      await Future<void>.delayed(Duration.zero);
      expect(sender.onDuty, isFalse);
    });

    test('a courier_off_duty answer from the GPS endpoint flips the toggle off', () async {
      final api = FakeLocationApi()..errors.add(problem(409, 'courier_off_duty'));
      final source = FakeLocationSource();
      final gps = LocationSender(source: source, api: api, store: InMemoryLocationBufferStore());
      addTearDown(gps.dispose);
      final cubit = DashboardCubit(FakeCourierRepository(), sender: gps);
      await cubit.loadDashboard();
      await gps.setConsent(true);
      source.emit(point(DateTime.now().toUtc()));
      await Future<void>.delayed(Duration.zero);

      await gps.flush();
      await Future<void>.delayed(Duration.zero);

      expect(cubit.state.isAvailable, isFalse);
      expect(gps.isRunning, isFalse);
    });
  });

  testWidgets('ApiClient.onSessionExpired stops GPS and returns to the login screen', (tester) async {
    final api = FakeLocationApi()..valid = true;
    final sender = LocationSender(source: FakeLocationSource(), api: api, store: InMemoryLocationBufferStore());
    addTearDown(sender.dispose);
    final client = ApiClient();
    final subscription = bindAppEventHandlers(
      apiClient: client,
      sender: sender,
      consentCubit: LocationConsentCubit(api, sender),
    );
    addTearDown(subscription.cancel);
    await tester.runAsync(() async {
      await sender.setConsent(true);
      await sender.setOnDuty(true);
    });
    expect(sender.isRunning, isTrue);

    await tester.pumpWidget(MaterialApp(
      navigatorKey: appNavigatorKey,
      scaffoldMessengerKey: appMessengerKey,
      initialRoute: AppRoutes.shell,
      routes: {
        AppRoutes.login: (_) => const Scaffold(body: Text('login-screen')),
        AppRoutes.shell: (_) => const Scaffold(body: Text('shell-screen')),
      },
    ));
    expect(find.text('shell-screen'), findsOneWidget);

    await tester.runAsync(() async {
      client.onSessionExpired!();
      await Future<void>.delayed(const Duration(milliseconds: 10));
    });
    await tester.pumpAndSettle();

    expect(find.text('login-screen'), findsOneWidget);
    expect(find.text('shell-screen'), findsNothing);
    expect(find.text('A sua sessão expirou. Entre novamente.'), findsOneWidget);
    expect(sender.isRunning, isFalse);
  });
}
