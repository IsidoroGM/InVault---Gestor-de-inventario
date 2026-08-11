import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ApiClient } from '../../core/services/api-client.service';
import { MovementService } from './movement.service';

describe('MovementService', () => {
  const api = { get: vi.fn(), post: vi.fn() };

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
