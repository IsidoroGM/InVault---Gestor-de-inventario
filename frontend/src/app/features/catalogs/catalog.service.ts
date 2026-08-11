import { inject, Injectable } from '@angular/core';
import { forkJoin, Observable } from 'rxjs';

import { ApiClient } from '../../core/services/api-client.service';
import {
  CatalogCollections,
  CatalogItemMap,
  CatalogKind,
  CatalogRequestMap,
  NamedCatalogItem,
  SupplierCatalogItem,
  UnitCatalogItem,
} from './catalog.models';

const CATALOG_ENDPOINTS: Readonly<Record<CatalogKind, string>> = {
  categories: '/api/categories',
  locations: '/api/locations',
  units: '/api/units',
  suppliers: '/api/suppliers',
};

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly api = inject(ApiClient);

  loadAll(): Observable<CatalogCollections> {
    return forkJoin({
      categories: this.api.get<readonly NamedCatalogItem[]>(CATALOG_ENDPOINTS.categories),
      locations: this.api.get<readonly NamedCatalogItem[]>(CATALOG_ENDPOINTS.locations),
      units: this.api.get<readonly UnitCatalogItem[]>(CATALOG_ENDPOINTS.units),
      suppliers: this.api.get<readonly SupplierCatalogItem[]>(CATALOG_ENDPOINTS.suppliers),
    });
  }

  create<K extends CatalogKind>(
    kind: K,
    request: CatalogRequestMap[K],
  ): Observable<CatalogItemMap[K]> {
    return this.api.post<CatalogRequestMap[K], CatalogItemMap[K]>(CATALOG_ENDPOINTS[kind], request);
  }

  update<K extends CatalogKind>(
    kind: K,
    id: number,
    request: CatalogRequestMap[K],
  ): Observable<CatalogItemMap[K]> {
    return this.api.put<CatalogRequestMap[K], CatalogItemMap[K]>(
      `${CATALOG_ENDPOINTS[kind]}/${id}`,
      request,
    );
  }

  deactivate(kind: CatalogKind, id: number): Observable<void> {
    return this.api.patch<Record<string, never>, void>(
      `${CATALOG_ENDPOINTS[kind]}/${id}/deactivate`,
      {},
    );
  }
}
