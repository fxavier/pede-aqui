import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../app.dart';
import '../../core/providers/app_settings_provider.dart';
import '../cubits/dashboard/dashboard_cubit.dart';
import '../cubits/location_consent/location_consent_cubit.dart';
import '../cubits/location_consent/location_consent_state.dart';
import '../widgets/app_card.dart';
import '../widgets/app_top_bar.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<AppSettingsProvider>();
    return Scaffold(
      appBar: const AppTopBar(title: 'Definições', showBack: true),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(18, 18, 18, 32),
        children: [
          AppCard(
            padding: EdgeInsets.zero,
            child: Column(
              children: [
                SwitchListTile(
                  title: const Text('Notificações push', style: TextStyle(fontWeight: FontWeight.w800)),
                  subtitle: const Text('Receber novas entregas, pagamentos e alertas'),
                  value: settings.notificationsEnabled,
                  onChanged: settings.toggleNotifications,
                ),
                const Divider(height: 1),
                ListTile(
                  title: const Text('Idioma', style: TextStyle(fontWeight: FontWeight.w800)),
                  subtitle: Text(settings.locale),
                  trailing: const Icon(Icons.chevron_right_rounded),
                ),
                const Divider(height: 1),
                const ListTile(
                  title: Text('Localização operacional', style: TextStyle(fontWeight: FontWeight.w800)),
                  subtitle: Text('Maputo, Moçambique'),
                  trailing: Icon(Icons.location_on_outlined),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          const _LocationConsentCard(),
        ],
      ),
    );
  }
}

/// GPS consent status and withdrawal (LAC-40). Withdrawing takes the courier offline (server side) and is refused
/// mid-delivery (409 `courier_has_active_delivery`).
class _LocationConsentCard extends StatelessWidget {
  const _LocationConsentCard();

  Future<void> _revoke(BuildContext context) async {
    final consentCubit = context.read<LocationConsentCubit>();
    final dashboard = context.read<DashboardCubit>();
    final messenger = ScaffoldMessenger.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Retirar consentimento?'),
        content: const Text(
          'Deixa de partilhar a sua localização e fica indisponível. Não poderá receber entregas até voltar a dar o consentimento.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(dialogContext).pop(false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.of(dialogContext).pop(true), child: const Text('Retirar')),
        ],
      ),
    );
    if (confirmed != true) return;
    if (await consentCubit.revoke()) {
      messenger.showSnackBar(const SnackBar(content: Text('Consentimento retirado. Está indisponível.')));
      await dashboard.loadDashboard();
    } else {
      final message = consentCubit.state.errorMessage;
      if (message != null) messenger.showSnackBar(SnackBar(content: Text(message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<LocationConsentCubit, LocationConsentState>(
      builder: (context, state) {
        final consent = state.consent;
        final subtitle = consent == null
            ? (state.isLoading ? 'A verificar…' : 'Estado desconhecido')
            : consent.valid
                ? 'Consentimento dado (versão ${consent.acceptedVersion ?? consent.currentVersion}). Só é recolhida em serviço.'
                : 'Sem consentimento: não pode ficar disponível para entregas.';
        return AppCard(
          padding: EdgeInsets.zero,
          child: ListTile(
            title: const Text('Partilha de localização', style: TextStyle(fontWeight: FontWeight.w800)),
            subtitle: Text(subtitle),
            trailing: state.isSubmitting
                ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                : TextButton(
                    onPressed: consent == null
                        ? () => context.read<LocationConsentCubit>().load()
                        : consent.valid
                            ? () => _revoke(context)
                            : () => Navigator.of(context).pushNamed(AppRoutes.locationConsent),
                    child: Text(consent == null ? 'Verificar' : (consent.valid ? 'Retirar' : 'Dar consentimento')),
                  ),
          ),
        );
      },
    );
  }
}
