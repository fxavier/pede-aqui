import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:pede_aqui_courier_app/core/api/api_client.dart';
import 'package:pede_aqui_courier_app/core/formatters/currency_formatter.dart';

const _apiBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://localhost:8080/api/v1');
const _appToken = String.fromEnvironment('APP_TOKEN', defaultValue: 'dev-token');

void main() {
  runApp(const CourierApp());
}

class CourierApp extends StatelessWidget {
  const CourierApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Pede Aqui Estafeta',
      locale: const Locale('pt', 'MZ'),
      theme: ThemeData(
        colorSchemeSeed: const Color(0xFF145A9B),
        scaffoldBackgroundColor: const Color(0xFFF4F8FC),
        useMaterial3: true,
      ),
      home: const CourierShell(),
    );
  }
}

class CourierShell extends StatefulWidget {
  const CourierShell({super.key});

  @override
  State<CourierShell> createState() => _CourierShellState();
}

class _CourierShellState extends State<CourierShell> {
  int _index = 0;
  late final ApiClient _apiClient;
  late final CourierCubit _courierCubit;
  late final DeliveryCubit _deliveryCubit;

  @override
  void initState() {
    super.initState();
    _apiClient = ApiClient(baseUrl: _apiBaseUrl, token: _appToken);
    _courierCubit = CourierCubit(_apiClient)..load();
    _deliveryCubit = DeliveryCubit(_apiClient)..loadJobs();
  }

  @override
  Widget build(BuildContext context) {
    final tabs = [
      _JobsScreen(courierCubit: _courierCubit, deliveryCubit: _deliveryCubit),
      _DeliveryFlowScreen(deliveryCubit: _deliveryCubit),
      _EarningsScreen(deliveryCubit: _deliveryCubit),
    ];

    return Scaffold(
      appBar: AppBar(title: const Text('Pede Aqui Estafeta - Mocambique')),
      body: tabs[_index],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (value) => setState(() => _index = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.local_shipping_outlined), label: 'Atribuicoes'),
          NavigationDestination(icon: Icon(Icons.pin_outlined), label: 'Entrega'),
          NavigationDestination(icon: Icon(Icons.payments_outlined), label: 'Ganhos'),
        ],
      ),
    );
  }
}

class _JobsScreen extends StatelessWidget {
  const _JobsScreen({required this.courierCubit, required this.deliveryCubit});

  final CourierCubit courierCubit;
  final DeliveryCubit deliveryCubit;

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<CourierCubit, CourierState>(
      bloc: courierCubit,
      builder: (context, courierState) {
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (courierState.loading) const LinearProgressIndicator(),
            if (courierState.forbidden) const Text('Sem permissao para perfil estafeta.'),
            SwitchListTile(
              value: courierState.available,
              onChanged: courierState.loading ? null : courierCubit.setAvailability,
              title: const Text('Disponivel para entregas'),
              subtitle: const Text('Zona de operacao: Maputo Cidade'),
            ),
            if (courierState.error != null) Text('Erro: ${courierState.error}', style: const TextStyle(color: Colors.red)),
            const SizedBox(height: 8),
            BlocBuilder<DeliveryCubit, DeliveryState>(
              bloc: deliveryCubit,
              builder: (context, deliveryState) {
                if (deliveryState.loading) return const CircularProgressIndicator();
                if (deliveryState.forbidden) return const Text('Sem permissao para jobs.');
                if (deliveryState.error != null) return Text('Erro: ${deliveryState.error}', style: const TextStyle(color: Colors.red));
                if (deliveryState.jobs.isEmpty) return const Text('Nenhuma atribuicao disponivel.');
                return Column(
                  children: deliveryState.jobs
                      .map(
                        (job) => Card(
                          child: ListTile(
                            title: Text(job['id'].toString()),
                            subtitle: Text('Estado: ${job['status']} | Delivery: ${job['deliveryId']}'),
                            onTap: () => deliveryCubit.selectDelivery(job['deliveryId']?.toString()),
                            trailing: Wrap(
                              spacing: 8,
                              children: [
                                TextButton(
                                  onPressed: () => deliveryCubit.accept(job['id'].toString()),
                                  child: const Text('Aceitar'),
                                ),
                                TextButton(
                                  onPressed: () => deliveryCubit.reject(job['id'].toString(), 'Indisponivel no momento'),
                                  child: const Text('Rejeitar'),
                                ),
                              ],
                            ),
                          ),
                        ),
                      )
                      .toList(),
                );
              },
            ),
          ],
        );
      },
    );
  }
}

class _DeliveryFlowScreen extends StatelessWidget {
  const _DeliveryFlowScreen({required this.deliveryCubit});

  final DeliveryCubit deliveryCubit;

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<DeliveryCubit, DeliveryState>(
      bloc: deliveryCubit,
      builder: (context, state) {
        final controller = TextEditingController(text: state.codeInput);
        return Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Confirmacao de entrega', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              Text(state.selectedDeliveryId == null ? 'Selecione um job para concluir entrega.' : 'Delivery selecionada: ${state.selectedDeliveryId}'),
              const SizedBox(height: 8),
              TextField(
                controller: controller,
                maxLength: 6,
                keyboardType: TextInputType.number,
                onChanged: deliveryCubit.setCode,
              ),
              if (state.lastSuccessMessage != null) Text(state.lastSuccessMessage!, style: const TextStyle(color: Colors.green)),
              if (state.error != null) Text('Erro: ${state.error}', style: const TextStyle(color: Colors.red)),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: state.selectedDeliveryId == null || state.codeInput.length != 6
                    ? null
                    : () => deliveryCubit.complete(
                          deliveryId: state.selectedDeliveryId!,
                          code: state.codeInput,
                        ),
                child: const Text('Concluir entrega'),
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  OutlinedButton(
                    onPressed: state.selectedDeliveryId == null ? null : () => deliveryCubit.updateStatus('ARRIVED_AT_VENDOR'),
                    child: const Text('Cheguei ao vendedor'),
                  ),
                  OutlinedButton(
                    onPressed: state.selectedDeliveryId == null ? null : () => deliveryCubit.updateStatus('PICKED_UP'),
                    child: const Text('Recolhido'),
                  ),
                  OutlinedButton(
                    onPressed: state.selectedDeliveryId == null ? null : () => deliveryCubit.updateStatus('ON_ROUTE_TO_CUSTOMER'),
                    child: const Text('Em rota'),
                  ),
                  OutlinedButton(
                    onPressed: state.selectedDeliveryId == null ? null : () => deliveryCubit.updateStatus('ARRIVED_AT_CUSTOMER'),
                    child: const Text('Cheguei ao cliente'),
                  ),
                  OutlinedButton(
                    onPressed: state.selectedDeliveryId == null ? null : () => deliveryCubit.updateStatus('FAILED_DELIVERY'),
                    child: const Text('Falha de entrega'),
                  ),
                ],
              ),
            ],
          ),
        );
      },
    );
  }
}

class _EarningsScreen extends StatelessWidget {
  const _EarningsScreen({required this.deliveryCubit});

  final DeliveryCubit deliveryCubit;

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<DeliveryCubit, DeliveryState>(
      bloc: deliveryCubit,
      builder: (context, state) {
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (state.earningsLoading) const LinearProgressIndicator(),
            ListTile(title: const Text('Ganhos da semana'), trailing: Text(formatMzn(state.earningsTotal))),
            ListTile(title: const Text('Entregas concluidas'), trailing: Text('${state.completedDeliveries}')),
            ListTile(title: const Text('Entregas falhadas'), trailing: Text('${state.failedDeliveries}')),
          ],
        );
      },
    );
  }
}

class CourierState {
  const CourierState({this.loading = false, this.available = false, this.error, this.forbidden = false});
  final bool loading;
  final bool available;
  final String? error;
  final bool forbidden;
}

class CourierCubit extends Cubit<CourierState> {
  CourierCubit(this.apiClient) : super(const CourierState());
  final ApiClient apiClient;

  Future<void> load() async {
    emit(const CourierState(loading: true));
    final result = await apiClient.getJson('/couriers/me');
    if (result.forbidden) return emit(const CourierState(forbidden: true));
    if (!result.ok) return emit(CourierState(error: result.error));
    emit(CourierState(available: result.data['available'] == true));
  }

  Future<void> setAvailability(bool value) async {
    emit(CourierState(loading: true, available: value));
    final result = await apiClient.patchJson('/couriers/me/availability', {'available': value});
    if (result.forbidden) return emit(const CourierState(forbidden: true));
    if (!result.ok) return emit(CourierState(error: result.error, available: !value));
    emit(CourierState(available: result.data['available'] == true));
  }
}

class DeliveryState {
  const DeliveryState({
    this.loading = false,
    this.jobs = const [],
    this.error,
    this.forbidden = false,
    this.selectedDeliveryId,
    this.codeInput = '',
    this.lastSuccessMessage,
    this.completedDeliveries = 0,
    this.failedDeliveries = 0,
    this.earningsTotal = 0,
    this.earningsLoading = false,
  });
  final bool loading;
  final List<Map<String, dynamic>> jobs;
  final String? error;
  final bool forbidden;
  final String? selectedDeliveryId;
  final String codeInput;
  final String? lastSuccessMessage;
  final int completedDeliveries;
  final int failedDeliveries;
  final num earningsTotal;
  final bool earningsLoading;
}

class DeliveryCubit extends Cubit<DeliveryState> {
  DeliveryCubit(this.apiClient) : super(const DeliveryState());
  final ApiClient apiClient;

  Future<void> loadJobs() async {
    emit(const DeliveryState(loading: true));
    final result = await apiClient.getJson('/dispatch-jobs');
    if (result.forbidden) return emit(const DeliveryState(forbidden: true));
    if (!result.ok) return emit(DeliveryState(error: result.error));
    final jobs = (result.data as List<dynamic>).cast<Map<String, dynamic>>();
    emit(DeliveryState(
      jobs: jobs,
      selectedDeliveryId: jobs.isNotEmpty ? jobs.first['deliveryId']?.toString() : null,
      completedDeliveries: state.completedDeliveries,
      failedDeliveries: state.failedDeliveries,
      earningsTotal: state.earningsTotal,
    ));
    await loadEarningsSummary();
  }

  Future<void> accept(String jobId) async {
    await apiClient.postJson('/dispatch-jobs/$jobId/accept', {});
    await loadJobs();
  }

  Future<void> reject(String jobId, String reason) async {
    await apiClient.postJson('/dispatch-jobs/$jobId/reject', {'reason': reason});
    await loadJobs();
  }

  Future<void> updateStatus(String status) async {
    if (state.selectedDeliveryId == null) return;
    final result = await apiClient.patchJson('/deliveries/${state.selectedDeliveryId}/status', {'status': status});
    if (result.forbidden) {
      emit(DeliveryState(
        jobs: state.jobs,
        selectedDeliveryId: state.selectedDeliveryId,
        codeInput: state.codeInput,
        forbidden: true,
      ));
      return;
    }
    if (!result.ok) {
      emit(DeliveryState(
        jobs: state.jobs,
        selectedDeliveryId: state.selectedDeliveryId,
        codeInput: state.codeInput,
        error: result.error,
      ));
      return;
    }
    emit(DeliveryState(
      jobs: state.jobs,
      selectedDeliveryId: state.selectedDeliveryId,
      codeInput: state.codeInput,
      lastSuccessMessage: 'Estado atualizado para $status',
      completedDeliveries: state.completedDeliveries,
      failedDeliveries: state.failedDeliveries,
      earningsTotal: state.earningsTotal,
    ));
  }

  Future<void> loadEarningsSummary() async {
    emit(DeliveryState(
      jobs: state.jobs,
      selectedDeliveryId: state.selectedDeliveryId,
      codeInput: state.codeInput,
      earningsLoading: true,
      completedDeliveries: state.completedDeliveries,
      failedDeliveries: state.failedDeliveries,
      earningsTotal: state.earningsTotal,
    ));
    final result = await apiClient.getJson('/couriers/me/earnings-summary');
    if (!result.ok) {
      emit(DeliveryState(
        jobs: state.jobs,
        selectedDeliveryId: state.selectedDeliveryId,
        codeInput: state.codeInput,
        error: result.error,
        completedDeliveries: state.completedDeliveries,
        failedDeliveries: state.failedDeliveries,
        earningsTotal: state.earningsTotal,
      ));
      return;
    }
    emit(DeliveryState(
      jobs: state.jobs,
      selectedDeliveryId: state.selectedDeliveryId,
      codeInput: state.codeInput,
      completedDeliveries: (result.data['completedDeliveries'] as num?)?.toInt() ?? 0,
      failedDeliveries: (result.data['failedDeliveries'] as num?)?.toInt() ?? 0,
      earningsTotal: (result.data['earningsTotalMzn'] as num?) ?? 0,
    ));
  }

  Future<void> complete({required String deliveryId, required String code}) async {
    final result = await apiClient.postJson('/deliveries/$deliveryId/complete', {'confirmationCode': code});
    if (result.forbidden) {
      emit(DeliveryState(
        jobs: state.jobs,
        selectedDeliveryId: state.selectedDeliveryId,
        codeInput: state.codeInput,
        forbidden: true,
      ));
      return;
    }
    if (!result.ok) {
      emit(DeliveryState(
        jobs: state.jobs,
        selectedDeliveryId: state.selectedDeliveryId,
        codeInput: state.codeInput,
        error: result.error,
      ));
      return;
    }
    emit(DeliveryState(
      jobs: state.jobs,
      selectedDeliveryId: state.selectedDeliveryId,
      codeInput: '',
      lastSuccessMessage: 'Entrega concluida com sucesso.',
    ));
  }

  void selectDelivery(String? deliveryId) {
    emit(DeliveryState(
      jobs: state.jobs,
      selectedDeliveryId: deliveryId,
      error: state.error,
      forbidden: state.forbidden,
      loading: state.loading,
      codeInput: state.codeInput,
      lastSuccessMessage: state.lastSuccessMessage,
    ));
  }

  void setCode(String value) {
    emit(DeliveryState(
      jobs: state.jobs,
      selectedDeliveryId: state.selectedDeliveryId,
      error: state.error,
      forbidden: state.forbidden,
      loading: state.loading,
      codeInput: value,
      lastSuccessMessage: state.lastSuccessMessage,
      completedDeliveries: state.completedDeliveries,
      failedDeliveries: state.failedDeliveries,
      earningsTotal: state.earningsTotal,
      earningsLoading: state.earningsLoading,
    ));
  }
}
