import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/services/api-client.service';
import {
  PasswordResetRequest,
  RoleOption,
  UserAccount,
  UserCreateRequest,
  UserUpdateRequest,
} from './user.models';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly api = inject(ApiClient);

  findAll(): Observable<readonly UserAccount[]> {
    return this.api.get<readonly UserAccount[]>('/api/users');
  }

  loadRoles(): Observable<readonly RoleOption[]> {
    return this.api.get<readonly RoleOption[]>('/api/roles');
  }

  create(request: UserCreateRequest): Observable<UserAccount> {
    return this.api.post<UserCreateRequest, UserAccount>('/api/users', request);
  }

  update(id: number, request: UserUpdateRequest): Observable<UserAccount> {
    return this.api.put<UserUpdateRequest, UserAccount>(`/api/users/${id}`, request);
  }

  activate(id: number): Observable<void> {
    return this.api.patch<Record<string, never>, void>(`/api/users/${id}/activate`, {});
  }

  deactivate(id: number): Observable<void> {
    return this.api.patch<Record<string, never>, void>(`/api/users/${id}/deactivate`, {});
  }

  resetPassword(id: number, request: PasswordResetRequest): Observable<void> {
    return this.api.put<PasswordResetRequest, void>(`/api/users/${id}/password-reset`, request);
  }
}
