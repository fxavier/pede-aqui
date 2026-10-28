import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../core/constants/app_colors.dart';
import '../cubits/delivery/delivery_cubit.dart';
import '../cubits/delivery/delivery_state.dart';
import '../widgets/app_card.dart';
import '../widgets/app_top_bar.dart';

class DeliveryConfirmationScreen extends StatelessWidget {
  const DeliveryConfirmationScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocConsumer<DeliveryCubit, DeliveryState>(
      listener: (context, state) {
        if (state.isConfirmed) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Entrega confirmada com sucesso.')),
          );
          Navigator.of(context).popUntil((route) => route.isFirst);
        }
      },
      builder: (context, state) {
        final delivery = state.delivery;
        return Scaffold(
          appBar: const AppTopBar(title: 'Confirmar entrega', showBack: true),
          body: delivery == null
              ? const Center(child: CircularProgressIndicator())
              : ListView(
                  padding: const EdgeInsets.fromLTRB(18, 22, 18, 110),
                  children: [
                    AppCard(
                      child: Row(
                        children: [
                          Container(
                            width: 58,
                            height: 58,
                            decoration: BoxDecoration(color: AppColors.surfaceContainerHighest, borderRadius: BorderRadius.circular(14)),
                            child: const Icon(Icons.inventory_2_outlined, color: AppColors.primary, size: 30),
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('Pedido ${delivery.reference.replaceFirst('#PA-', '#')}', style: const TextStyle(color: AppColors.onSurfaceVariant, fontWeight: FontWeight.w900)),
                                Text('Entrega para ${delivery.customerName}', style: const TextStyle(fontSize: 25, fontWeight: FontWeight.w900)),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 24),
                    AppCard(
                      padding: const EdgeInsets.all(22),
                      child: Column(
                        children: [
                          const Text('Código de Verificação', textAlign: TextAlign.center, style: TextStyle(fontSize: 30, fontWeight: FontWeight.w900)),
                          const SizedBox(height: 12),
                          const Text(
                            'Peça o código de 6 dígitos ao cliente para finalizar a entrega.',
                            textAlign: TextAlign.center,
                            style: TextStyle(color: AppColors.onSurfaceVariant, fontSize: 18, height: 1.35),
                          ),
                          const SizedBox(height: 28),
                          _OtpInput(value: state.otpCode, onChanged: context.read<DeliveryCubit>().updateOtp),
                          const SizedBox(height: 24),
                          OutlinedButton.icon(
                            onPressed: context.read<DeliveryCubit>().toggleProofPhoto,
                            icon: Icon(state.hasProofPhoto ? Icons.check_circle : Icons.photo_camera_outlined),
                            label: Text(state.hasProofPhoto ? 'Foto de prova anexada' : 'Tirar foto de prova'),
                          ),
                          const SizedBox(height: 14),
                          FilledButton(
                            onPressed: state.canConfirm ? context.read<DeliveryCubit>().confirmDelivery : null,
                            style: FilledButton.styleFrom(
                              backgroundColor: state.canConfirm ? AppColors.primary : Colors.grey.shade500,
                            ),
                            child: const Text('Confirmar entrega'),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 24),
                    Container(
                      height: 178,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(24),
                        gradient: const LinearGradient(
                          colors: [Color(0xFF261A16), Color(0xFFC76A31)],
                          begin: Alignment.bottomLeft,
                          end: Alignment.topRight,
                        ),
                      ),
                      child: Stack(
                        children: const [
                          Positioned(right: 22, top: 26, child: Icon(Icons.delivery_dining, color: Colors.white30, size: 86)),
                          Positioned(
                            left: 18,
                            bottom: 18,
                            right: 18,
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('Ponto de entrega', style: TextStyle(color: Colors.white, fontSize: 26, fontWeight: FontWeight.w900)),
                                SizedBox(height: 4),
                                Text('Av. Eduardo Mondlane, 456 • Maputo', style: TextStyle(color: Colors.white, fontSize: 16)),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
        );
      },
    );
  }
}

class _OtpInput extends StatefulWidget {
  const _OtpInput({required this.value, required this.onChanged});

  final String value;
  final ValueChanged<String> onChanged;

  @override
  State<_OtpInput> createState() => _OtpInputState();
}

class _OtpInputState extends State<_OtpInput> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.value);
  }

  @override
  void didUpdateWidget(covariant _OtpInput oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value && _controller.text != widget.value) {
      _controller.text = widget.value;
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final digits = widget.value.padRight(6, '-').split('').take(6).toList();
    return Column(
      children: [
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: const Color(0xFFFFF9E6),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: AppColors.warning.withOpacity(.35), width: 2),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: digits
                .map(
                  (digit) => Container(
                    width: 46,
                    height: 56,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: AppColors.outlineVariant, width: 2),
                    ),
                    child: Text(digit, style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w900, color: Color(0xFF6B7280))),
                  ),
                )
                .toList(),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _controller,
          keyboardType: TextInputType.number,
          maxLength: 6,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          onChanged: widget.onChanged,
          decoration: const InputDecoration(counterText: '', hintText: 'Introduza o código aqui'),
        ),
      ],
    );
  }
}
