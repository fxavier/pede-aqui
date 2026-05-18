import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class AddressItem {
  AddressItem({required this.id, required this.label, required this.fullAddress, this.selected = false});

  final String id;
  final String label;
  final String fullAddress;
  final bool selected;

  AddressItem copyWith({bool? selected}) => AddressItem(id: id, label: label, fullAddress: fullAddress, selected: selected ?? this.selected);
}

class AddressState {
  const AddressState({this.items = const [], this.loading = false, this.error});

  final List<AddressItem> items;
  final bool loading;
  final String? error;
}

class AddressCubit extends Cubit<AddressState> {
  AddressCubit() : super(const AddressState());

  Future<void> loadDefaults() async {
    emit(const AddressState(loading: true));
    emit(
      AddressState(
        items: [
          AddressItem(
            id: 'a-1',
            label: 'Casa',
            fullAddress: 'Av. Julius Nyerere, Maputo',
            selected: true,
          ),
          AddressItem(id: 'a-2', label: 'Trabalho', fullAddress: 'Baixa, Maputo'),
        ],
      ),
    );
  }

  void select(String id) {
    emit(
      AddressState(
        items: state.items.map((item) => item.copyWith(selected: item.id == id)).toList(),
      ),
    );
  }

  void add(String label, String address) {
    if (label.trim().isEmpty || address.trim().isEmpty) {
      emit(AddressState(items: state.items, error: 'Preencha etiqueta e morada.'));
      return;
    }
    final id = 'a-${state.items.length + 1}';
    emit(
      AddressState(
        items: [...state.items, AddressItem(id: id, label: label, fullAddress: address)],
      ),
    );
  }

  void edit(String id, String label, String address) {
    if (label.trim().isEmpty || address.trim().isEmpty) {
      emit(AddressState(items: state.items, error: 'Nao e possivel guardar campos vazios.'));
      return;
    }
    emit(
      AddressState(
        items: state.items
            .map(
              (item) => item.id == id
                  ? AddressItem(
                      id: item.id,
                      label: label,
                      fullAddress: address,
                      selected: item.selected,
                    )
                  : item,
            )
            .toList(),
      ),
    );
  }
}

class AddressScreen extends StatelessWidget {
  const AddressScreen({super.key, required this.cubit});

  final AddressCubit cubit;

  @override
  Widget build(BuildContext context) {
    final labelCtrl = TextEditingController();
    final addressCtrl = TextEditingController();
    return BlocBuilder<AddressCubit, AddressState>(
      bloc: cubit,
      builder: (context, state) {
        return Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Moradas de entrega', style: TextStyle(fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              if (state.loading) const LinearProgressIndicator(),
              if (state.error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Text(state.error!, style: const TextStyle(color: Colors.red)),
                ),
              if (state.items.isEmpty) const Text('Sem moradas cadastradas.'),
              ...state.items.map(
                (item) => RadioListTile<String>(
                  value: item.id,
                  groupValue: state.items.firstWhere((entry) => entry.selected, orElse: () => state.items.first).id,
                  onChanged: (_) => cubit.select(item.id),
                  title: Text(item.label),
                  subtitle: Text(item.fullAddress),
                  secondary: IconButton(
                    icon: const Icon(Icons.edit_outlined),
                    onPressed: () {
                      labelCtrl.text = item.label;
                      addressCtrl.text = item.fullAddress;
                      cubit.edit(item.id, labelCtrl.text.trim(), addressCtrl.text.trim());
                    },
                  ),
                ),
              ),
              const Divider(),
              TextField(controller: labelCtrl, decoration: const InputDecoration(labelText: 'Etiqueta')),
              const SizedBox(height: 8),
              TextField(controller: addressCtrl, decoration: const InputDecoration(labelText: 'Morada completa')),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: () {
                  if (labelCtrl.text.trim().isEmpty || addressCtrl.text.trim().isEmpty) return;
                  cubit.add(labelCtrl.text.trim(), addressCtrl.text.trim());
                },
                child: const Text('Adicionar morada'),
              ),
            ],
          ),
        );
      },
    );
  }
}
