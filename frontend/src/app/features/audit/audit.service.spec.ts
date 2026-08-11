import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ApiClient } from '../../core/services/api-client.service';
import { AuditService } from './audit.service';

describe('AuditService', () => {
  it('builds all server-side audit filters', () => {
    const api = { get: vi.fn().mockReturnValue(of({ content: [] })) };
    TestBed.configureTestingModule({ providers: [{ provide: ApiClient, useValue: api }] });
    TestBed.inject(AuditService)
      .search({
        action: 'UPDATED',
        entityName: ' User ',
        userId: 3,
        from: '2026-08-01T09:00',
        to: '2026-08-11T18:30',
        page: 1,
        size: 50,
      })
      .subscribe();
    expect(api.get).toHaveBeenCalledWith(
      '/api/audit-logs?page=1&size=50&action=UPDATED&entityName=User&userId=3&from=2026-08-01T09%3A00%3A00&to=2026-08-11T18%3A30%3A00',
    );
  });
});
