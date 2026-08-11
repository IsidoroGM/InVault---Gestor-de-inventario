import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ApiClient } from '../../core/services/api-client.service';
import { UserService } from './user.service';

describe('UserService', () => {
  const api = { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn() };

  function service(): UserService {
    TestBed.configureTestingModule({ providers: [{ provide: ApiClient, useValue: api }] });
    return TestBed.inject(UserService);
  }

  it('loads users and official active roles', () => {
    api.get.mockReturnValue(of([]));
    const instance = service();
    instance.findAll().subscribe();
    instance.loadRoles().subscribe();
    expect(api.get).toHaveBeenNthCalledWith(1, '/api/users');
    expect(api.get).toHaveBeenNthCalledWith(2, '/api/roles');
  });

  it('uses explicit lifecycle endpoints', () => {
    api.patch.mockReturnValue(of(undefined));
    api.put.mockReturnValue(of(undefined));
    const instance = service();
    instance.activate(4).subscribe();
    instance.deactivate(5).subscribe();
    instance.resetPassword(6, { temporaryPassword: 'Temporary!2026' }).subscribe();
    expect(api.patch).toHaveBeenNthCalledWith(1, '/api/users/4/activate', {});
    expect(api.patch).toHaveBeenNthCalledWith(2, '/api/users/5/deactivate', {});
    expect(api.put).toHaveBeenCalledWith('/api/users/6/password-reset', {
      temporaryPassword: 'Temporary!2026',
    });
  });
});
