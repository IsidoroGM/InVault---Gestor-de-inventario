import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { authErrorMessage } from '../../../core/error-handling/auth-error-message';

@Component({
  selector: 'app-login-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './login-page.component.html',
  styleUrl: '../auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPageComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly sessionMessage = this.route.snapshot.queryParamMap.has('sessionExpired')
    ? 'Tu sesión ha caducado. Vuelve a identificarte.'
    : this.route.snapshot.queryParamMap.has('passwordChanged')
      ? 'Contraseña actualizada. Inicia sesión con la nueva contraseña.'
      : null;

  protected readonly form = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(50)]],
    password: ['', [Validators.required, Validators.maxLength(200)]],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);
    this.auth
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (session) => {
          const destination = session.mustChangePassword
            ? '/change-password'
            : this.safeReturnUrl();
          void this.router.navigateByUrl(destination, { replaceUrl: true });
        },
        error: (error: unknown) =>
          this.errorMessage.set(authErrorMessage(error, 'No se pudo iniciar sesión.')),
      });
  }

  private safeReturnUrl(): string {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    return returnUrl?.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/dashboard';
  }
}
