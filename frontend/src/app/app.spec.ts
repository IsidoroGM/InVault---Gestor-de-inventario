import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SwUpdate } from '@angular/service-worker';
import { EMPTY } from 'rxjs';

import { App } from './app';

describe('App', () => {
  it('creates the application root with a router outlet', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        {
          provide: SwUpdate,
          useValue: { isEnabled: false, versionUpdates: EMPTY, unrecoverable: EMPTY },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(fixture.componentInstance).toBeTruthy();
    expect(fixture.nativeElement.querySelector('router-outlet')).toBeTruthy();
  });
});
