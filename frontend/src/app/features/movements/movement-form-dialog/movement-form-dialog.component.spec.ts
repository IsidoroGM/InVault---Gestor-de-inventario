import { Signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { vi } from 'vitest';

import { MovementBatchOption } from '../movement.models';
import { MovementService } from '../movement.service';
import { MovementFormDialogComponent } from './movement-form-dialog.component';

interface TestableDialog {
  form: FormGroup;
  batches: Signal<readonly MovementBatchOption[]>;
  loadingBatches: Signal<boolean>;
  errorMessage: Signal<string | null>;
}

describe('MovementFormDialogComponent batch loading', () => {
  const service = { loadBatches: vi.fn(), create: vi.fn() };

  beforeEach(() => vi.clearAllMocks());

  async function create(): Promise<TestableDialog> {
    await TestBed.configureTestingModule({
      imports: [MovementFormDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { products: [], suppliers: [] } },
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        { provide: MovementService, useValue: service },
      ],
    }).compileComponents();
    return TestBed.createComponent(MovementFormDialogComponent)
      .componentInstance as unknown as TestableDialog;
  }

  it('cancels the old product request so a late response cannot replace the new batches', async () => {
    const oldRequest = new Subject<readonly MovementBatchOption[]>();
    const newRequest = new Subject<readonly MovementBatchOption[]>();
    service.loadBatches.mockReturnValueOnce(oldRequest).mockReturnValueOnce(newRequest);
    const component = await create();

    component.form.controls['productId'].setValue(1);
    component.form.controls['batchId'].setValue(7);
    component.form.controls['productId'].setValue(2);
    expect(oldRequest.observed).toBe(false);
    expect(component.form.controls['batchId'].value).toBeNull();

    oldRequest.next([{ id: 7, productId: 1 } as MovementBatchOption]);
    newRequest.next([{ id: 8, productId: 2 } as MovementBatchOption]);
    newRequest.complete();

    expect(component.batches()[0].id).toBe(8);
    expect(component.loadingBatches()).toBe(false);
  });

  it('cancels loading and clears options when the product is cleared', async () => {
    const request = new Subject<readonly MovementBatchOption[]>();
    service.loadBatches.mockReturnValue(request);
    const component = await create();
    component.form.controls['productId'].setValue(1);
    component.form.controls['productId'].setValue(null);
    expect(request.observed).toBe(false);
    expect(component.batches()).toEqual([]);
    expect(component.loadingBatches()).toBe(false);
  });

  it('can load another product after a request fails', async () => {
    const failed = new Subject<readonly MovementBatchOption[]>();
    const recovered = new Subject<readonly MovementBatchOption[]>();
    service.loadBatches.mockReturnValueOnce(failed).mockReturnValueOnce(recovered);
    const component = await create();
    component.form.controls['productId'].setValue(1);
    failed.error(new Error('Failed'));
    expect(component.errorMessage()).toBeTruthy();

    component.form.controls['productId'].setValue(2);
    recovered.next([{ id: 8, productId: 2 } as MovementBatchOption]);
    recovered.complete();
    expect(component.batches()[0].id).toBe(8);
    expect(component.errorMessage()).toBeNull();
  });
});
