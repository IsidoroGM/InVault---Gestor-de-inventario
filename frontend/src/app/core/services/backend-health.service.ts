import { inject, Injectable, signal } from '@angular/core';

import { ApiErrorResponse } from '../../shared/models/api-error.model';
import { ApiClient } from './api-client.service';

export type BackendHealthState = 'idle' | 'checking' | 'available' | 'unavailable';

@Injectable({ providedIn: 'root' })
export class BackendHealthService {
  private readonly api = inject(ApiClient);

  readonly state = signal<BackendHealthState>('idle');
  readonly message = signal('Pendiente de comprobación');

  check(): void {
    this.state.set('checking');
    this.message.set('Comprobando conexión…');

    this.api.getText('/api/health').subscribe({
      next: (message) => {
        this.state.set('available');
        this.message.set(message);
      },
      error: (error: unknown) => {
        this.state.set('unavailable');
        this.message.set(this.errorMessage(error));
      },
    });
  }

  private errorMessage(error: unknown): string {
    const apiError = error as Partial<ApiErrorResponse>;
    return typeof apiError.message === 'string'
      ? apiError.message
      : 'No se pudo comprobar el backend.';
  }
}
