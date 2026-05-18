import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:pede_aqui_delivery_app/core/api/api_client.dart';

class CheckoutState {
  const CheckoutState({this.loading = false, this.successRef, this.successOrderId, this.error, this.forbidden = false});

  final bool loading;
  final String? successRef;
  final String? successOrderId;
  final String? error;
  final bool forbidden;
}

class CheckoutCubit extends Cubit<CheckoutState> {
  CheckoutCubit(this.apiClient) : super(const CheckoutState());

  final ApiClient apiClient;

  Future<void> checkout({
    required String cartId,
    required String fulfillmentType,
    String? deliveryInstructions,
  }) async {
    emit(const CheckoutState(loading: true));
    final result = await apiClient.postJson('/checkout', {
      'cartId': cartId,
      'fulfillmentType': fulfillmentType,
      'deliveryInstructions': deliveryInstructions ?? '',
      'idempotencyKey': DateTime.now().millisecondsSinceEpoch.toString(),
    });
    if (result.forbidden) {
      emit(const CheckoutState(forbidden: true));
      return;
    }
    if (!result.ok) {
      emit(CheckoutState(error: result.error));
      return;
    }
    emit(CheckoutState(
      successRef: (result.data['reference'] ?? 'N/A').toString(),
      successOrderId: (result.data['id'] ?? '').toString(),
    ));
  }
}

class CheckoutScreen extends StatelessWidget {
  const CheckoutScreen({
    super.key,
    required this.cubit,
    required this.onOrderCreated,
    required this.selectedCartId,
  });

  final CheckoutCubit cubit;
  final ValueChanged<String> onOrderCreated;
  final String? selectedCartId;

  @override
  Widget build(BuildContext context) {
    final instructionsController = TextEditingController();
    final fulfillmentType = ValueNotifier<String>('DELIVERY');
    return BlocListener<CheckoutCubit, CheckoutState>(
      bloc: cubit,
      listener: (context, state) {
        if (state.successOrderId != null && state.successOrderId!.isNotEmpty) {
          onOrderCreated(state.successOrderId!);
        }
      },
      child: BlocBuilder<CheckoutCubit, CheckoutState>(
        bloc: cubit,
        builder: (context, state) {
          return Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const ListTile(title: Text('Morada'), subtitle: Text('Av. Julius Nyerere, Maputo')),
                ValueListenableBuilder<String>(
                  valueListenable: fulfillmentType,
                  builder: (context, value, _) => SegmentedButton<String>(
                    segments: const [
                      ButtonSegment(value: 'DELIVERY', label: Text('Entrega')),
                      ButtonSegment(value: 'PICKUP', label: Text('Levantamento')),
                    ],
                    selected: {value},
                    onSelectionChanged: (selection) => fulfillmentType.value = selection.first,
                  ),
                ),
                const ListTile(title: Text('Pagamento'), subtitle: Text('Mock payment local')),
                TextField(
                  controller: instructionsController,
                  maxLines: 3,
                  decoration: const InputDecoration(labelText: 'Instrucoes de entrega'),
                ),
                const SizedBox(height: 12),
                Text('Cart ID ativo: ${selectedCartId ?? '-'}'),
                const SizedBox(height: 16),
                if (state.loading) const CircularProgressIndicator(),
                if (state.forbidden) const Text('Sem permissao para checkout.'),
                if (state.error != null) Text('Erro: ${state.error}', style: const TextStyle(color: Colors.red)),
                if (state.successRef != null) Text('Encomenda criada: ${state.successRef}'),
                const Spacer(),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: () {
                        final cartId = selectedCartId;
                        if (cartId != null && cartId.isNotEmpty) {
                          cubit.checkout(
                            cartId: cartId,
                            fulfillmentType: fulfillmentType.value,
                            deliveryInstructions: instructionsController.text.trim(),
                          );
                        }
                      },
                    child: const Text('Confirmar encomenda'),
                  ),
                )
              ],
            ),
          );
        },
      ),
    );
  }
}
