import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { authErrorMessage } from '../../../core/error-handling/auth-error-message';

const matchingPasswords: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.get('newPassword')?.value === control.get('confirmation')?.value
    ? null
    : { passwordMismatch: true };

@Component({
  selector: 'app-change-password-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './change-password-page.component.html',
  styleUrl: '../auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChangePasswordPageComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly session = inject(AuthSessionStore).session;
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.formBuilder.nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(72)]],
      confirmation: ['', Validators.required],
    },
    { validators: matchingPasswords },
  );

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const { currentPassword, newPassword } = this.form.getRawValue();
    this.errorMessage.set(null);
    this.submitting.set(true);
    this.auth
      .changePassword({ currentPassword, newPassword })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () =>
          void this.router.navigate(['/login'], {
            queryParams: { passwordChanged: '1' },
            replaceUrl: true,
          }),
        error: (error: unknown) =>
          this.errorMessage.set(authErrorMessage(error, 'No se pudo actualizar la contraseña.')),
      });
  }

  protected logout(): void {
    this.auth.logout().subscribe(() => void this.router.navigate(['/login'], { replaceUrl: true }));
  }
}
