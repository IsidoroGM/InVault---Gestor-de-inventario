import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { finalize, forkJoin } from 'rxjs';

import { AuthSessionStore } from '../../core/auth/auth-session.store';
import { OFFICIAL_ROLES, Role } from '../../shared/models/role.model';
import { apiErrorMessage } from '../../shared/utils/api-error-message';
import { PasswordResetDialogComponent } from './password-reset-dialog/password-reset-dialog.component';
import { UserFormDialogComponent } from './user-form-dialog/user-form-dialog.component';
import { UserLifecycleDialogComponent } from './user-lifecycle-dialog.component';
import { ROLE_LABELS, RoleOption, UserAccount, UserActiveFilter } from './user.models';
import { UserService } from './user.service';

@Component({
  selector: 'app-users-page',
  imports: [
    DatePipe,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  templateUrl: './users-page.component.html',
  styleUrl: './users-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersPageComponent implements OnInit {
  private readonly dialog = inject(MatDialog);
  private readonly service = inject(UserService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly sessionStore = inject(AuthSessionStore);

  protected readonly users = signal<readonly UserAccount[]>([]);
  protected readonly roles = signal<readonly RoleOption[]>([]);
  protected readonly query = signal('');
  protected readonly activeFilter = signal<UserActiveFilter>('all');
  protected readonly roleFilter = signal<Role | 'all'>('all');
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly canManage = computed(() => this.sessionStore.hasAnyRole(['ADMIN']));
  protected readonly currentUserId = computed(() => this.sessionStore.session()?.userId ?? null);
  protected readonly activeCount = computed(
    () => this.users().filter((user) => user.active).length,
  );
  protected readonly passwordPendingCount = computed(
    () => this.users().filter((user) => user.mustChangePassword).length,
  );
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly officialRoles = OFFICIAL_ROLES;
  protected readonly displayedColumns = [
    'user',
    'roles',
    'status',
    'security',
    'lastLogin',
    'actions',
  ];
  protected readonly filteredUsers = computed(() => {
    const query = this.query().trim().toLocaleLowerCase('es');
    return this.users().filter(
      (user) =>
        (!query ||
          user.username.toLocaleLowerCase('es').includes(query) ||
          user.email.toLocaleLowerCase('es').includes(query)) &&
        (this.activeFilter() === 'all' || user.active === (this.activeFilter() === 'active')) &&
        (this.roleFilter() === 'all' || user.roles.includes(this.roleFilter() as Role)),
    );
  });

  ngOnInit(): void {
    this.reload();
  }
  protected updateQuery(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }
  protected setActiveFilter(value: UserActiveFilter): void {
    this.activeFilter.set(value);
  }
  protected setRoleFilter(value: Role | 'all'): void {
    this.roleFilter.set(value);
  }
  protected roleLabel(role: Role): string {
    return this.roleLabels[role];
  }

  protected openCreate(): void {
    this.openForm(null);
  }
  protected openEdit(user: UserAccount): void {
    this.openForm(user);
  }

  protected changeLifecycle(user: UserAccount): void {
    const action = user.active ? 'deactivate' : 'activate';
    this.dialog
      .open(UserLifecycleDialogComponent, {
        data: { user, action },
        width: '34rem',
        maxWidth: 'calc(100vw - 2rem)',
      })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (!confirmed) return;
        const operation =
          action === 'activate' ? this.service.activate(user.id) : this.service.deactivate(user.id);
        operation.subscribe({
          next: () => {
            this.snackBar.open(
              action === 'activate' ? 'Usuario activado.' : 'Usuario desactivado.',
              'Cerrar',
              { duration: 3500 },
            );
            this.reload();
          },
          error: (error: unknown) =>
            this.snackBar.open(
              apiErrorMessage(error, 'No se pudo cambiar el estado del usuario.'),
              'Cerrar',
              { duration: 6000 },
            ),
        });
      });
  }

  protected resetPassword(user: UserAccount): void {
    this.dialog
      .open(PasswordResetDialogComponent, {
        data: user,
        width: '38rem',
        maxWidth: 'calc(100vw - 2rem)',
        disableClose: true,
      })
      .afterClosed()
      .subscribe((reset: boolean | undefined) => {
        if (reset) {
          this.snackBar.open(
            'Contraseña temporal actualizada; las sesiones han sido revocadas.',
            'Cerrar',
            { duration: 5000 },
          );
          this.reload();
        }
      });
  }

  protected reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    forkJoin({ users: this.service.findAll(), roles: this.service.loadRoles() })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ users, roles }) => {
          this.users.set(users);
          this.roles.set(roles);
        },
        error: (error: unknown) => {
          this.users.set([]);
          this.errorMessage.set(apiErrorMessage(error, 'No se pudieron cargar los usuarios.'));
        },
      });
  }

  private openForm(user: UserAccount | null): void {
    this.dialog
      .open(UserFormDialogComponent, {
        data: { user, roles: this.roles() },
        width: '48rem',
        maxWidth: 'calc(100vw - 2rem)',
        disableClose: true,
      })
      .afterClosed()
      .subscribe((saved: UserAccount | undefined) => {
        if (saved) {
          this.snackBar.open(user ? 'Usuario actualizado.' : 'Usuario creado.', 'Cerrar', {
            duration: 3500,
          });
          this.reload();
        }
      });
  }
}
