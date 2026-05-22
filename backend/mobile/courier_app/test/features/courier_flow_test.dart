import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_courier_app/core/api/api_client.dart';
import 'package:pede_aqui_courier_app/main.dart';

class FakeApiClient extends ApiClient {
  FakeApiClient() : super(baseUrl: 'http://localhost', token: 'token');

  ApiResult<dynamic>? nextGet;
  ApiResult<dynamic>? nextGetEarnings;
  ApiResult<dynamic>? nextPatch;
  ApiResult<dynamic>? nextPost;
  final List<String> postCalls = [];
  final List<String> patchCalls = [];

  @override
  Future<ApiResult<dynamic>> getJson(String path) async {
    if (path == '/couriers/me/earnings-summary') {
      return nextGetEarnings ?? ApiResult.success({'completedDeliveries': 0, 'failedDeliveries': 0, 'earningsTotalMzn': 0});
    }
    return nextGet ?? ApiResult.error('missing fake get response');
  }

  @override
  Future<ApiResult<dynamic>> patchJson(String path, Map<String, dynamic> body) async {
    patchCalls.add(path);
    return nextPatch ?? ApiResult.error('missing fake patch response');
  }

  @override
  Future<ApiResult<dynamic>> postJson(String path, Map<String, dynamic> body) async {
    postCalls.add(path);
    return nextPost ?? ApiResult.success({});
  }
}

void main() {
  group('US3 cubits', () {
    test('courier transitions loading to success', () async {
      final api = FakeApiClient()..nextGet = ApiResult.success({'available': true});
      final cubit = CourierCubit(api);
      await cubit.load();
      expect(cubit.state.available, true);
    });

    test('courier transitions loading to forbidden', () async {
      final api = FakeApiClient()..nextGet = ApiResult.forbidden();
      final cubit = CourierCubit(api);
      await cubit.load();
      expect(cubit.state.forbidden, true);
    });

    test('delivery jobs transitions loading to success and selection', () async {
      final api = FakeApiClient()
        ..nextGet = ApiResult.success([
          {'id': 'dj-1', 'deliveryId': 'd-1', 'status': 'ASSIGNED'}
        ])
        ..nextGetEarnings = ApiResult.success({'completedDeliveries': 3, 'failedDeliveries': 1, 'earningsTotalMzn': 450});
      final cubit = DeliveryCubit(api);
      await cubit.loadJobs();
      expect(cubit.state.jobs.length, 1);
      expect(cubit.state.selectedDeliveryId, 'd-1');
      expect(cubit.state.completedDeliveries, 3);
      cubit.selectDelivery('d-2');
      expect(cubit.state.selectedDeliveryId, 'd-2');
    });

    test('delivery jobs transitions loading to empty', () async {
      final api = FakeApiClient()
        ..nextGet = ApiResult.success([])
        ..nextGetEarnings = ApiResult.success({'completedDeliveries': 0, 'failedDeliveries': 0, 'earningsTotalMzn': 0});
      final cubit = DeliveryCubit(api);
      await cubit.loadJobs();
      expect(cubit.state.jobs, isEmpty);
      expect(cubit.state.selectedDeliveryId, isNull);
    });

    test('delivery accept calls API and reloads jobs', () async {
      final api = FakeApiClient()
        ..nextGet = ApiResult.success([
          {'id': 'dj-1', 'deliveryId': 'd-1', 'status': 'ASSIGNED'}
        ])
        ..nextGetEarnings = ApiResult.success({'completedDeliveries': 0, 'failedDeliveries': 0, 'earningsTotalMzn': 0})
        ..nextPost = ApiResult.success({});
      final cubit = DeliveryCubit(api);

      await cubit.loadJobs();
      await cubit.accept('dj-1');

      expect(api.postCalls, contains('/dispatch-jobs/dj-1/accept'));
      expect(cubit.state.jobs.length, 1);
    });

    test('delivery reject calls API and reloads jobs', () async {
      final api = FakeApiClient()
        ..nextGet = ApiResult.success([
          {'id': 'dj-2', 'deliveryId': 'd-2', 'status': 'ASSIGNED'}
        ])
        ..nextGetEarnings = ApiResult.success({'completedDeliveries': 0, 'failedDeliveries': 0, 'earningsTotalMzn': 0})
        ..nextPost = ApiResult.success({});
      final cubit = DeliveryCubit(api);

      await cubit.loadJobs();
      await cubit.reject('dj-2', 'Indisponivel');

      expect(api.postCalls, contains('/dispatch-jobs/dj-2/reject'));
      expect(cubit.state.jobs.length, 1);
    });

    test('delivery status update and completion transitions are applied', () async {
      final api = FakeApiClient()
        ..nextGet = ApiResult.success([
          {'id': 'dj-3', 'deliveryId': 'd-3', 'status': 'ASSIGNED'}
        ])
        ..nextGetEarnings = ApiResult.success({'completedDeliveries': 0, 'failedDeliveries': 0, 'earningsTotalMzn': 0})
        ..nextPatch = ApiResult.success({})
        ..nextPost = ApiResult.success({});
      final cubit = DeliveryCubit(api);

      await cubit.loadJobs();
      await cubit.updateStatus('PICKED_UP');
      cubit.setCode('123456');
      await cubit.complete(deliveryId: 'd-3', code: '123456');

      expect(api.patchCalls, contains('/deliveries/d-3/status'));
      expect(api.postCalls, contains('/deliveries/d-3/complete'));
      expect(cubit.state.lastSuccessMessage, isNotNull);
      expect(cubit.state.codeInput, isEmpty);
    });
  });
}
