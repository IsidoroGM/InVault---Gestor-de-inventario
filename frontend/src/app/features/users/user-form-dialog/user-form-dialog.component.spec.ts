import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { UserAccount } from '../user.models';
import { UserService } from '../user.service';
import { UserFormDialogComponent, UserFormDialogData } from './user-form-dialog.component';

interface TestableUserFormDialog {
  readonly form: FormGroup;
  submit(): void;
}

describe('UserFormDialogComponent', () => {
  const roles = [
    { id: 1, name: 'ADMIN' as const, description: 'Admin', active: true },
    { id: 2, name: 'READ_ONLY' as const, description: 'Read', active: true },
  ];
  const saved = { id: 8, username: 'operator' } as UserAccount;
  const service = { create: vi.fn(() => of(saved)), update: vi.fn(() => of(saved)) };
  const dialogRef = { close: vi.fn() };

  beforeEach(() => vi.clearAllMocks());

  it('creates a normalized user with a temporary password', async () => {
    const testable = (await createComponent({
      user: null,
      roles,
    })) as unknown as TestableUserFormDialog;
    testable.form.setValue({
      username: ' Operator ',
      email: 'OPERATOR@EXAMPLE.COM',
      temporaryPassword: 'Temporary!2026',
      roles: ['READ_ONLY'],
    });
    testable.submit();
    expect(service.create).toHaveBeenCalledWith({
      username: 'operator',
      email: 'operator@example.com',
      temporaryPassword: 'Temporary!2026',
      roles: ['READ_ONLY'],
    });
  });

  it('updates only mutable account fields', async () => {
    const user = { ...saved, email: 'old@example.com', roles: ['READ_ONLY'] } as UserAccount;
    const testable = (await createComponent({ user, roles })) as unknown as TestableUserFormDialog;
    testable.form.patchValue({ email: 'NEW@EXAMPLE.COM', roles: ['ADMIN'] });
    testable.submit();
    expect(service.update).toHaveBeenCalledWith(8, {
      email: 'new@example.com',
      roles: ['ADMIN'],
    });
  });

  async function createComponent(data: UserFormDialogData): Promise<UserFormDialogComponent> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [UserFormDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: UserService, useValue: service },
      ],
    }).compileComponents();
    return TestBed.createComponent(UserFormDialogComponent).componentInstance;
  }
});
