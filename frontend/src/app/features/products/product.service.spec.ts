import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { ApiClient } from '../../core/services/api-client.service';
import { ProductRequest } from './product.models';
import { ProductService } from './product.service';

describe('ProductService', () => {
  const api = {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
  };
  let service: ProductService;

  beforeEach(() => {
    vi.clearAllMocks();
    api.get.mockReturnValue(of([]));
    api.post.mockReturnValue(of({}));
    api.put.mockReturnValue(of({}));
    api.patch.mockReturnValue(of(undefined));

    TestBed.configureTestingModule({
      providers: [ProductService, { provide: ApiClient, useValue: api }],
    });
    service = TestBed.inject(ProductService);
  });

  it('builds a trimmed paginated search with an active filter', () => {
    service.search({ query: '  cable usb  ', active: 'active', page: 2, size: 50 }).subscribe();

    expect(api.get).toHaveBeenCalledWith(
      '/api/products/search?page=2&size=50&query=cable+usb&active=true',
    );
  });

  it('omits optional filters when listing every product', () => {
    service.search({ query: '  ', active: 'all', page: 0, size: 25 }).subscribe();

    expect(api.get).toHaveBeenCalledWith('/api/products/search?page=0&size=25');
  });

  it('uses the product write endpoints', () => {
    const request: ProductRequest = {
      sku: 'SKU-01',
      name: 'Producto',
      description: null,
      categoryId: 1,
      locationId: 2,
      unitId: 3,
      minimumStock: 4,
      active: true,
    };

    service.create(request).subscribe();
    service.update(7, request).subscribe();
    service.deactivate(7).subscribe();

    expect(api.post).toHaveBeenCalledWith('/api/products', request);
    expect(api.put).toHaveBeenCalledWith('/api/products/7', request);
    expect(api.patch).toHaveBeenCalledWith('/api/products/7/deactivate', {});
  });

  it('loads the three active catalogues needed by the form', () => {
    service.loadCatalogs().subscribe();

    expect(api.get).toHaveBeenCalledWith('/api/categories/active');
    expect(api.get).toHaveBeenCalledWith('/api/locations/active');
    expect(api.get).toHaveBeenCalledWith('/api/units/active');
  });
});
