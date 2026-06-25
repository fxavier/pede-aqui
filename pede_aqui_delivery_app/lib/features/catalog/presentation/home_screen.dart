import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../app/pede_aqui_app.dart';
import '../../../core/constants/app_colors.dart';
import '../../../core/constants/app_shadows.dart';
import '../../../core/constants/app_spacing.dart';
import '../../../shared/widgets/app_bottom_nav.dart';
import '../../../shared/widgets/screen_shell.dart';
import '../../../shared/widgets/section_header.dart';
import '../../../shared/widgets/status_chip.dart';
import '../../../shared/widgets/vendor_card.dart';
import 'catalog_cubit.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ScreenShell(
      bottomNavigationBar: const AppBottomNav(currentIndex: 0),
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 116),
      child: BlocBuilder<CatalogCubit, CatalogState>(
        builder: (context, state) {
          final vendors = state.vendors;
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const StatusChip(label: 'Av. Julius Nyerere', icon: Icons.location_on_rounded),
                  const Spacer(),
                  IconButton(onPressed: () {}, icon: const Icon(Icons.notifications_none_rounded)),
                  Stack(
                    children: [
                      IconButton(onPressed: () => Navigator.pushNamed(context, AppRoutes.cart), icon: const Icon(Icons.shopping_cart_outlined)),
                      Positioned(
                        right: 8,
                        top: 9,
                        child: Container(width: 8, height: 8, decoration: const BoxDecoration(color: AppColors.primary, shape: BoxShape.circle)),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 24),
              const Text('Bom dia, Felix!', style: TextStyle(fontFamily: 'Fraunces', fontSize: 26, fontWeight: FontWeight.w800)),
              const SizedBox(height: 16),
              Row(
                children: [
                  const Expanded(
                    child: TextField(
                      decoration: InputDecoration(
                        prefixIcon: Icon(Icons.search_rounded),
                        hintText: 'O que quer pedir hoje?',
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  SizedBox(
                    height: 56,
                    child: FilledButton(
                      onPressed: () {},
                      style: FilledButton.styleFrom(backgroundColor: AppColors.primary, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadii.md))),
                      child: const Text('Buscar', style: TextStyle(fontWeight: FontWeight.w800)),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 22),
              SizedBox(
                height: 92,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  itemCount: state.categories.length,
                  separatorBuilder: (_, __) => const SizedBox(width: 12),
                  itemBuilder: (_, index) {
                    final category = state.categories[index];
                    return SizedBox(
                      width: 78,
                      child: Column(
                        children: [
                          Container(
                            width: 58,
                            height: 58,
                            decoration: BoxDecoration(color: category.color.withValues(alpha: 0.13), borderRadius: BorderRadius.circular(AppRadii.lg)),
                            child: Icon(category.icon, color: category.color),
                          ),
                          const SizedBox(height: 8),
                          Text(category.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800)),
                        ],
                      ),
                    );
                  },
                ),
              ),
              const SectionHeader(title: 'Abertos Agora', actionLabel: 'Ver todos'),
              const SizedBox(height: 10),
              SizedBox(
                height: 182,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  itemCount: vendors.take(2).length,
                  separatorBuilder: (_, __) => const SizedBox(width: 14),
                  itemBuilder: (_, index) => VendorCard(vendor: vendors[index], horizontal: true),
                ),
              ),
              const SizedBox(height: 22),
              const SectionHeader(title: 'Populares perto de si'),
              const SizedBox(height: 12),
              ...vendors.skip(2).map(
                    (vendor) => Padding(
                      padding: const EdgeInsets.only(bottom: 14),
                      child: VendorCard(vendor: vendor),
                    ),
                  ),
              Container(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [AppColors.forest, AppColors.forestLight],
                  ),
                  borderRadius: BorderRadius.circular(AppRadii.card),
                  boxShadow: AppShadows.warmMd,
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text('Farmácias 24h', style: TextStyle(fontFamily: 'Fraunces', color: Colors.white, fontSize: 18, fontWeight: FontWeight.w800)),
                          const SizedBox(height: 6),
                          Text('Entregamos os seus medicamentos em menos de 30 minutos.', style: TextStyle(color: Colors.white.withValues(alpha: 0.7))),
                        ],
                      ),
                    ),
                    Icon(Icons.local_hospital_rounded, color: Colors.white.withValues(alpha: 0.2), size: 76),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
