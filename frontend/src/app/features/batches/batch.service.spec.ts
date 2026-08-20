import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { ApiClient } from '../../core/services/api-client.service';
import { BatchRequest } from './batch.models';
import { BatchService } from './batch.service';

describe('BatchService', () => {
  const api = { get: vi.fn(() => of([])), post: vi.fn(() => of({})), put: vi.fn(() => of({})) };
  let service: BatchService;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [BatchService, { provide: ApiClient, useValue: api }],
    });
    service = TestBed.inject(BatchService);
  });

  it('lists all batches or filters them by product on the server', () => {
    service.search({ productId: null, page: 0, size: 25 }).subscribe();
    service.search({ productId: 8, page: 2, size: 50 }).subscribe();

    expect(api.get).toHaveBeenNthCalledWith(1, '/api/batches?page=0&size=25');
    expect(api.get).toHaveBeenNthCalledWith(2, '/api/batches/product/8?page=2&size=50');
  });

  it('creates and updates metadata without a quantity field', () => {
    const request: BatchRequest = {
      productId: 2,
      batchCode: 'LOT-01',
      status: 'AVAILABLE',
      notes: null,
    };

    service.create(request).subscribe();
    service.update(4, request).subscribe();

    expect(api.post).toHaveBeenCalledWith('/api/batches', request);
    expect(api.put).toHaveBeenCalledWith('/api/batches/4', request);
    expect('quantity' in request).toBe(false);
  });
});
