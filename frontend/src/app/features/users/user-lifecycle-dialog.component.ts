import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

import { UserAccount } from './user.models';

export interface UserLifecycleDialogData {
  readonly user: UserAccount;
  readonly action: 'activate' | 'deactivate';
}

@Component({
  selector: 'app-user-lifecycle-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>
      {{ data.action === 'activate' ? 'Activar cuenta' : 'Desactivar cuenta' }}
    </h2>
    <mat-dialog-content>
      <p>
        {{
          data.action === 'activate'
            ? 'La cuenta recuperará el acceso a InVault.'
            : 'La cuenta perderá el acceso y sus sesiones serán revocadas.'
        }}
      </p>
      <strong>{{ data.user.username }} · {{ data.user.email }}</strong>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Cancelar</button>
      <button mat-flat-button [mat-dialog-close]="true">
        {{ data.action === 'activate' ? 'Activar' : 'Desactivar' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    p {
      max-width: 32rem;
      color: #5f6c83;
      line-height: 1.55;
    }
    strong {
      color: #25334e;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserLifecycleDialogComponent {
  protected readonly data = inject<UserLifecycleDialogData>(MAT_DIALOG_DATA);
}
