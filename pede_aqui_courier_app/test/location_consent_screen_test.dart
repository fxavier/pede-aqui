import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_courier_app/core/location/location_buffer_store.dart';
import 'package:pede_aqui_courier_app/core/location/location_sender.dart';
import 'package:pede_aqui_courier_app/presentation/cubits/location_consent/location_consent_cubit.dart';
import 'package:pede_aqui_courier_app/presentation/screens/location_consent_screen.dart';

import 'support/location_fakes.dart';

/// Pumps a launcher that pushes the consent screen and records what it pops.
Future<List<Object?>> pumpConsent(WidgetTester tester, LocationConsentCubit cubit) async {
  final results = <Object?>[];
  tester.view.physicalSize = const Size(800, 2000); // the whole screen (both buttons) without scrolling
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    BlocProvider.value(
      value: cubit,
      child: MaterialApp(
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () async => results.add(
              await Navigator.of(context).push<bool>(MaterialPageRoute(builder: (_) => const LocationConsentScreen())),
            ),
            child: const Text('open'),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('open'));
  await tester.pumpAndSettle();
  return results;
}

void main() {
  late FakeLocationApi api;
  late LocationSender sender;

  // Built inside each test body: futures created in setUp live outside testWidgets' fake-async zone and their
  // callbacks would never run during pump().
  LocationSender makeSender() {
    final created = LocationSender(source: FakeLocationSource(), api: api, store: InMemoryLocationBufferStore());
    addTearDown(created.dispose);
    return sender = created;
  }

  setUp(() => api = FakeLocationApi()..currentVersion = '2026-10-v1');

  testWidgets('shows the pt-PT text and the server version; accepting PUTs that version and pops true', (tester) async {
    final results = await pumpConsent(tester, LocationConsentCubit(api, makeSender()));

    expect(find.text('Precisamos da sua localização enquanto está em serviço'), findsOneWidget);
    expect(find.textContaining('30 dias'), findsOneWidget);
    expect(find.text('Versão do texto: 2026-10-v1'), findsOneWidget);

    await tester.tap(find.text('Aceitar e continuar'));
    await tester.pumpAndSettle();

    expect(api.acceptedVersions, ['2026-10-v1']);
    expect(results, [true]);
    expect(sender.hasConsent, isTrue);
  });

  testWidgets('"Agora não" pops false without recording consent', (tester) async {
    final results = await pumpConsent(tester, LocationConsentCubit(api, makeSender()));

    await tester.tap(find.text('Agora não'));
    await tester.pumpAndSettle();

    expect(api.acceptedVersions, isEmpty);
    expect(results, [false]);
    expect(sender.hasConsent, isFalse);
  });

  testWidgets('409 location_consent_outdated reloads the new version and asks again', (tester) async {
    final cubit = LocationConsentCubit(api, makeSender());
    final results = await pumpConsent(tester, cubit);

    api.currentVersion = '2026-11-v2';
    api.acceptError = problem(409, 'location_consent_outdated');
    await tester.tap(find.text('Aceitar e continuar'));
    await tester.pumpAndSettle();

    expect(results, isEmpty, reason: 'still on the consent screen');
    expect(find.text('Versão do texto: 2026-11-v2'), findsOneWidget);
    expect(find.textContaining('foi actualizado'), findsOneWidget);
  });

  test('withdrawing mid-delivery (409 courier_has_active_delivery) keeps the consent and explains why', () async {
    api.valid = true;
    final cubit = LocationConsentCubit(api, makeSender());
    await cubit.load();
    api.withdrawError = problem(409, 'courier_has_active_delivery');

    expect(await cubit.revoke(), isFalse);
    expect(cubit.state.isValid, isTrue);
    expect(cubit.state.errorMessage, contains('Termine a entrega em curso'));
    expect(sender.hasConsent, isTrue);
  });

  test('withdrawing stops the sender', () async {
    api.valid = true;
    final cubit = LocationConsentCubit(api, makeSender());
    await cubit.load();

    expect(await cubit.revoke(), isTrue);
    expect(api.withdrawals, 1);
    expect(cubit.state.isValid, isFalse);
    expect(sender.hasConsent, isFalse);
    expect(sender.onDuty, isFalse);
  });
}
