import { FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { UnitCatalogItem } from '../catalog.models';
import { CatalogService } from '../catalog.service';
import { CatalogFormDialogComponent, CatalogFormDialogData } from './catalog-form-dialog.component';

interface TestableCatalogFormDialog {
  readonly form: FormGroup;
  submit(): void;
}

describe('CatalogFormDialogComponent', () => {
  const savedUnit = { id: 5, code: 'KG', name: 'Kilogramo' } as UnitCatalogItem;
  const service = {
    create: vi.fn(() => of(savedUnit)),
    update: vi.fn(() => of(savedUnit)),
  };
  const dialogRef = { close: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('normalizes and creates a unit catalogue entry', async () => {
    const component = await createComponent({ kind: 'units', item: null });
    const testable = component as unknown as TestableCatalogFormDialog;
    testable.form.setValue({
      code: ' kg ',
      name: ' Kilogramo ',
      symbol: ' kg ',
      description: ' Unidad de masa ',
      contactName: '',
      phone: '',
      email: '',
      notes: '',
      active: true,
    });

    testable.submit();

    expect(service.create).toHaveBeenCalledWith('units', {
      code: 'KG',
      name: 'Kilogramo',
      symbol: 'kg',
      description: 'Unidad de masa',
      active: true,
    });
    expect(dialogRef.close).toHaveBeenCalledWith(savedUnit);
  });

  it('does not submit a catalogue entry without a name', async () => {
    const component = await createComponent({ kind: 'categories', item: null });
    const testable = component as unknown as TestableCatalogFormDialog;

    testable.submit();

    expect(service.create).not.toHaveBeenCalled();
    expect(testable.form.controls['name'].touched).toBe(true);
  });

  it('keeps an existing locked identity exactly while saving a description', async () => {
    const unit: UnitCatalogItem = {
      ...savedUnit,
      code: 'kg',
      symbol: 'kg',
      description: null,
      identityLocked: true,
      active: true,
    };
    const component = (await createComponent({
      kind: 'units',
      item: unit,
    })) as unknown as TestableCatalogFormDialog;
    expect(component.form.controls['code'].disabled).toBe(true);
    expect(component.form.controls['name'].disabled).toBe(true);
    expect(component.form.controls['symbol'].disabled).toBe(true);
    component.form.patchValue({
      code: 'L',
      name: 'Litro',
      symbol: 'L',
      description: 'Descripción revisada',
    });
    component.submit();
    expect(service.update).toHaveBeenCalledWith('units', 5, {
      code: 'kg',
      name: 'Kilogramo',
      symbol: 'kg',
      description: 'Descripción revisada',
      active: true,
    });
  });

  async function createComponent(data: CatalogFormDialogData): Promise<CatalogFormDialogComponent> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [CatalogFormDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: CatalogService, useValue: service },
      ],
    }).compileComponents();
    return TestBed.createComponent(CatalogFormDialogComponent).componentInstance;
  }
});
