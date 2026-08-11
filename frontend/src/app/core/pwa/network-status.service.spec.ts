import { TestBed } from '@angular/core/testing';

import { NetworkStatusService } from './network-status.service';

describe('NetworkStatusService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('tracks browser offline and online events', () => {
    const service = TestBed.inject(NetworkStatusService);

    window.dispatchEvent(new Event('offline'));
    expect(service.offline()).toBe(true);

    window.dispatchEvent(new Event('online'));
    expect(service.online()).toBe(true);
  });
});
