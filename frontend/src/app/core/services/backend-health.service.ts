import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, timeout, TimeoutError } from 'rxjs';

import { ApiErrorResponse } from '../../shared/models/api-error.model';
import { ApiClient } from './api-client.service';

export type BackendHealthState = 'idle' | 'checking' | 'available' | 'unavailable';

@Injectable({ providedIn: 'root' })
export class BackendHealthService {
  private readonly api = inject(ApiClient);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;

  readonly state = signal<BackendHealthState>('idle');
  readonly message = signal('Pendiente de comprobación');

  check(): void {
    this.request?.unsubscribe();
    this.state.set('checking');
    this.message.set('Comprobando conexión…');

    this.request = this.api
      .get<{ readonly status?: string }>('/api/health/readiness')
      .pipe(timeout({ first: 8000 }), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          const ready = response?.status === 'UP';
          this.state.set(ready ? 'available' : 'unavailable');
          this.message.set(
            ready
              ? 'Servidor y base de datos disponibles.'
              : 'El servicio de inventario no está disponible.',
          );
        },
        error: (error: unknown) => {
          this.state.set('unavailable');
          this.message.set(this.errorMessage(error));
        },
      });
  }

  private errorMessage(error: unknown): string {
    if (error instanceof TimeoutError) {
      return 'El servidor no confirmó su disponibilidad a tiempo.';
    }
    if (typeof error === 'object' && error !== null) {
      const apiError = error as Partial<ApiErrorResponse>;
      if (apiError.status === 0 && typeof apiError.message === 'string') {
        return apiError.message;
      }
    }
    return 'El servidor o su base de datos no están disponibles.';
  }
}
