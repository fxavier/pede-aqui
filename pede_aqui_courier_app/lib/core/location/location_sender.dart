import 'dart:async';
import 'dart:collection';
import 'dart:math' as math;

import '../../data/datasources/location_data_source.dart';
import '../network/api_error.dart';
import 'location_buffer_store.dart';
import 'location_point.dart';
import 'location_source.dart';

/// Things the UI must react to (navigation, messages); the sender has already stopped itself where relevant.
enum LocationSenderEvent {
  /// 409 `location_consent_required`: show the consent screen.
  consentRequired,

  /// 409 `courier_off_duty`: the server no longer considers the courier on duty.
  offDuty,

  /// The OS refused location access (or location services are off).
  permissionDenied,
}

/// Foreground GPS sender (LAC-40, W6-D).
///
/// Collects fixes only while the courier is on duty **and** has a valid consent (user decision LAC-40), buffers them
/// (bounded, persisted) and posts batches of at most [maxBatch] to `POST /couriers/me/locations` every
/// [flushInterval] or as soon as [flushThreshold] points are waiting. Offline/5xx failures keep the buffer and back
/// off; 429 waits for `Retry-After`; points the server would drop as `too_old` are pruned before sending.
class LocationSender {
  LocationSender({
    required LocationSource source,
    required LocationDataSource api,
    required LocationBufferStore store,
    DateTime Function()? clock,
    this.flushInterval = const Duration(seconds: 15),
    this.flushThreshold = 20,
    this.maxBatch = 50,
    this.maxBuffered = 300,
    this.maxPointAge = const Duration(minutes: 4, seconds: 30),
    this.maxClockSkew = const Duration(minutes: 5),
    this.defaultRetryAfter = const Duration(seconds: 30),
    this.retryBaseDelay = const Duration(seconds: 15),
  })  : _source = source,
        _api = api,
        _store = store,
        _clock = clock ?? DateTime.now;

  final LocationSource _source;
  final LocationDataSource _api;
  final LocationBufferStore _store;
  final DateTime Function() _clock;

  final Duration flushInterval;
  final int flushThreshold;

  /// Server limit: 1..50 points per batch.
  final int maxBatch;

  /// Oldest points are discarded beyond this (offline for a long shift).
  final int maxBuffered;

  /// The server drops points older than 5 min (`too_old`); keep a margin for upload latency.
  final Duration maxPointAge;

  /// The server drops points more than 5 min in the future (`future_timestamp`).
  final Duration maxClockSkew;
  final Duration defaultRetryAfter;

  /// First backoff after an offline/5xx failure; doubles up to 8x.
  final Duration retryBaseDelay;

  final List<LocationPoint> _buffer = [];
  final _events = StreamController<LocationSenderEvent>.broadcast();

  bool _onDuty = false;
  bool _consent = false;
  bool _loaded = false;
  bool _flushing = false;
  int _failures = 0;
  DateTime? _retryNotBefore;
  StreamSubscription<LocationPoint>? _subscription;
  Timer? _timer;
  Future<void> _pending = Future.value();

  Stream<LocationSenderEvent> get events => _events.stream;

  /// True while fixes are being collected (on duty + consent + OS permission).
  bool get isRunning => _subscription != null;
  bool get onDuty => _onDuty;
  bool get hasConsent => _consent;

  /// Points waiting to be sent (copy).
  List<LocationPoint> get buffered => List.unmodifiable(_buffer);

  /// Earliest time the next upload may happen (429 / failure backoff), or null.
  DateTime? get retryNotBefore => _retryNotBefore;

  /// On duty = status AVAILABLE/BUSY or working an accepted delivery.
  Future<void> setOnDuty(bool value) {
    _onDuty = value;
    return _reconcile();
  }

  /// Whether the courier holds a consent to the current text version.
  Future<void> setConsent(bool value) {
    _consent = value;
    return _reconcile();
  }

  /// Logout / session expiry: stop and forget everything, including the persisted buffer.
  Future<void> reset() {
    _onDuty = false;
    _consent = false;
    return _reconcile(wipe: true);
  }

  /// Sends one batch now if allowed (not backing off, something buffered). Safe to call at any time.
  Future<void> flush() async {
    if (_flushing || !_onDuty || !_consent) return;
    final now = _clock();
    final notBefore = _retryNotBefore;
    if (notBefore != null && now.isBefore(notBefore)) return;

    _prune(now);
    if (_buffer.isEmpty) {
      await _persist();
      return;
    }
    final batch = _buffer.take(maxBatch).toList();
    _flushing = true;
    try {
      await _api.sendBatch(batch);
      _removeAll(batch);
      _failures = 0;
      _retryNotBefore = null;
    } catch (error) {
      await _handleFailure(ApiError.from(error), batch, now);
    } finally {
      _flushing = false;
    }
    await _persist();
  }

  Future<void> dispose() async {
    await _stopCollecting();
    await _events.close();
  }

  // --- internals ---------------------------------------------------------------------------------------------

  /// Serialises start/stop so concurrent duty/consent changes never start two streams.
  Future<void> _reconcile({bool wipe = false}) {
    _pending = _pending.then((_) => _apply(wipe: wipe)).catchError((Object _) {});
    return _pending;
  }

  Future<void> _apply({required bool wipe}) async {
    if (_onDuty && _consent) {
      await _start();
      return;
    }
    await _stopCollecting();
    // Off duty / no consent: nothing may be kept or sent later (privacy, LAC-40). Before the first start of this
    // process the persisted buffer is left alone (duty and consent are still being loaded after an app restart);
    // anything stale in it is pruned by age on the next start.
    if (_loaded || wipe) {
      _buffer.clear();
      _retryNotBefore = null;
      _failures = 0;
      await _store.clear();
    }
  }

  Future<void> _start() async {
    if (_subscription != null) return;
    if (!_loaded) {
      _buffer.insertAll(0, await _store.load());
      _loaded = true;
    }
    final access = await _source.ensureAccess();
    if (access != LocationAccess.granted) {
      _emit(LocationSenderEvent.permissionDenied);
      return;
    }
    if (!_onDuty || !_consent) return; // changed while the permission dialog was open
    _subscription = _source.positions().listen(_onPoint, onError: (Object _) {});
    _timer = Timer.periodic(flushInterval, (_) => flush());
  }

  Future<void> _stopCollecting() async {
    _timer?.cancel();
    _timer = null;
    final sub = _subscription;
    _subscription = null;
    await sub?.cancel();
  }

  void _onPoint(LocationPoint point) {
    if (!point.isValid) return;
    _buffer.add(point);
    if (_buffer.length > maxBuffered) {
      _buffer.removeRange(0, _buffer.length - maxBuffered);
    }
    if (_buffer.length >= flushThreshold) {
      flush();
    } else {
      _persist();
    }
  }

  Future<void> _handleFailure(ApiError error, List<LocationPoint> batch, DateTime now) async {
    switch (error.code) {
      case 'location_consent_required':
        _consent = false;
        await _reconcile();
        _emit(LocationSenderEvent.consentRequired);
        return;
      case 'courier_off_duty':
        _onDuty = false;
        await _reconcile();
        _emit(LocationSenderEvent.offDuty);
        return;
    }
    if (error.isRateLimited) {
      _retryNotBefore = now.add(error.retryAfter ?? defaultRetryAfter);
      return;
    }
    final status = error.status;
    if (status != null && status >= 400 && status < 500 && status != 401 && status != 408) {
      // The server refuses this batch as such (validation): retrying it would fail forever.
      _removeAll(batch);
      return;
    }
    // Offline, timeout, 5xx, 401 (the client already tried a refresh): keep the points and back off.
    _failures++;
    final factor = math.pow(2, math.min(_failures - 1, 3)).toInt();
    _retryNotBefore = now.add(retryBaseDelay * factor);
  }

  void _prune(DateTime now) {
    final oldest = now.subtract(maxPointAge);
    final newest = now.add(maxClockSkew);
    _buffer.removeWhere((p) => p.recordedAt.isBefore(oldest) || p.recordedAt.isAfter(newest));
  }

  void _removeAll(List<LocationPoint> batch) {
    final sent = LinkedHashSet<LocationPoint>.identity()..addAll(batch);
    _buffer.removeWhere(sent.contains);
  }

  Future<void> _persist() async {
    try {
      await _store.save(_buffer);
    } catch (_) {
      // Persistence is best effort; the in-memory buffer still works.
    }
  }

  void _emit(LocationSenderEvent event) {
    if (!_events.isClosed) _events.add(event);
  }
}
