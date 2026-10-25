import { Signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { AuthSession } from '../../../core/auth/auth.models';
import { LoginPageComponent } from './login-page.component';

interface TestableLogin {
  form: FormGroup;
  submitting: Signal<boolean>;
  retryAfterSeconds: Signal<number>;
  errorMessage: Signal<string | null>;
  submit(): void;
}

describe('LoginPageComponent throttling', () => {
  const auth = { login: vi.fn() };
  const router = { navigateByUrl: vi.fn() };

  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  async function create() {
    await TestBed.configureTestingModule({
      imports: [LoginPageComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(LoginPageComponent);
    const component = fixture.componentInstance as unknown as TestableLogin;
    component.form.setValue({ username: 'operator', password: 'correct-password' });
    return { fixture, component };
  }

  it('prevents double submission while authentication is unresolved', async () => {
    auth.login.mockReturnValue(new Subject<AuthSession>());
    const { fixture, component } = await create();
    component.submit();
    component.submit();
    expect(auth.login).toHaveBeenCalledTimes(1);
    expect(component.submitting()).toBe(true);
    fixture.destroy();
  });

  it('shows the countdown, blocks retries and releases the button after elapsed wall time', async () => {
    const response = new Subject<AuthSession>();
    auth.login.mockReturnValue(response);
    const { fixture, component } = await create();
    let tick: (() => void) | undefined;
    const interval = vi.spyOn(globalThis, 'setInterval').mockImplementation((callback) => {
      tick = callback as () => void;
      return 123 as unknown as ReturnType<typeof setInterval>;
    });
    const clear = vi.spyOn(globalThis, 'clearInterval');
    const now = vi.spyOn(Date, 'now').mockReturnValue(1000);
    component.submit();
    response.error({ status: 429, message: 'Too many attempts.', retryAfterSeconds: 42 });
    fixture.detectChanges();
    expect(component.retryAfterSeconds()).toBe(42);
    expect(fixture.nativeElement.textContent).toContain('42 segundos');
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    component.submit();
    expect(auth.login).toHaveBeenCalledTimes(1);
    expect(interval).toHaveBeenCalledTimes(1);

    // A background tab may miss timer ticks; use elapsed time rather than counting callbacks.
    now.mockReturnValue(44000);
    tick?.();
    fixture.detectChanges();
    expect(component.retryAfterSeconds()).toBe(0);
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(false);
    expect(clear).toHaveBeenCalledWith(123);
    expect(auth.login).toHaveBeenCalledTimes(1); // Reaching zero never sends credentials automatically.
    fixture.destroy();
  });

  it('clears its timer when the page is destroyed and never stores credentials', async () => {
    const response = new Subject<AuthSession>();
    auth.login.mockReturnValue(response);
    const { fixture, component } = await create();
    vi.spyOn(globalThis, 'setInterval').mockReturnValue(
      321 as unknown as ReturnType<typeof setInterval>,
    );
    const clear = vi.spyOn(globalThis, 'clearInterval');
    const saveSession = vi.spyOn(Storage.prototype, 'setItem');
    component.submit();
    response.error({ status: 429, message: 'Too many attempts.', retryAfterSeconds: 10 });
    fixture.destroy();
    expect(clear).toHaveBeenCalledWith(321);
    expect(saveSession).not.toHaveBeenCalled();
  });
});
