import { inject, Injectable } from '@angular/core';
import { catchError, finalize, Observable, of, tap } from 'rxjs';

import { ApiClient } from '../services/api-client.service';
import { AuthSession, LoginRequest, LoginResponse, PasswordChangeRequest } from './auth.models';
import { AuthSessionStore } from './auth-session.store';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiClient);
  private readonly store = inject(AuthSessionStore);

  login(credentials: LoginRequest): Observable<AuthSession> {
    return this.api
      .post<LoginRequest, LoginResponse>('/api/auth/login', credentials)
      .pipe(tap((response) => this.store.setFromLogin(response)));
  }

  logout(): Observable<void> {
    if (!this.store.accessToken()) {
      this.store.clear();
      return of(undefined);
    }

    return this.api.post<Record<string, never>, void>('/api/auth/logout', {}).pipe(
      catchError(() => of(undefined)),
      finalize(() => this.store.clear()),
    );
  }

  changePassword(request: PasswordChangeRequest): Observable<void> {
    return this.api
      .put<PasswordChangeRequest, void>('/api/users/me/password', request)
      .pipe(tap(() => this.store.clear()));
  }
}
