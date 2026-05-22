// API-ready extension point.
//
// Keep mock repositories during UI development. When backend endpoints are ready,
// create API repositories next to each feature repository and bind them in
// service_locator.dart. Example:
//
// class ApiCatalogRepository implements CatalogRepository {
//   ApiCatalogRepository(this._client);
//   final ApiClient _client;
//
//   @override
//   Future<List<Category>> getCategories() async {
//     final response = await _client.get<List<dynamic>>('/categories');
//     return response.data!.map((json) => CategoryDto.fromJson(json).toDomain()).toList();
//   }
// }
