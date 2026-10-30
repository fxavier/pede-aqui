import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import 'location_point.dart';

/// Persists the not-yet-sent GPS buffer so points recorded offline survive an app restart.
abstract interface class LocationBufferStore {
  Future<List<LocationPoint>> load();
  Future<void> save(List<LocationPoint> points);
  Future<void> clear();
}

/// Simple store on shared_preferences: the whole (bounded, small) buffer as one JSON string.
class SharedPrefsLocationBufferStore implements LocationBufferStore {
  static const _key = 'courier_location_buffer_v1';

  @override
  Future<List<LocationPoint>> load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_key);
    if (raw == null) return [];
    try {
      return (jsonDecode(raw) as List).map((e) => LocationPoint.fromJson(e as Map<String, dynamic>)).toList();
    } catch (_) {
      await prefs.remove(_key); // corrupt entry: drop it rather than crash every start
      return [];
    }
  }

  @override
  Future<void> save(List<LocationPoint> points) async {
    final prefs = await SharedPreferences.getInstance();
    if (points.isEmpty) {
      await prefs.remove(_key);
    } else {
      await prefs.setString(_key, jsonEncode(points.map((p) => p.toJson()).toList()));
    }
  }

  @override
  Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_key);
  }
}

/// In-memory store (USE_MOCK_DATA and tests).
class InMemoryLocationBufferStore implements LocationBufferStore {
  List<LocationPoint> points = [];

  @override
  Future<List<LocationPoint>> load() async => List.of(points);

  @override
  Future<void> save(List<LocationPoint> points) async => this.points = List.of(points);

  @override
  Future<void> clear() async => points = [];
}
