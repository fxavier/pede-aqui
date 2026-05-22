# Pede Aqui Courier App — Flutter

Aplicação mobile para estafetas do Pede Aqui em Moçambique, baseada nos ecrãs HTML/PNG fornecidos.

## Stack

- Flutter + Dart
- BLoC/Cubit (`flutter_bloc`)
- GetIt para dependency injection
- Provider para definições globais da aplicação
- Dio preparado para integração futura com API
- Mock data para desenvolvimento local
- Design mobile-first inspirado nas telas anexadas

## Localização e idioma

- Idioma da UI: Português de Portugal
- Local operacional: Maputo, Moçambique
- Moeda exibida: Metical moçambicano — `MT`, com ISO `MZN` no código

## Ecrãs implementados

- Início / Painel do estafeta
- Detalhes da entrega activa
- Confirmação de entrega com OTP e prova fotográfica
- Ganhos
- Histórico
- Perfil
- Carteira e levantamentos
- Notificações
- Definições
- Login
- Onboarding

## Estrutura

```text
lib/
  core/
    constants/       # cores e espaçamentos
    di/              # GetIt
    network/         # ApiClient Dio
    providers/       # Provider global
    theme/           # tema Material 3
    utils/           # formatação MZN/MT
  data/
    datasources/     # mock e remote datasource
    models/          # modelos da app
    repositories/    # repository contract + impl
  presentation/
    cubits/          # BLoC/Cubit state management
    screens/         # ecrãs
    widgets/         # componentes reutilizáveis
```

## Como executar

Se o projecto ainda não tiver pastas nativas Android/iOS, execute:

```bash
flutter create . --platforms=android,ios,web
flutter pub get
flutter run
```

Se já tiver plataformas geradas:

```bash
flutter pub get
flutter run
```

## Preparado para API

Actualmente a app usa `MockCourierDataSource`. Para ligar a API real:

1. Implementar mapeamento JSON em `RemoteCourierDataSource`.
2. Alterar `lib/core/di/injection.dart` para registar `RemoteCourierDataSource` em vez de `MockCourierDataSource`.
3. Ajustar `ApiClient._defaultBaseUrl` para o endpoint real.

Os ecrãs dependem apenas de `CourierRepository`, portanto a UI não precisa ser alterada.

## Validação local

```bash
flutter analyze
flutter test
python3 tool/validate_project.py
```
