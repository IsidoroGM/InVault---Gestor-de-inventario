import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';

import { apiErrorMessage } from '../../../shared/utils/api-error-message';
import { UserAccount } from '../user.models';
import { UserService } from '../user.service';

@Component({
  selector: 'app-password-reset-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './password-reset-dialog.component.html',
  styleUrl: './password-reset-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordResetDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<PasswordResetDialogComponent, boolean>);
  private readonly service = inject(UserService);
  protected readonly user = inject<UserAccount>(MAT_DIALOG_DATA);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = new FormGroup(
    {
      temporaryPassword: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(12), Validators.maxLength(72)],
      }),
      confirmation: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    },
    { validators: matchingPasswords },
  );

  protected submit(): void {
    if (this.form.invalid || this.passwordMismatch() || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.service
      .resetPassword(this.user.id, {
        temporaryPassword: this.form.controls.temporaryPassword.value,
      })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => this.dialogRef.close(true),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No se pudo restablecer la contraseña.')),
      });
  }

  protected passwordMismatch(): boolean {
    return this.form.controls.confirmation.touched && this.form.hasError('passwordMismatch');
  }
}

function matchingPasswords(control: AbstractControl): ValidationErrors | null {
  return control.get('temporaryPassword')?.value === control.get('confirmation')?.value
    ? null
    : { passwordMismatch: true };
}
