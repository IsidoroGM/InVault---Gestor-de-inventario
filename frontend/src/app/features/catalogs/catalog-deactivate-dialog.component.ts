import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

import { BaseCatalogItem, CatalogDefinition } from './catalog.models';

export interface CatalogDeactivateDialogData {
  readonly item: BaseCatalogItem;
  readonly definition: CatalogDefinition;
}

@Component({
  selector: 'app-catalog-deactivate-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Desactivar {{ data.definition.singular }}</h2>
    <mat-dialog-content>
      <p>
        ¿Quieres desactivar <strong>{{ data.item.name }}</strong
        >?
      </p>
      <p class="note">Dejará de estar disponible para nuevas asignaciones.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false">Cancelar</button>
      <button mat-flat-button type="button" [mat-dialog-close]="true">Desactivar</button>
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
export class CatalogDeactivateDialogComponent {
  protected readonly data = inject<CatalogDeactivateDialogData>(MAT_DIALOG_DATA);
}
