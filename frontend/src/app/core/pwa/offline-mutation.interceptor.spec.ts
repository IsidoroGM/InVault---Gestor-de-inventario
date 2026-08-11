import { HttpRequest, HttpResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of } from 'rxjs';
import { vi } from 'vitest';

import { NetworkStatusService } from './network-status.service';
import { offlineMutationInterceptor } from './offline-mutation.interceptor';

class NetworkStatusStub {
  readonly online = signal(true);
}

describe('offlineMutationInterceptor', () => {
  let network: NetworkStatusStub;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        NetworkStatusStub,
        { provide: NetworkStatusService, useExisting: NetworkStatusStub },
      ],
    });
    network = TestBed.inject(NetworkStatusStub);
  });

  it('allows mutations while online', async () => {
    const next = vi.fn(() => of(new HttpResponse({ status: 200 })));
    const request = new HttpRequest('POST', '/api/stock/movements', {});

    await TestBed.runInInjectionContext(() =>
      firstValueFrom(offlineMutationInterceptor(request, next)),
    );

    expect(next).toHaveBeenCalledOnce();
  });

  it('allows read-only requests while offline', async () => {
    network.online.set(false);
    const next = vi.fn(() => of(new HttpResponse({ status: 200 })));
    const request = new HttpRequest('GET', '/api/products');

    await TestBed.runInInjectionContext(() =>
      firstValueFrom(offlineMutationInterceptor(request, next)),
    );

    expect(next).toHaveBeenCalledOnce();
  });

  it('blocks inventory mutations while offline', async () => {
    network.online.set(false);
    const next = vi.fn(() => of(new HttpResponse({ status: 200 })));
    const request = new HttpRequest('POST', '/api/stock/movements', {});

    await expect(
      TestBed.runInInjectionContext(() =>
        firstValueFrom(offlineMutationInterceptor(request, next)),
      ),
    ).rejects.toMatchObject({
      status: 503,
      error: 'Offline',
    });
    expect(next).not.toHaveBeenCalled();
  });
});
