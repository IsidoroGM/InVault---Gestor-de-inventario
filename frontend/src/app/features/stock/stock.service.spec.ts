import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ApiClient } from '../../core/services/api-client.service';
import { StockService } from './stock.service';

describe('StockService', () => {
  const api = { get: vi.fn() };

  it('reads the server-derived product stock summary', () => {
    api.get.mockReturnValue(of([]));
    TestBed.configureTestingModule({ providers: [{ provide: ApiClient, useValue: api }] });
    TestBed.inject(StockService).findAll().subscribe();
    expect(api.get).toHaveBeenCalledWith('/api/stock/summary/products');
  });
});
