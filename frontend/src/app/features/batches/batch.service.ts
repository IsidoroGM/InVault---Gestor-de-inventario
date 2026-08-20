import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/services/api-client.service';
import { PageResponse } from '../../shared/models/page-response.model';
import { Batch, BatchProductOption, BatchRequest, BatchSearchCriteria } from './batch.models';

@Injectable({ providedIn: 'root' })
export class BatchService {
  private readonly api = inject(ApiClient);

  search(criteria: BatchSearchCriteria): Observable<PageResponse<Batch>> {
    const query = new URLSearchParams({
      page: criteria.page.toString(),
      size: criteria.size.toString(),
    });
    const path = criteria.productId ? `/api/batches/product/${criteria.productId}` : '/api/batches';
    return this.api.get<PageResponse<Batch>>(`${path}?${query.toString()}`);
  }

  loadActiveProducts(): Observable<readonly BatchProductOption[]> {
    return this.api.get<readonly BatchProductOption[]>('/api/products/active');
  }

  create(request: BatchRequest): Observable<Batch> {
    return this.api.post<BatchRequest, Batch>('/api/batches', request);
  }

  update(id: number, request: BatchRequest): Observable<Batch> {
    return this.api.put<BatchRequest, Batch>(`/api/batches/${id}`, request);
  }
}
