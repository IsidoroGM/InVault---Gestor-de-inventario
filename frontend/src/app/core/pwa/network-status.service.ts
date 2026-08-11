import { DOCUMENT } from '@angular/common';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class NetworkStatusService {
  private readonly destroyRef = inject(DestroyRef);
  private readonly window = inject(DOCUMENT).defaultView;
  private readonly onlineState = signal(this.window?.navigator.onLine ?? true);

  readonly online = this.onlineState.asReadonly();
  readonly offline = computed(() => !this.onlineState());

  constructor() {
    if (!this.window) {
      return;
    }

    const markOnline = () => this.onlineState.set(true);
    const markOffline = () => this.onlineState.set(false);

    this.window.addEventListener('online', markOnline);
    this.window.addEventListener('offline', markOffline);
    this.destroyRef.onDestroy(() => {
      this.window?.removeEventListener('online', markOnline);
      this.window?.removeEventListener('offline', markOffline);
    });
  }
}
