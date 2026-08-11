import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ApiClient } from '../../core/services/api-client.service';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  it('loads the aggregated backend dashboard', () => {
    const api = { get: vi.fn().mockReturnValue(of({})) };
    TestBed.configureTestingModule({ providers: [{ provide: ApiClient, useValue: api }] });
    TestBed.inject(DashboardService).load().subscribe();
    expect(api.get).toHaveBeenCalledWith('/api/dashboard');
  });
});
