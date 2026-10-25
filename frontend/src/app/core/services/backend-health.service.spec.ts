import { TestBed } from '@angular/core/testing';
import { Observable, of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ApiClient } from './api-client.service';
import { BackendHealthService } from './backend-health.service';

class ApiClientStub {
  response: Observable<unknown> = of({ status: 'UP' });
  path = '';

  get<T>(path: string): Observable<T> {
    this.path = path;
    return this.response as Observable<T>;
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

  afterEach(() => vi.useRealTimers());

  it('reports an available backend', () => {
    service.check();

    expect(service.state()).toBe('available');
    expect(service.message()).toBe('Servidor y base de datos disponibles.');
    expect(api.path).toBe('/api/health/readiness');
  });

  it('reports a normalized connection error', () => {
    api.response = throwError(() => ({
      status: 0,
      message: 'No se pudo conectar con el backend.',
    }));

    service.check();

    expect(service.state()).toBe('unavailable');
    expect(service.message()).toBe('No se pudo conectar con el backend.');
  });

  it('reports database unavailability with a clear Spanish message', () => {
    api.response = throwError(() => ({ status: 503 }));
    service.check();
    expect(service.state()).toBe('unavailable');
    expect(service.message()).toBe('El servidor o su base de datos no están disponibles.');
  });

  it('does not accept a proxy HTML page or DOWN response as available', () => {
    for (const response of ['<html>fallback</html>', null, { status: 'DOWN' }]) {
      api.response = of(response);
      service.check();
      expect(service.state()).toBe('unavailable');
    }
  });

  it('cancels an earlier check so a late response cannot overwrite the current result', () => {
    const old = new Subject<unknown>();
    api.response = old;
    service.check();
    api.response = of({ status: 'UP' });
    service.check();
    expect(old.observed).toBe(false);
    old.next({ status: 'DOWN' });
    expect(service.state()).toBe('available');
  });

  it('stops waiting after eight seconds when the server does not respond', () => {
    vi.useFakeTimers();
    api.response = new Subject<unknown>();
    service.check();
    vi.advanceTimersByTime(8001);
    expect(service.state()).toBe('unavailable');
    expect(service.message()).toBe('El servidor no confirmó su disponibilidad a tiempo.');
  });
});
