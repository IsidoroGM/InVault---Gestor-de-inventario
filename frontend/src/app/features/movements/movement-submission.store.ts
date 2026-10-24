import { inject, Injectable } from '@angular/core';

import { AuthSessionStore } from '../../core/auth/auth-session.store';
import { MOVEMENT_TYPES, StockMovementRequest } from './movement.models';

export interface MovementSubmission {
  readonly key: string;
  readonly ownerId: number;
  readonly request: StockMovementRequest;
}

@Injectable({ providedIn: 'root' })
export class MovementSubmissionStore {
  private readonly auth = inject(AuthSessionStore);

  current(): MovementSubmission | null {
    const ownerId = this.auth.session()?.userId;
    if (!ownerId) return null;
    const serialized = sessionStorage.getItem(this.storageKey(ownerId));
    if (!serialized) return null;
    try {
      const value = JSON.parse(serialized) as MovementSubmission;
      const request = value.request;
      if (
        value.ownerId !== ownerId ||
        typeof value.key !== 'string' ||
        !/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(value.key) ||
        !request ||
        !Number.isSafeInteger(request.productId) ||
        request.productId <= 0 ||
        !Number.isSafeInteger(request.batchId) ||
        request.batchId <= 0 ||
        !Number.isFinite(request.quantity) ||
        request.quantity <= 0 ||
        !MOVEMENT_TYPES.includes(request.movementType) ||
        !(
          request.supplierId === null ||
          (Number.isSafeInteger(request.supplierId) && request.supplierId > 0)
        ) ||
        !(request.reason === null || typeof request.reason === 'string')
      )
        throw new Error('Invalid pending movement');
      return value;
    } catch {
      throw new Error(
        'No se puede leer el envío pendiente. Revisa el historial con un administrador antes de registrar otro movimiento.',
      );
    }
  }

  hasPending(): boolean {
    const ownerId = this.auth.session()?.userId;
    try {
      return !!ownerId && sessionStorage.getItem(this.storageKey(ownerId)) !== null;
    } catch {
      return true;
    }
  }

  begin(request: StockMovementRequest): MovementSubmission {
    if (this.current()) throw new Error('Confirma primero el movimiento pendiente.');
    const ownerId = this.auth.session()?.userId;
    if (!ownerId) throw new Error('Inicia sesión para registrar el movimiento.');
    const submission = { key: crypto.randomUUID(), ownerId, request: { ...request } };
    // Persist before sending; a reload after a lost response must keep the original key and payload.
    sessionStorage.setItem(this.storageKey(ownerId), JSON.stringify(submission));
    return submission;
  }

  complete(submission: MovementSubmission): void {
    const key = this.storageKey(submission.ownerId);
    const current = sessionStorage.getItem(key);
    if (current && (JSON.parse(current) as MovementSubmission).key === submission.key) {
      sessionStorage.removeItem(key);
    }
  }

  private storageKey(ownerId: number): string {
    return `invault.movement.pending.v1.${ownerId}`;
  }
}
