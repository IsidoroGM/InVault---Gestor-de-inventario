import { inject, Injectable } from '@angular/core';
import { EMPTY, expand, forkJoin, Observable, reduce } from 'rxjs';

import { ApiClient } from '../../core/services/api-client.service';
import { PageResponse } from '../../shared/models/page-response.model';
import {
  MovementBatchOption,
  MovementFormOptions,
  MovementSearchCriteria,
  MovementSupplierOption,
  StockMovement,
  StockMovementRequest,
} from './movement.models';
import { BatchProductOption } from '../batches/batch.models';

@Injectable({ providedIn: 'root' })
export class MovementService {
  private readonly api = inject(ApiClient);

  search(criteria: MovementSearchCriteria): Observable<PageResponse<StockMovement>> {
    const query = new URLSearchParams({
      page: criteria.page.toString(),
      size: criteria.size.toString(),
    });
    if (criteria.productId !== null) {
      query.set('productId', criteria.productId.toString());
    }
    if (criteria.movementType !== null) {
      query.set('movementType', criteria.movementType);
    }
    return this.api.get<PageResponse<StockMovement>>(
      `/api/stock/movements/search?${query.toString()}`,
    );
  }

  loadFormOptions(): Observable<MovementFormOptions> {
    return forkJoin({
      products: this.api.get<readonly BatchProductOption[]>('/api/products/active'),
      suppliers: this.api.get<readonly MovementSupplierOption[]>('/api/suppliers/active'),
    });
  }

  loadBatches(productId: number): Observable<readonly MovementBatchOption[]> {
    const loadPage = (page: number) =>
      this.api.get<PageResponse<MovementBatchOption>>(
        `/api/batches/product/${productId}?page=${page}&size=100`,
      );

    return loadPage(0).pipe(
      expand((page) => (page.last ? EMPTY : loadPage(page.page + 1)), 1),
      reduce(
        (batches, page) => [...batches, ...page.content],
        [] as readonly MovementBatchOption[],
      ),
    );
  }

  create(request: StockMovementRequest): Observable<StockMovement> {
    return this.api.post<StockMovementRequest, StockMovement>('/api/stock/movements', request);
  }
}
