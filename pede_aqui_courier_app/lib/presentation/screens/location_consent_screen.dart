import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../core/constants/app_colors.dart';
import '../cubits/location_consent/location_consent_cubit.dart';
import '../cubits/location_consent/location_consent_state.dart';
import '../widgets/app_card.dart';
import '../widgets/app_top_bar.dart';

/// Explicit, versioned GPS consent (LAC-40). Shown at onboarding (first start without a valid consent), when the
/// courier tries to go online without it, and when the server answers `location_consent_required`.
/// Pops `true` once the current version is accepted, `false`/null otherwise.
class LocationConsentScreen extends StatefulWidget {
  const LocationConsentScreen({super.key});

  @override
  State<LocationConsentScreen> createState() => _LocationConsentScreenState();
}

class _LocationConsentScreenState extends State<LocationConsentScreen> {
  @override
  void initState() {
    super.initState();
    // Always re-read: the server may have bumped the text version.
    context.read<LocationConsentCubit>().load();
  }

  Future<void> _accept() async {
    final navigator = Navigator.of(context);
    final ok = await context.read<LocationConsentCubit>().accept();
    if (ok && mounted) navigator.pop(true);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const AppTopBar(title: 'Partilha de localização', showBack: true),
      body: BlocBuilder<LocationConsentCubit, LocationConsentState>(
        builder: (context, state) {
          final consent = state.consent;
          return ListView(
            padding: const EdgeInsets.fromLTRB(18, 18, 18, 32),
            children: [
              const Icon(Icons.location_on_rounded, color: AppColors.primary, size: 56),
              const SizedBox(height: 12),
              const Text(
                'Precisamos da sua localização enquanto está em serviço',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900),
              ),
              const SizedBox(height: 18),
              const AppCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _Bullet('A localização GPS só é recolhida quando está em serviço: disponível, ocupado ou com uma entrega em curso. Ao ficar indisponível, a recolha pára.'),
                    _Bullet('É usada para lhe propor entregas próximas, acompanhar a entrega em curso e dar ao cliente uma previsão de chegada.'),
                    _Bullet('Enquanto estiver em serviço, o Android mostra uma notificação permanente a indicar que a localização está a ser partilhada.'),
                    _Bullet('O percurso das entregas é guardado durante 30 dias e depois apagado. A última posição é apagada quando deixa de estar em serviço.'),
                    _Bullet('Pode retirar este consentimento a qualquer momento em Perfil → Definições. Sem ele não pode ficar disponível para entregas.'),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              if (consent != null)
                Text(
                  'Versão do texto: ${consent.currentVersion}',
                  key: const Key('consent-version'),
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppColors.onSurfaceVariant),
                ),
              if (state.errorMessage != null) ...[
                const SizedBox(height: 12),
                Text(state.errorMessage!, textAlign: TextAlign.center, style: const TextStyle(color: AppColors.error)),
              ],
              const SizedBox(height: 20),
              FilledButton(
                onPressed: consent == null || state.isSubmitting || state.isLoading ? null : _accept,
                child: state.isSubmitting
                    ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Text('Aceitar e continuar'),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: state.isSubmitting ? null : () => Navigator.of(context).pop(false),
                child: const Text('Agora não'),
              ),
              if (state.isLoading && consent == null)
                const Padding(padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator())),
              if (!state.isLoading && consent == null && state.errorMessage != null)
                TextButton(
                  onPressed: () => context.read<LocationConsentCubit>().load(),
                  child: const Text('Tentar novamente'),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _Bullet extends StatelessWidget {
  const _Bullet(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.only(top: 3, right: 10),
            child: Icon(Icons.check_circle_outline, size: 18, color: AppColors.secondary),
          ),
          Expanded(child: Text(text, style: const TextStyle(height: 1.4))),
        ],
      ),
    );
  }
}
