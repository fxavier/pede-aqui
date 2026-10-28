import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/providers/app_settings_provider.dart';
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
        ],
      ),
    );
  }
}
