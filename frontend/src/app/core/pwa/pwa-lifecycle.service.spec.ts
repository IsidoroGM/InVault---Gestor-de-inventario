import { TestBed } from '@angular/core/testing';
import { SwUpdate } from '@angular/service-worker';
import { EMPTY } from 'rxjs';
import { vi } from 'vitest';

import { PwaLifecycleService } from './pwa-lifecycle.service';

class SwUpdateStub {
  readonly isEnabled = false;
  readonly versionUpdates = EMPTY;
  readonly unrecoverable = EMPTY;
  readonly activateUpdate = vi.fn(() => Promise.resolve(true));
  readonly checkForUpdate = vi.fn(() => Promise.resolve(false));
}

describe('PwaLifecycleService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SwUpdateStub, { provide: SwUpdate, useExisting: SwUpdateStub }],
    });
  });

  it('captures and resolves the browser install prompt', async () => {
    const service = TestBed.inject(PwaLifecycleService);
    const prompt = vi.fn(() => Promise.resolve());
    const event = Object.assign(new Event('beforeinstallprompt', { cancelable: true }), {
      prompt,
      userChoice: Promise.resolve({ outcome: 'accepted' as const }),
    });

    window.dispatchEvent(event);
    expect(service.canInstall()).toBe(true);

    await service.install();

    expect(prompt).toHaveBeenCalledOnce();
    expect(service.installed()).toBe(true);
    expect(service.canInstall()).toBe(false);
  });
});
