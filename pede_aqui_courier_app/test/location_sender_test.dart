import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_courier_app/core/location/location_buffer_store.dart';
import 'package:pede_aqui_courier_app/core/location/location_sender.dart';
import 'package:pede_aqui_courier_app/core/location/location_source.dart';
import 'package:pede_aqui_courier_app/core/network/api_error.dart';

import 'support/location_fakes.dart';

Future<void> settle() => Future<void>.delayed(Duration.zero);

void main() {
  late FakeLocationSource source;
  late FakeLocationApi api;
  late InMemoryLocationBufferStore store;
  late DateTime now;
  late List<LocationSenderEvent> events;

  LocationSender build({int flushThreshold = 1000, int maxBuffered = 300}) {
    final sender = LocationSender(
      source: source,
      api: api,
      store: store,
      clock: () => now,
      flushInterval: const Duration(hours: 1), // tests drive flush() explicitly
      flushThreshold: flushThreshold,
      maxBuffered: maxBuffered,
    );
    sender.events.listen(events.add);
    addTearDown(sender.dispose);
    return sender;
  }

  Future<LocationSender> running({int flushThreshold = 1000, int maxBuffered = 300}) async {
    final sender = build(flushThreshold: flushThreshold, maxBuffered: maxBuffered);
    await sender.setConsent(true);
    await sender.setOnDuty(true);
    return sender;
  }

  setUp(() {
    source = FakeLocationSource();
    api = FakeLocationApi();
    store = InMemoryLocationBufferStore();
    now = DateTime.utc(2026, 10, 30, 12);
    events = [];
  });

  group('duty / consent gating', () {
    test('collects only when on duty AND consented', () async {
      final sender = build();
      await sender.setOnDuty(true);
      expect(sender.isRunning, isFalse, reason: 'no consent yet');
      expect(source.accessChecks, 0);

      await sender.setConsent(true);
      expect(sender.isRunning, isTrue);
      expect(source.isListening, isTrue);

      await sender.setOnDuty(false);
      expect(sender.isRunning, isFalse);
      expect(source.isListening, isFalse);
    });

    test('going off duty discards buffered points and the persisted copy, and flush sends nothing', () async {
      final sender = await running();
      source.emit(point(now));
      await settle();
      expect(store.points, hasLength(1));

      await sender.setOnDuty(false);
      await sender.flush();

      expect(sender.buffered, isEmpty);
      expect(store.points, isEmpty);
      expect(api.batches, isEmpty);
    });

    test('permission refused: not running and the UI is told', () async {
      source.access = LocationAccess.deniedForever;
      final sender = await running();
      await settle();
      expect(sender.isRunning, isFalse);
      expect(events, [LocationSenderEvent.permissionDenied]);
    });

    test('reset (logout) stops and wipes the persisted buffer', () async {
      final sender = await running();
      source.emit(point(now));
      await settle();
      await sender.reset();
      expect(sender.isRunning, isFalse);
      expect(sender.onDuty, isFalse);
      expect(sender.hasConsent, isFalse);
      expect(store.points, isEmpty);
    });
  });

  group('batching', () {
    test('never sends more than 50 points per request', () async {
      final sender = await running();
      for (var i = 0; i < 60; i++) {
        source.emit(point(now.subtract(Duration(seconds: 60 - i))));
      }
      await settle();

      await sender.flush();
      await sender.flush();

      expect(api.batches.map((b) => b.length), [50, 10]);
      expect(sender.buffered, isEmpty);
    });

    test('flushes as soon as the threshold is reached', () async {
      await running(flushThreshold: 5);
      for (var i = 0; i < 5; i++) {
        source.emit(point(now.subtract(Duration(seconds: 5 - i))));
      }
      await settle();
      await settle();

      expect(api.batches, hasLength(1));
      expect(api.batches.single, hasLength(5));
    });

    test('drops points the server would reject as too old before sending', () async {
      final sender = await running();
      source.emit(point(now.subtract(const Duration(minutes: 6))));
      source.emit(point(now.subtract(const Duration(seconds: 10))));
      await settle();

      await sender.flush();

      expect(api.batches.single, hasLength(1));
      expect(api.batches.single.single.recordedAt, now.subtract(const Duration(seconds: 10)));
    });

    test('the buffer is bounded (oldest dropped first)', () async {
      final sender = await running(maxBuffered: 3);
      for (var i = 0; i < 5; i++) {
        source.emit(point(now.subtract(Duration(seconds: 10 - i))));
      }
      await settle();
      expect(sender.buffered.map((p) => p.recordedAt), [
        now.subtract(const Duration(seconds: 8)),
        now.subtract(const Duration(seconds: 7)),
        now.subtract(const Duration(seconds: 6)),
      ]);
    });
  });

  group('offline buffering', () {
    test('keeps and persists the points while offline, then sends them when the network is back', () async {
      final sender = await running();
      source.emit(point(now.subtract(const Duration(seconds: 3))));
      source.emit(point(now.subtract(const Duration(seconds: 2))));
      await settle();

      api.errors.add(offline());
      await sender.flush();
      expect(api.batches, isEmpty);
      expect(sender.buffered, hasLength(2));
      expect(store.points, hasLength(2), reason: 'persisted for an app restart');
      expect(sender.retryNotBefore, now.add(const Duration(seconds: 15)));

      now = now.add(const Duration(seconds: 16));
      await sender.flush();
      expect(api.batches.single, hasLength(2));
      expect(sender.buffered, isEmpty);
      expect(store.points, isEmpty);
    });

    test('points persisted by a previous run are sent after a restart', () async {
      store.points = [point(now.subtract(const Duration(seconds: 30)))];
      final sender = await running();
      await sender.flush();
      expect(api.batches.single, hasLength(1));
    });

    test('a persisted buffer survives the app start while duty/consent are still unknown', () async {
      store.points = [point(now.subtract(const Duration(seconds: 30)))];
      final sender = build();
      await sender.setOnDuty(true); // consent not loaded yet -> stop path
      expect(store.points, hasLength(1));
    });
  });

  group('server answers', () {
    test('429 waits for Retry-After before the next upload', () async {
      final sender = await running();
      source.emit(point(now.subtract(const Duration(seconds: 1))));
      await settle();

      api.errors.add(problem(429, 'rate_limited', headers: {
        'retry-after': ['20'],
      }));
      await sender.flush();
      expect(sender.retryNotBefore, now.add(const Duration(seconds: 20)));

      now = now.add(const Duration(seconds: 10));
      await sender.flush();
      expect(api.batches, isEmpty, reason: 'still inside Retry-After');

      now = now.add(const Duration(seconds: 11));
      await sender.flush();
      expect(api.batches, hasLength(1));
    });

    test('409 location_consent_required stops the sender and asks for the consent screen', () async {
      final sender = await running();
      source.emit(point(now));
      await settle();
      api.errors.add(problem(409, 'location_consent_required'));

      await sender.flush();
      await settle();

      expect(sender.isRunning, isFalse);
      expect(sender.hasConsent, isFalse);
      expect(sender.buffered, isEmpty);
      expect(events, [LocationSenderEvent.consentRequired]);
    });

    test('409 courier_off_duty stops the sender', () async {
      final sender = await running();
      source.emit(point(now));
      await settle();
      api.errors.add(problem(409, 'courier_off_duty'));

      await sender.flush();
      await settle();

      expect(sender.isRunning, isFalse);
      expect(sender.onDuty, isFalse);
      expect(events, [LocationSenderEvent.offDuty]);
    });

    test('400 drops the refused batch instead of retrying it forever', () async {
      final sender = await running();
      source.emit(point(now));
      await settle();
      api.errors.add(problem(400, 'validation_failed'));

      await sender.flush();

      expect(sender.buffered, isEmpty);
      expect(sender.retryNotBefore, isNull);
    });
  });

  group('ApiError', () {
    test('reads code and Retry-After from a problem response', () {
      final error = ApiError.from(problem(429, 'rate_limited', headers: {
        'retry-after': ['7'],
      }));
      expect(error.status, 429);
      expect(error.code, 'rate_limited');
      expect(error.retryAfter, const Duration(seconds: 7));
    });

    test('no response = network error', () {
      final error = ApiError.from(offline());
      expect(error.isNetwork, isTrue);
      expect(error.status, isNull);
    });
  });
}
