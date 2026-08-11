import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { ApiClient } from '../../core/services/api-client.service';
import { NamedCatalogRequest } from './catalog.models';
import { CatalogService } from './catalog.service';

describe('CatalogService', () => {
  const api = {
    get: vi.fn(() => of([])),
    post: vi.fn(() => of({})),
    put: vi.fn(() => of({})),
    patch: vi.fn(() => of(undefined)),
  };
  let service: CatalogService;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [CatalogService, { provide: ApiClient, useValue: api }],
    });
    service = TestBed.inject(CatalogService);
  });

  it('loads every catalogue through its authenticated API endpoint', () => {
    service.loadAll().subscribe();

    expect(api.get).toHaveBeenCalledWith('/api/categories');
    expect(api.get).toHaveBeenCalledWith('/api/locations');
    expect(api.get).toHaveBeenCalledWith('/api/units');
    expect(api.get).toHaveBeenCalledWith('/api/suppliers');
  });

  it('creates and updates the selected catalogue kind', () => {
    const request: NamedCatalogRequest = {
      name: 'Consumibles',
      description: null,
      active: true,
    };

    service.create('categories', request).subscribe();
    service.update('categories', 4, request).subscribe();

    expect(api.post).toHaveBeenCalledWith('/api/categories', request);
    expect(api.put).toHaveBeenCalledWith('/api/categories/4', request);
  });

  it('uses logical deactivation instead of deleting catalogue data', () => {
    service.deactivate('suppliers', 9).subscribe();

    expect(api.patch).toHaveBeenCalledWith('/api/suppliers/9/deactivate', {});
  });
});
