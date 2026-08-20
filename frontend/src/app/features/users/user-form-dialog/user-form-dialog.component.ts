import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';

import { Role } from '../../../shared/models/role.model';
import { apiErrorMessage, apiFieldErrors } from '../../../shared/utils/api-error-message';
import {
  ROLE_LABELS,
  RoleOption,
  UserAccount,
  UserCreateRequest,
  UserUpdateRequest,
} from '../user.models';
import { UserService } from '../user.service';

export interface UserFormDialogData {
  readonly user: UserAccount | null;
  readonly roles: readonly RoleOption[];
}

@Component({
  selector: 'app-user-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './user-form-dialog.component.html',
  styleUrl: './user-form-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserFormDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<UserFormDialogComponent, UserAccount>);
  private readonly service = inject(UserService);
  protected readonly data = inject<UserFormDialogData>(MAT_DIALOG_DATA);
  protected readonly editing = this.data.user !== null;
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly form = new FormGroup({
    username: new FormControl(this.data.user?.username ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(50)],
    }),
    email: new FormControl(this.data.user?.email ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(120)],
    }),
    temporaryPassword: new FormControl('', {
      nonNullable: true,
      validators: this.data.user
        ? []
        : [Validators.required, Validators.minLength(12), Validators.maxLength(72)],
    }),
    roles: new FormControl<readonly Role[]>(this.data.user?.roles ?? [], {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  protected submit(): void {
    if (this.form.invalid || this.form.controls.roles.value.length === 0 || this.submitting()) {
      this.form.markAllAsTouched();
      if (this.form.controls.roles.value.length === 0) {
        this.form.controls.roles.setErrors({ required: true });
      }
      return;
    }
    const value = this.form.getRawValue();
    const operation = this.data.user
      ? this.service.update(this.data.user.id, {
          email: value.email.trim().toLowerCase(),
          roles: value.roles,
        } satisfies UserUpdateRequest)
      : this.service.create({
          username: value.username.trim().toLowerCase(),
          email: value.email.trim().toLowerCase(),
          temporaryPassword: value.temporaryPassword,
          roles: value.roles,
        } satisfies UserCreateRequest);

    this.submitting.set(true);
    this.errorMessage.set(null);
    operation.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (user) => this.dialogRef.close(user),
      error: (error: unknown) => {
        this.applyFieldErrors(apiFieldErrors(error));
        this.errorMessage.set(apiErrorMessage(error, 'No se pudo guardar el usuario.'));
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
