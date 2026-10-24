import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ApiClient } from '../../core/services/api-client.service';
import { MovementService } from './movement.service';
import { MovementBatchOption } from './movement.models';

describe('MovementService', () => {
  const api = { get: vi.fn(), post: vi.fn() };

  beforeEach(() => vi.clearAllMocks());

  it('includes batches beyond the first hundred', async () => {
    const first = Array.from(
      { length: 100 },
      (_, index) => ({ id: index + 1 }) as MovementBatchOption,
    );
    const last = { id: 101 } as MovementBatchOption;
    api.get
      .mockReturnValueOnce(of({ content: first, page: 0, last: false }))
      .mockReturnValueOnce(of({ content: [last], page: 1, last: true }));

    const batches = await firstValueFrom(service().loadBatches(7));

    expect(batches).toHaveLength(101);
    expect(batches[100].id).toBe(101);
    expect(api.get).toHaveBeenNthCalledWith(2, '/api/batches/product/7?page=1&size=100');
    expect(api.get).toHaveBeenCalledTimes(2);
  });

  it('finishes when the product has no batches', async () => {
    api.get.mockReturnValue(of({ content: [], page: 0, last: true }));
    expect(await firstValueFrom(service().loadBatches(7))).toEqual([]);
    expect(api.get).toHaveBeenCalledOnce();
  });

  function service(): MovementService {
    TestBed.configureTestingModule({ providers: [{ provide: ApiClient, useValue: api }] });
    return TestBed.inject(MovementService);
  }

  it('builds the movement search query', () => {
    api.get.mockReturnValue(of({ content: [] }));
    service().search({ productId: 7, movementType: 'OUTBOUND', page: 2, size: 25 }).subscribe();
    expect(api.get).toHaveBeenCalledWith(
      '/api/stock/movements/search?page=2&size=25&productId=7&movementType=OUTBOUND',
    );
  });

  it('posts only the auditable movement request', () => {
    api.post.mockReturnValue(of({ id: 1 }));
    const request = {
      productId: 2,
      batchId: 3,
      supplierId: null,
      movementType: 'INBOUND' as const,
      quantity: 4,
      reason: 'Recepción',
    };
    service().create(request).subscribe();
    expect(api.post).toHaveBeenCalledWith('/api/stock/movements', request);
  });
});
