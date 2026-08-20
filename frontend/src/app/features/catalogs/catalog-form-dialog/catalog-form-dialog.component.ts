import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { finalize, Observable } from 'rxjs';

import {
  CatalogDefinition,
  CatalogItem,
  CatalogKind,
  CATALOG_DEFINITIONS,
  NamedCatalogItem,
  NamedCatalogRequest,
  SupplierCatalogItem,
  SupplierCatalogRequest,
  UnitCatalogItem,
  UnitCatalogRequest,
} from '../catalog.models';
import { catalogErrorMessage, catalogFieldErrors } from '../catalog-error-message';
import { CatalogService } from '../catalog.service';

export interface CatalogFormDialogData {
  readonly kind: CatalogKind;
  readonly item: CatalogItem | null;
}

@Component({
  selector: 'app-catalog-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
  ],
  templateUrl: './catalog-form-dialog.component.html',
  styleUrl: './catalog-form-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogFormDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<CatalogFormDialogComponent, CatalogItem>);
  private readonly service = inject(CatalogService);
  protected readonly data = inject<CatalogFormDialogData>(MAT_DIALOG_DATA);
  protected readonly definition = definitionFor(this.data.kind);
  protected readonly editing = this.data.item !== null;
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  private readonly namedItem = this.data.item as NamedCatalogItem | null;
  private readonly unitItem =
    this.data.kind === 'units' ? (this.data.item as UnitCatalogItem) : null;
  private readonly supplierItem =
    this.data.kind === 'suppliers' ? (this.data.item as SupplierCatalogItem) : null;

  protected readonly form = new FormGroup({
    code: new FormControl(this.unitItem?.code ?? '', {
      nonNullable: true,
      validators:
        this.data.kind === 'units'
          ? [Validators.required, Validators.maxLength(30)]
          : [Validators.maxLength(30)],
    }),
    name: new FormControl(this.data.item?.name ?? '', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(this.data.kind === 'suppliers' ? 150 : 100),
      ],
    }),
    symbol: new FormControl(this.unitItem?.symbol ?? '', {
      nonNullable: true,
      validators: [Validators.maxLength(20)],
    }),
    description: new FormControl(this.namedItem?.description ?? '', {
      nonNullable: true,
      validators: [Validators.maxLength(255)],
    }),
    contactName: new FormControl(this.supplierItem?.contactName ?? '', {
      nonNullable: true,
      validators: [Validators.maxLength(120)],
    }),
    phone: new FormControl(this.supplierItem?.phone ?? '', {
      nonNullable: true,
      validators: [Validators.maxLength(30)],
    }),
    email: new FormControl(this.supplierItem?.email ?? '', {
      nonNullable: true,
      validators: [Validators.email, Validators.maxLength(120)],
    }),
    notes: new FormControl(this.supplierItem?.notes ?? '', {
      nonNullable: true,
      validators: [Validators.maxLength(255)],
    }),
    active: new FormControl(this.data.item?.active ?? true, { nonNullable: true }),
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.clearServerErrors();
    this.errorMessage.set(null);
    this.submitting.set(true);
    this.save()
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (item) => this.dialogRef.close(item),
        error: (error: unknown) => {
          this.applyFieldErrors(catalogFieldErrors(error));
          this.errorMessage.set(
            catalogErrorMessage(error, `No se pudo guardar la ${this.definition.singular}.`),
          );
        },
      });
  }

  protected serverError(controlName: keyof typeof this.form.controls): string | null {
    return (this.form.controls[controlName].getError('server') as string | undefined) ?? null;
  }

  private save(): Observable<CatalogItem> {
    const id = this.data.item?.id;
    switch (this.data.kind) {
      case 'categories': {
        const request = this.namedRequest();
        return id
          ? this.service.update('categories', id, request)
          : this.service.create('categories', request);
      }
      case 'locations': {
        const request = this.namedRequest();
        return id
          ? this.service.update('locations', id, request)
          : this.service.create('locations', request);
      }
      case 'units': {
        const request = this.unitRequest();
        return id
          ? this.service.update('units', id, request)
          : this.service.create('units', request);
      }
      case 'suppliers': {
        const request = this.supplierRequest();
        return id
          ? this.service.update('suppliers', id, request)
          : this.service.create('suppliers', request);
      }
    }
  }

  private namedRequest(): NamedCatalogRequest {
    const value = this.form.getRawValue();
    return {
      name: value.name.trim(),
      description: nullable(value.description),
      active: value.active,
    };
  }

  private unitRequest(): UnitCatalogRequest {
    return {
      ...this.namedRequest(),
      code: this.form.controls.code.value.trim().toUpperCase(),
      symbol: nullable(this.form.controls.symbol.value),
    };
  }

  private supplierRequest(): SupplierCatalogRequest {
    const value = this.form.getRawValue();
    return {
      name: value.name.trim(),
      contactName: nullable(value.contactName),
      phone: nullable(value.phone),
      email: nullable(value.email)?.toLowerCase() ?? null,
      notes: nullable(value.notes),
      active: value.active,
    };
  }

  private applyFieldErrors(errors: Readonly<Record<string, string>>): void {
    for (const [name, message] of Object.entries(errors)) {
      if (name in this.form.controls) {
        const control = this.form.controls[name as keyof typeof this.form.controls];
        control.setErrors({ ...control.errors, server: message });
      }
    }
  }

  private clearServerErrors(): void {
    for (const control of Object.values(this.form.controls)) {
      if (!control.hasError('server')) {
        continue;
      }
      const { server: _server, ...remaining } = control.errors ?? {};
      control.setErrors(Object.keys(remaining).length > 0 ? remaining : null);
    }
  }
}

function definitionFor(kind: CatalogKind): CatalogDefinition {
  return CATALOG_DEFINITIONS.find((definition) => definition.kind === kind) as CatalogDefinition;
}

function nullable(value: string): string | null {
  return value.trim() || null;
}
