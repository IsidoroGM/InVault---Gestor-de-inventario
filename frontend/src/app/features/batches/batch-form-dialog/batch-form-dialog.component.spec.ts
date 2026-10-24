import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { Batch } from '../batch.models';
import { BatchService } from '../batch.service';
import { BatchFormDialogComponent } from './batch-form-dialog.component';

describe('BatchFormDialogComponent', () => {
  const batch: Batch = {
    id: 7,
    productId: 1,
    productSku: 'RAW-001',
    productName: 'Material',
    batchCode: 'LOT-001',
    quantity: 18.5,
    status: 'AVAILABLE',
    notes: null,
    createdAt: '',
    updatedAt: '',
  };
  const service = { create: vi.fn(() => of(batch)), update: vi.fn(() => of(batch)) };

  beforeEach(() => vi.clearAllMocks());

  async function create(existing: Batch | null) {
    await TestBed.configureTestingModule({
      imports: [BatchFormDialogComponent],
      providers: [
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            batch: existing,
            products: [{ id: 1, sku: 'RAW-001', name: 'Material', unitSymbol: 'kg' }],
          },
        },
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        { provide: BatchService, useValue: service },
      ],
    }).compileComponents();
    return TestBed.createComponent(BatchFormDialogComponent).componentInstance as unknown as {
      form: FormGroup;
      submit(): void;
    };
  }

  it('locks the original product while preserving it in update requests', async () => {
    const component = await create(batch);
    expect(component.form.controls['productId'].disabled).toBe(true);
    component.form.controls['notes'].setValue('Revisado');
    component.submit();
    expect(service.update).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ productId: 1, notes: 'Revisado' }),
    );
  });

  it('allows selecting a product for a new batch', async () => {
    const component = await create(null);
    expect(component.form.controls['productId'].enabled).toBe(true);
    component.form.patchValue({ productId: 1, batchCode: ' lot-002 ' });
    component.submit();
    expect(service.create).toHaveBeenCalledWith(
      expect.objectContaining({ productId: 1, batchCode: 'LOT-002' }),
    );
  });
});
