import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../app/pede_aqui_app.dart';
import '../../../core/constants/app_colors.dart';
import '../../../core/constants/app_shadows.dart';
import '../../../core/constants/app_spacing.dart';
import '../../../shared/widgets/app_bottom_nav.dart';
import '../../../shared/widgets/product_tile.dart';
import '../../../shared/widgets/screen_shell.dart';
import '../../../shared/widgets/status_chip.dart';
import 'catalog_cubit.dart';

class StoreScreen extends StatefulWidget {
  const StoreScreen({super.key});

  @override
  State<StoreScreen> createState() => _StoreScreenState();
}

class _StoreScreenState extends State<StoreScreen> {
  @override
  void initState() {
    super.initState();
    context.read<CatalogCubit>().loadStore('avenida-gourmet');
  }

  @override
  Widget build(BuildContext context) {
    return ScreenShell(
      bottomNavigationBar: const AppBottomNav(currentIndex: 1),
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 116),
      appBar: AppBar(
        title: const Text('Pede Aqui', style: TextStyle(fontFamily: 'Fraunces', fontWeight: FontWeight.w800)),
        leading: IconButton(onPressed: () => Navigator.maybePop(context), icon: const Icon(Icons.arrow_back_rounded)),
        actions: [
          IconButton(onPressed: () {}, icon: const Icon(Icons.search_rounded)),
          IconButton(onPressed: () {}, icon: const Icon(Icons.notifications_none_rounded)),
        ],
      ),
      child: BlocBuilder<CatalogCubit, CatalogState>(
        builder: (context, state) {
          final vendor = state.selectedVendor;
          final products = state.products;
          if (state.loading && vendor == null) {
            return const Center(child: CircularProgressIndicator());
          }
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                height: 190,
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [AppColors.forestDark, AppColors.forest, AppColors.forestLight],
                  ),
                  borderRadius: BorderRadius.circular(28),
                  boxShadow: AppShadows.warmMd,
                ),
                child: Stack(
                  children: [
                    const Positioned(right: 34, bottom: 28, child: Text('🍲', style: TextStyle(fontSize: 82))),
                    Positioned(
                      left: 18,
                      right: 18,
                      bottom: 14,
                      child: Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(AppRadii.card),
                          boxShadow: AppShadows.warmMd,
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Row(
                              children: [
                                Expanded(child: Text(vendor?.name ?? 'Avenida Gourmet', style: const TextStyle(fontFamily: 'Fraunces', fontSize: 20, fontWeight: FontWeight.w800))),
                                const StatusChip(label: '17 min', green: true),
                              ],
                            ),
                            const SizedBox(height: 6),
                            Text(vendor?.description ?? 'Grelhados • Comida contemporânea • 2.1 km', style: const TextStyle(color: AppColors.mutedText, fontSize: 12)),
                            const SizedBox(height: 10),
                            Row(
                              children: const [
                                Icon(Icons.star_rounded, color: AppColors.amber600, size: 17),
                                SizedBox(width: 4),
                                Text('4.8', style: TextStyle(fontWeight: FontWeight.w800)),
                                SizedBox(width: 14),
                                Icon(Icons.delivery_dining_rounded, color: AppColors.green, size: 17),
                                SizedBox(width: 4),
                                Text('150 MT', style: TextStyle(fontWeight: FontWeight.w800)),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                height: 42,
                child: ListView(
                  scrollDirection: Axis.horizontal,
                  children: const [
                    _MenuTab(label: 'Entradas', active: true),
                    _MenuTab(label: 'Pratos Principais'),
                    _MenuTab(label: 'Bebidas'),
                    _MenuTab(label: 'Sobremesas'),
                  ],
                ),
              ),
              const SizedBox(height: 20),
              const Text('Entradas', style: TextStyle(fontFamily: 'Fraunces', fontSize: 20, fontWeight: FontWeight.w800)),
              const SizedBox(height: 12),
              GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: products.length,
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2,
                  crossAxisSpacing: 14,
                  mainAxisSpacing: 14,
                  childAspectRatio: .60,
                ),
                itemBuilder: (_, index) => ProductTile(
                  product: products[index],
                  onAdd: () => Navigator.pushNamed(context, AppRoutes.cart),
                ),
              ),
              const SizedBox(height: 20),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(color: AppColors.forest, borderRadius: BorderRadius.circular(AppRadii.card), boxShadow: AppShadows.warm),
                child: Row(
                  children: [
                    const Expanded(child: Text('3 itens no carrinho', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700))),
                    TextButton(
                      onPressed: () => Navigator.pushNamed(context, AppRoutes.cart),
                      child: const Text('Ver carrinho', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w800)),
                    ),
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

class _MenuTab extends StatelessWidget {
  const _MenuTab({required this.label, this.active = false});

  final String label;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(right: 10),
      padding: const EdgeInsets.symmetric(horizontal: 16),
      decoration: BoxDecoration(
        color: active ? AppColors.primary : Colors.white,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: active ? AppColors.primary : AppColors.border),
      ),
      alignment: Alignment.center,
      child: Text(label, style: TextStyle(color: active ? Colors.white : AppColors.mutedText, fontWeight: FontWeight.w800)),
    );
  }
}
