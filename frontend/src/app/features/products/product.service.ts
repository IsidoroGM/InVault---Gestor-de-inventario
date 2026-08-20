import { inject, Injectable } from '@angular/core';
import { forkJoin, Observable } from 'rxjs';

import { ApiClient } from '../../core/services/api-client.service';
import { PageResponse } from '../../shared/models/page-response.model';
import {
  NamedCatalogOption,
  Product,
  ProductCatalogs,
  ProductRequest,
  ProductSearchCriteria,
  UnitCatalogOption,
} from './product.models';

@Injectable({ providedIn: 'root' })
export class ProductService {
  private readonly api = inject(ApiClient);

  search(criteria: ProductSearchCriteria): Observable<PageResponse<Product>> {
    const parameters = new URLSearchParams({
      page: criteria.page.toString(),
      size: criteria.size.toString(),
    });
    const query = criteria.query.trim();
    if (query) {
      parameters.set('query', query);
    }
    if (criteria.active !== 'all') {
      parameters.set('active', String(criteria.active === 'active'));
    }

    return this.api.get<PageResponse<Product>>(`/api/products/search?${parameters.toString()}`);
  }

  create(request: ProductRequest): Observable<Product> {
    return this.api.post<ProductRequest, Product>('/api/products', request);
  }

  update(id: number, request: ProductRequest): Observable<Product> {
    return this.api.put<ProductRequest, Product>(`/api/products/${id}`, request);
  }

  deactivate(id: number): Observable<void> {
    return this.api.patch<Record<string, never>, void>(`/api/products/${id}/deactivate`, {});
  }

  loadCatalogs(): Observable<ProductCatalogs> {
    return forkJoin({
      categories: this.api.get<readonly NamedCatalogOption[]>('/api/categories/active'),
      locations: this.api.get<readonly NamedCatalogOption[]>('/api/locations/active'),
      units: this.api.get<readonly UnitCatalogOption[]>('/api/units/active'),
    });
  }
}
