import 'package:flutter/material.dart';

import '../widgets/bottom_navigation.dart';
import 'earnings_screen.dart';
import 'history_screen.dart';
import 'home_dashboard_screen.dart';
import 'profile_screen.dart';

class ShellScreen extends StatefulWidget {
  const ShellScreen({super.key});

  @override
  State<ShellScreen> createState() => _ShellScreenState();
}

class _ShellScreenState extends State<ShellScreen> {
  int _index = 0;

  final List<Widget> _screens = const [
    HomeDashboardScreen(),
    HistoryScreen(),
    EarningsScreen(),
    ProfileScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _index, children: _screens),
      bottomNavigationBar: PedeAquiBottomNavigation(currentIndex: _index, onChanged: (value) => setState(() => _index = value)),
    );
  }
}
