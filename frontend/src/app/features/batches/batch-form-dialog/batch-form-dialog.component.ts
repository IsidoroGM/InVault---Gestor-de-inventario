import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';

import { apiErrorMessage, apiFieldErrors } from '../../../shared/utils/api-error-message';
import {
  Batch,
  BatchProductOption,
  BatchRequest,
  BatchStatus,
  BATCH_STATUS_LABELS,
} from '../batch.models';
import { BatchService } from '../batch.service';

export interface BatchFormDialogData {
  readonly batch: Batch | null;
  readonly products: readonly BatchProductOption[];
}

@Component({
  selector: 'app-batch-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './batch-form-dialog.component.html',
  styleUrl: './batch-form-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BatchFormDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<BatchFormDialogComponent, Batch>);
  private readonly service = inject(BatchService);
  protected readonly data = inject<BatchFormDialogData>(MAT_DIALOG_DATA);
  protected readonly editing = this.data.batch !== null;
  protected readonly statusLabels = BATCH_STATUS_LABELS;
  protected readonly statusOptions = allowedStatuses(this.data.batch?.status ?? null);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly form = new FormGroup({
    productId: new FormControl<number | null>(this.data.batch?.productId ?? null, [
      Validators.required,
    ]),
    batchCode: new FormControl(this.data.batch?.batchCode ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    status: new FormControl<BatchStatus>(this.data.batch?.status ?? 'AVAILABLE', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    notes: new FormControl(this.data.batch?.notes ?? '', {
      nonNullable: true,
      validators: [Validators.maxLength(255)],
    }),
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request: BatchRequest = {
      productId: value.productId as number,
      batchCode: value.batchCode.trim().toUpperCase(),
      status: value.status,
      notes: value.notes.trim() || null,
    };
    const operation = this.data.batch
      ? this.service.update(this.data.batch.id, request)
      : this.service.create(request);

    this.errorMessage.set(null);
    this.submitting.set(true);
    operation.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (batch) => this.dialogRef.close(batch),
      error: (error: unknown) => {
        this.applyFieldErrors(apiFieldErrors(error));
        this.errorMessage.set(
          apiErrorMessage(
            error,
            'No se pudo guardar el lote.',
            'Tu rol no permite modificar lotes.',
          ),
        );
      },
    });
  }

  protected serverError(controlName: keyof typeof this.form.controls): string | null {
    return (this.form.controls[controlName].getError('server') as string | undefined) ?? null;
  }

  private applyFieldErrors(errors: Readonly<Record<string, string>>): void {
    for (const [name, message] of Object.entries(errors)) {
      if (name in this.form.controls) {
        const control = this.form.controls[name as keyof typeof this.form.controls];
        control.setErrors({ ...control.errors, server: message });
      }
    }
  }
}

function allowedStatuses(current: BatchStatus | null): readonly BatchStatus[] {
  switch (current) {
    case 'AVAILABLE':
      return ['AVAILABLE', 'BLOCKED', 'INACTIVE'];
    case 'BLOCKED':
      return ['BLOCKED', 'AVAILABLE', 'INACTIVE'];
    case 'CONSUMED':
      return ['CONSUMED', 'INACTIVE'];
    case 'INACTIVE':
      return ['INACTIVE', 'AVAILABLE', 'BLOCKED'];
    default:
      return ['AVAILABLE', 'BLOCKED', 'INACTIVE'];
  }
}
