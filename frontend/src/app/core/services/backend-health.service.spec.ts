import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { ApiClient } from './api-client.service';
import { BackendHealthService } from './backend-health.service';

class ApiClientStub {
  response: Observable<string> = of('InVault backend is running');

  getText(): Observable<string> {
    return this.response;
  }
}

describe('BackendHealthService', () => {
  let service: BackendHealthService;
  let api: ApiClientStub;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        BackendHealthService,
        ApiClientStub,
        { provide: ApiClient, useExisting: ApiClientStub },
      ],
    });

    service = TestBed.inject(BackendHealthService);
    api = TestBed.inject(ApiClientStub);
  });

  it('reports an available backend', () => {
    service.check();

    expect(service.state()).toBe('available');
    expect(service.message()).toBe('InVault backend is running');
  });

  it('reports a normalized connection error', () => {
    api.response = throwError(() => ({ message: 'No se pudo conectar con el backend.' }));

    service.check();

    expect(service.state()).toBe('unavailable');
    expect(service.message()).toBe('No se pudo conectar con el backend.');
  });
});
