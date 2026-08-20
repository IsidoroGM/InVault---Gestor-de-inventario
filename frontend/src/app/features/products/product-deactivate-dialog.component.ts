import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

import { Product } from './product.models';

@Component({
  selector: 'app-product-deactivate-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Desactivar producto</h2>
    <mat-dialog-content>
      <p>
        ¿Quieres desactivar <strong>{{ product.name }}</strong> ({{ product.sku }})?
      </p>
      <p class="note">Dejará de estar disponible para nuevas operaciones de inventario.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false">Cancelar</button>
      <button mat-flat-button type="button" color="warn" [mat-dialog-close]="true">
        Desactivar
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    p {
      margin-top: 0;
      line-height: 1.55;
    }

    .note {
      margin-bottom: 0;
      color: #69758c;
      font-size: 0.9rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductDeactivateDialogComponent {
  protected readonly product = inject<Product>(MAT_DIALOG_DATA);
}
