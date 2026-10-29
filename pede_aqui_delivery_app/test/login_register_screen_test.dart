import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_mobile/app/pede_aqui_app.dart';
import 'package:pede_aqui_mobile/core/auth/oidc_session.dart';
import 'package:pede_aqui_mobile/features/auth/data/auth_repository.dart';
import 'package:pede_aqui_mobile/features/auth/presentation/auth_cubit.dart';
import 'package:pede_aqui_mobile/features/auth/presentation/login_register_screen.dart';

class RecordingAuthRepository implements AuthRepository {
  int logins = 0;
  final List<String> registeredEmails = [];
  Object? loginError;

  static const user = AuthUser(id: 'u1', name: 'Felix', email: 'felix@example.com', token: 't');

  @override
  Future<AuthUser> login({String? loginHint}) async {
    logins++;
    final error = loginError;
    if (error != null) throw error;
    return user;
  }

  @override
  Future<AuthUser> register({required String name, required String email, required String password}) async {
    registeredEmails.add(email);
    return user;
  }

  @override
  Future<void> logout() async {}

  @override
  Future<AuthUser?> restoreSession() async => null;
}

Future<void> pumpLogin(WidgetTester tester, RecordingAuthRepository repo) async {
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 2.5;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    BlocProvider(
      create: (_) => AuthCubit(repo),
      child: MaterialApp(
        home: const LoginRegisterScreen(),
        routes: {AppRoutes.home: (_) => const Text('home-screen')},
      ),
    ),
  );
}

void main() {
  testWidgets('login tab has no password field and starts the PKCE flow', (tester) async {
    final repo = RecordingAuthRepository();
    await pumpLogin(tester, repo);

    expect(find.byType(TextField), findsNothing);
    await tester.tap(find.text('Entrar com Pede Aqui'));
    await tester.pumpAndSettle();

    expect(repo.logins, 1);
    expect(find.text('home-screen'), findsOneWidget);
  });

  testWidgets('cancelling the browser shows no error', (tester) async {
    final repo = RecordingAuthRepository()..loginError = const AuthCancelledException();
    await pumpLogin(tester, repo);

    await tester.tap(find.text('Entrar com Pede Aqui'));
    await tester.pumpAndSettle();

    expect(find.text('home-screen'), findsNothing);
    expect(find.textContaining('Não foi possível'), findsNothing);
  });

  testWidgets('register enforces the password policy before calling the backend', (tester) async {
    final repo = RecordingAuthRepository();
    await pumpLogin(tester, repo);

    await tester.tap(find.text('Registar'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('register-name')), 'Felix Tester');
    await tester.enterText(find.byKey(const Key('register-email')), 'felix@example.com');
    await tester.enterText(find.byKey(const Key('register-password')), 'password1');
    await tester.tap(find.text('CRIAR CONTA'));
    await tester.pumpAndSettle();

    expect(find.text('A palavra-passe deve ter pelo menos 10 caracteres.'), findsOneWidget);
    expect(repo.registeredEmails, isEmpty);

    await tester.enterText(find.byKey(const Key('register-password')), 'Password123');
    await tester.tap(find.text('CRIAR CONTA'));
    await tester.pumpAndSettle();

    expect(repo.registeredEmails, ['felix@example.com']);
    expect(find.text('home-screen'), findsOneWidget);
  });
}
