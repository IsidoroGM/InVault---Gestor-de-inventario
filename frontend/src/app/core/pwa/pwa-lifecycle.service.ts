import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { SwUpdate, VersionEvent } from '@angular/service-worker';

import { NetworkStatusService } from './network-status.service';

interface BeforeInstallPromptEvent extends Event {
  readonly userChoice: Promise<{ readonly outcome: 'accepted' | 'dismissed' }>;
  prompt(): Promise<void>;
}

const UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000;

@Injectable({ providedIn: 'root' })
export class PwaLifecycleService {
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly network = inject(NetworkStatusService);
  private readonly updates = inject(SwUpdate);
  private readonly window = this.document.defaultView;
  private installPrompt: BeforeInstallPromptEvent | null = null;
  private updateTimer: number | null = null;

  readonly canInstall = signal(false);
  readonly installing = signal(false);
  readonly installed = signal(this.isStandalone());
  readonly updateAvailable = signal(false);
  readonly updating = signal(false);
  readonly updateError = signal<string | null>(null);

  constructor() {
    this.listenForInstallation();
    this.listenForUpdates();
  }

  async install(): Promise<void> {
    const prompt = this.installPrompt;
    if (!prompt || this.installing()) {
      return;
    }

    this.installing.set(true);
    try {
      await prompt.prompt();
      const choice = await prompt.userChoice;
      this.canInstall.set(false);
      this.installPrompt = null;
      if (choice.outcome === 'accepted') {
        this.installed.set(true);
      }
    } finally {
      this.installing.set(false);
    }
  }

  async activateUpdate(): Promise<void> {
    if (!this.updates.isEnabled || this.updating()) {
      return;
    }

    this.updating.set(true);
    this.updateError.set(null);
    try {
      await this.updates.activateUpdate();
      this.document.location.reload();
    } catch {
      this.updateError.set('No se pudo activar la actualización. Inténtalo de nuevo.');
      this.updating.set(false);
    }
  }

  private listenForInstallation(): void {
    if (!this.window) {
      return;
    }

    const beforeInstall = (event: Event) => {
      event.preventDefault();
      this.installPrompt = event as BeforeInstallPromptEvent;
      this.canInstall.set(!this.installed());
    };
    const installed = () => {
      this.installPrompt = null;
      this.canInstall.set(false);
      this.installed.set(true);
    };

    this.window.addEventListener('beforeinstallprompt', beforeInstall);
    this.window.addEventListener('appinstalled', installed);
    this.destroyRef.onDestroy(() => {
      this.window?.removeEventListener('beforeinstallprompt', beforeInstall);
      this.window?.removeEventListener('appinstalled', installed);
    });
  }

  private listenForUpdates(): void {
    if (!this.updates.isEnabled || !this.window) {
      return;
    }

    const subscription = this.updates.versionUpdates.subscribe((event) =>
      this.handleVersionEvent(event),
    );
    const unrecoverableSubscription = this.updates.unrecoverable.subscribe(() => {
      this.updateError.set('La versión instalada debe recargarse para poder continuar.');
      this.updateAvailable.set(true);
    });
    const checkWhenOnline = () => void this.checkForUpdate();

    this.window.addEventListener('online', checkWhenOnline);
    this.updateTimer = this.window.setInterval(checkWhenOnline, UPDATE_CHECK_INTERVAL_MS);
    void this.checkForUpdate();

    this.destroyRef.onDestroy(() => {
      subscription.unsubscribe();
      unrecoverableSubscription.unsubscribe();
      this.window?.removeEventListener('online', checkWhenOnline);
      if (this.updateTimer !== null) {
        this.window?.clearInterval(this.updateTimer);
      }
    });
  }

  private handleVersionEvent(event: VersionEvent): void {
    if (event.type === 'VERSION_READY') {
      this.updateAvailable.set(true);
      this.updateError.set(null);
    } else if (event.type === 'VERSION_INSTALLATION_FAILED') {
      this.updateError.set('No se pudo descargar la nueva versión de InVault.');
    }
  }

  private async checkForUpdate(): Promise<void> {
    if (!this.network.online()) {
      return;
    }
    try {
      await this.updates.checkForUpdate();
    } catch {
      // A transient update failure must not interrupt inventory work.
    }
  }

  private isStandalone(): boolean {
    return (
      this.window?.matchMedia?.('(display-mode: standalone)').matches === true ||
      (this.window?.navigator as (Navigator & { standalone?: boolean }) | undefined)?.standalone ===
        true
    );
  }
}
