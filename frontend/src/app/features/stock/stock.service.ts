import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/services/api-client.service';
import { ProductStock } from './stock.models';

@Injectable({ providedIn: 'root' })
export class StockService {
  private readonly api = inject(ApiClient);

  findAll(): Observable<readonly ProductStock[]> {
    return this.api.get<readonly ProductStock[]>('/api/stock/summary/products');
  }

  findLowStock(): Observable<readonly ProductStock[]> {
    return this.api.get<readonly ProductStock[]>('/api/stock/summary/low-stock');
  }

  findByProduct(productId: number): Observable<ProductStock> {
    return this.api.get<ProductStock>(`/api/stock/summary/products/${productId}`);
  }
}
