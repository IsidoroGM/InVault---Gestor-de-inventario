import { Signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, Subject } from 'rxjs';
import { vi } from 'vitest';

import { MovementBatchOption, StockMovement, StockMovementRequest } from '../movement.models';
import { MovementSubmission, MovementSubmissionStore } from '../movement-submission.store';
import { NetworkStatusService } from '../../../core/pwa/network-status.service';
import { MovementService } from '../movement.service';
import { MovementFormDialogComponent } from './movement-form-dialog.component';

interface TestableDialog {
  form: FormGroup;
  batches: Signal<readonly MovementBatchOption[]>;
  loadingBatches: Signal<boolean>;
  errorMessage: Signal<string | null>;
  pendingSubmission: Signal<MovementSubmission | null>;
  submitting: Signal<boolean>;
  submit(): void;
}

describe('MovementFormDialogComponent batch loading', () => {
  const service = { loadBatches: vi.fn(), create: vi.fn() };
  const submissions = { current: vi.fn(), begin: vi.fn(), complete: vi.fn() };
  const dialog = { close: vi.fn() };
  const online = vi.fn();
  const key = '2b4e8f96-9d25-49da-a4f3-d6fe532c947e';

  beforeEach(() => {
    vi.resetAllMocks();
    online.mockReturnValue(true);
    service.loadBatches.mockReturnValue(of([]));
    submissions.current.mockReturnValue(null);
    submissions.begin.mockImplementation((request: StockMovementRequest) => ({
      key,
      ownerId: 7,
      request: { ...request },
    }));
  });

  async function create(): Promise<TestableDialog> {
    await TestBed.configureTestingModule({
      imports: [MovementFormDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { products: [], suppliers: [] } },
        { provide: MatDialogRef, useValue: dialog },
        { provide: MovementService, useValue: service },
        { provide: MovementSubmissionStore, useValue: submissions },
        { provide: NetworkStatusService, useValue: { online } },
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

  it('blocks double submit and retries the original frozen request after a lost response', async () => {
    const first = new Subject<StockMovement>();
    service.create.mockReturnValueOnce(first).mockReturnValueOnce(of({ id: 42 }));
    const component = await create();
    component.form.patchValue({ productId: 2, batchId: 3, quantity: 4, reason: 'Recepción' });
    component.submit();
    component.submit();
    expect(service.create).toHaveBeenCalledTimes(1);
    first.error({ status: 0 });
    expect(component.form.disabled).toBe(true);
    component.form.controls['quantity'].setValue(99);
    component.submit();
    expect(submissions.begin).toHaveBeenCalledTimes(1);
    expect(service.create.mock.calls[1]).toEqual(service.create.mock.calls[0]);
    expect(service.create.mock.calls[1][0].quantity).toBe(4);
    expect(submissions.complete).toHaveBeenCalledWith(expect.objectContaining({ key }));
    expect(dialog.close).toHaveBeenCalledWith({ id: 42 });
  });

  it('resumes a persisted pending movement and permits an explicit retry without editing', async () => {
    const request = {
      productId: 2,
      batchId: 3,
      quantity: 4,
      movementType: 'INBOUND',
      supplierId: null,
      reason: null,
    };
    submissions.current.mockReturnValue({ key, ownerId: 7, request });
    service.create.mockReturnValue(of({ id: 42 }));
    const component = await create();
    expect(component.form.disabled).toBe(true);
    component.submit();
    expect(submissions.begin).not.toHaveBeenCalled();
    expect(service.create).toHaveBeenCalledWith(request, key);
  });

  it('permits correction after a confirmed validation failure', async () => {
    const first = new Subject<StockMovement>();
    service.create.mockReturnValue(first);
    const component = await create();
    component.form.patchValue({ productId: 2, batchId: 3, quantity: 4 });
    component.submit();
    first.error({ status: 400 });
    expect(component.pendingSubmission()).toBeNull();
    expect(component.form.enabled).toBe(true);
    expect(submissions.complete).toHaveBeenCalledOnce();
  });

  it('does not queue a new movement while the browser reports offline', async () => {
    online.mockReturnValue(false);
    const component = await create();
    component.form.patchValue({ productId: 2, batchId: 3, quantity: 4 });
    component.submit();
    expect(service.create).not.toHaveBeenCalled();
    expect(submissions.begin).not.toHaveBeenCalled();
  });
});
