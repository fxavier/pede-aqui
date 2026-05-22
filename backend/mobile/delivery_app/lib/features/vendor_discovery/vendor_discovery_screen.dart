import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:pede_aqui_delivery_app/core/api/api_client.dart';

class VendorDiscoveryState {
  const VendorDiscoveryState({
    this.loading = false,
    this.items = const [],
    this.error,
    this.forbidden = false,
    this.category,
    this.onlyAvailable = true,
    this.minRating,
  });

  final bool loading;
  final List<Map<String, dynamic>> items;
  final String? error;
  final bool forbidden;
  final String? category;
  final bool onlyAvailable;
  final double? minRating;
}

class VendorDiscoveryCubit extends Cubit<VendorDiscoveryState> {
  VendorDiscoveryCubit(this.apiClient) : super(const VendorDiscoveryState());

  final ApiClient apiClient;

  Future<void> load({String? category, bool onlyAvailable = true, double? minRating}) async {
    emit(
      VendorDiscoveryState(
        loading: true,
        category: category,
        onlyAvailable: onlyAvailable,
        minRating: minRating,
      ),
    );
    final query = StringBuffer('/search/vendors?');
    if (category != null && category.isNotEmpty) {
      query.write('category=$category&');
    }
    if (onlyAvailable) {
      query.write('available=true&');
    }
    if (minRating != null) {
      query.write('ratingGte=${minRating.toStringAsFixed(1)}&');
    }
    final result = await apiClient.getJson(query.toString());
    if (result.forbidden) {
      emit(const VendorDiscoveryState(forbidden: true));
      return;
    }
    if (!result.ok) {
      emit(VendorDiscoveryState(error: result.error));
      return;
    }
    final list = (result.data as List<dynamic>).cast<Map<String, dynamic>>();
    emit(
      VendorDiscoveryState(
        items: list,
        category: category,
        onlyAvailable: onlyAvailable,
        minRating: minRating,
      ),
    );
  }
}

class VendorDiscoveryScreen extends StatelessWidget {
  const VendorDiscoveryScreen({
    super.key,
    required this.cubit,
    required this.onVendorSelected,
    required this.selectedVendorId,
  });

  final VendorDiscoveryCubit cubit;
  final ValueChanged<String> onVendorSelected;
  final String? selectedVendorId;

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<VendorDiscoveryCubit, VendorDiscoveryState>(
      bloc: cubit,
      builder: (context, state) {
        if (state.loading) return const Center(child: CircularProgressIndicator());
        if (state.forbidden) return const Center(child: Text('Sem permissao para listar fornecedores.'));
        if (state.error != null) return Center(child: Text('Erro: ${state.error}'));
        if (state.items.isEmpty) return const Center(child: Text('Nenhum fornecedor disponivel nesta zona.'));
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemBuilder: (_, index) {
            final vendor = state.items[index];
            final rating = (vendor['rating'] as num?)?.toDouble();
            return ListTile(
              tileColor: const Color(0xFFE7F3F5),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              title: Text((vendor['name'] ?? 'Fornecedor').toString()),
              subtitle: Text(
                'Categoria: ${(vendor['category'] ?? '-').toString()} | Disponivel: ${(vendor['available'] ?? false) ? 'Sim' : 'Nao'} | Rating: ${rating?.toStringAsFixed(1) ?? '-'}',
              ),
              trailing: (vendor['id']?.toString() == selectedVendorId)
                  ? const Icon(Icons.check_circle, color: Colors.green)
                  : null,
              onTap: () {
                final vendorId = vendor['id']?.toString();
                if (vendorId != null && vendorId.isNotEmpty) {
                  onVendorSelected(vendorId);
                }
              },
            );
          },
          separatorBuilder: (_, __) => const SizedBox(height: 10),
          itemCount: state.items.length,
        );
      },
    );
  }
}
