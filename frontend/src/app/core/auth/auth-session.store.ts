import { computed, Injectable, signal } from '@angular/core';

import { OFFICIAL_ROLES, Role } from '../../shared/models/role.model';
import { AuthSession, LoginResponse } from './auth.models';

const SESSION_STORAGE_KEY = 'invault.auth.session';

@Injectable({ providedIn: 'root' })
export class AuthSessionStore {
  private readonly sessionState = signal<AuthSession | null>(this.restore());

  readonly session = this.sessionState.asReadonly();
  readonly isAuthenticated = computed(() => {
    const session = this.sessionState();
    return session !== null && Date.parse(session.expiresAt) > Date.now();
  });
  readonly mustChangePassword = computed(
    () => this.isAuthenticated() && this.sessionState()?.mustChangePassword === true,
  );

  setFromLogin(response: LoginResponse): AuthSession {
    const session = this.normalize(response);
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    this.sessionState.set(session);
    return session;
  }

  clear(): void {
    sessionStorage.removeItem(SESSION_STORAGE_KEY);
    this.sessionState.set(null);
  }

  accessToken(): string | null {
    return this.hasValidSession() ? (this.sessionState()?.accessToken ?? null) : null;
  }

  hasAnyRole(roles: readonly Role[]): boolean {
    const session = this.sessionState();
    return this.hasValidSession() && roles.some((role) => session?.roles.includes(role));
  }

  hasValidSession(): boolean {
    const session = this.sessionState();
    if (!session || Date.parse(session.expiresAt) <= Date.now()) {
      if (session) {
        this.clear();
      }
      return false;
    }
    return true;
  }

  private restore(): AuthSession | null {
    try {
      const serialized = sessionStorage.getItem(SESSION_STORAGE_KEY);
      if (!serialized) {
        return null;
      }

      const session = this.normalize(JSON.parse(serialized) as LoginResponse);
      if (Date.parse(session.expiresAt) <= Date.now()) {
        sessionStorage.removeItem(SESSION_STORAGE_KEY);
        return null;
      }
      return session;
    } catch {
      sessionStorage.removeItem(SESSION_STORAGE_KEY);
      return null;
    }
  }

  private normalize(response: LoginResponse): AuthSession {
    const roles = Array.isArray(response.roles)
      ? response.roles.filter((role): role is Role => OFFICIAL_ROLES.includes(role as Role))
      : [];

    if (
      typeof response.accessToken !== 'string' ||
      response.accessToken.length === 0 ||
      response.tokenType !== 'Bearer' ||
      typeof response.userId !== 'number' ||
      response.userId <= 0 ||
      typeof response.username !== 'string' ||
      response.username.length === 0 ||
      roles.length === 0 ||
      !Number.isFinite(Date.parse(response.expiresAt)) ||
      Date.parse(response.expiresAt) <= Date.now() ||
      typeof response.expiresIn !== 'number' ||
      response.expiresIn <= 0
    ) {
      throw new Error('La respuesta de autenticación no contiene una sesión válida.');
    }

    return {
      accessToken: response.accessToken,
      tokenType: 'Bearer',
      expiresIn: response.expiresIn,
      expiresAt: response.expiresAt,
      userId: response.userId,
      username: response.username,
      roles,
      mustChangePassword: response.mustChangePassword === true,
    };
  }
}
