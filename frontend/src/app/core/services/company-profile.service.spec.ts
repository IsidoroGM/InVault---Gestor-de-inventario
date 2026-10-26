import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { ApiClient } from './api-client.service';
import { CompanyProfileService } from './company-profile.service';

describe('CompanyProfileService', () => {
  it('loads deployment metadata without a database selector', () => {
    let requested = '';
    TestBed.configureTestingModule({
      providers: [
        {
          provide: ApiClient,
          useValue: {
            get: (path: string) => {
              requested = path;
              return of({ name: 'Almacén José & Hijos' });
            },
          },
        },
      ],
    });
    const service = TestBed.inject(CompanyProfileService);
    service.load();
    expect(requested).toBe('/api/company');
    expect(service.name()).toBe('Almacén José & Hijos');
  });

  it('keeps a usable name when metadata cannot be loaded', () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: ApiClient, useValue: { get: () => throwError(() => ({ status: 503 })) } },
      ],
    });
    const service = TestBed.inject(CompanyProfileService);
    service.load();
    expect(service.name()).toBe('InVault');
  });
});
