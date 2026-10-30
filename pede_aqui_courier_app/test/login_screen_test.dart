import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get_it/get_it.dart';
import 'package:pede_aqui_courier_app/app.dart';
import 'package:pede_aqui_courier_app/core/auth/oidc_session.dart';
import 'package:pede_aqui_courier_app/data/repositories/auth_repository.dart';
import 'package:pede_aqui_courier_app/presentation/cubits/auth/auth_cubit.dart';
import 'package:pede_aqui_courier_app/presentation/screens/login_screen.dart';

class RecordingAuthRepository implements AuthRepository {
  int logins = 0;
  Object? loginError;

  @override
  Future<({String accessToken, String courierId})> login() async {
    logins++;
    final error = loginError;
    if (error != null) throw error;
    return (accessToken: 'acc', courierId: 'courier-1');
  }

  @override
  Future<({String accessToken, String courierId})?> restoreSession() async => null;

  @override
  Future<void> logout() async {}
}

Future<void> pumpLogin(WidgetTester tester, RecordingAuthRepository repo) async {
  GetIt.instance.registerFactory<AuthCubit>(() => AuthCubit(repo));
  addTearDown(GetIt.instance.reset);
  await tester.pumpWidget(
    MaterialApp(
      home: const LoginScreen(),
      routes: {AppRoutes.shell: (_) => const Text('shell-screen')},
    ),
  );
  await tester.pump();
}

void main() {
  testWidgets('has no phone/password fields and starts the PKCE flow', (tester) async {
    final repo = RecordingAuthRepository();
    await pumpLogin(tester, repo);

    expect(find.byType(TextField), findsNothing);
    await tester.tap(find.text('Entrar com Pede Aqui'));
    await tester.pumpAndSettle();

    expect(repo.logins, 1);
    expect(find.text('shell-screen'), findsOneWidget);
  });

  testWidgets('cancelling the browser returns to the idle login screen', (tester) async {
    final repo = RecordingAuthRepository()..loginError = const AuthCancelledException();
    await pumpLogin(tester, repo);

    await tester.tap(find.text('Entrar com Pede Aqui'));
    await tester.pumpAndSettle();

    expect(find.text('shell-screen'), findsNothing);
    expect(find.textContaining('Não foi possível'), findsNothing);
    expect(find.text('Entrar com Pede Aqui'), findsOneWidget);
  });

  testWidgets('a failed sign-in shows an error', (tester) async {
    final repo = RecordingAuthRepository()..loginError = Exception('boom');
    await pumpLogin(tester, repo);

    await tester.tap(find.text('Entrar com Pede Aqui'));
    await tester.pumpAndSettle();

    expect(find.text('Não foi possível iniciar sessão. Tente novamente.'), findsOneWidget);
  });
}
