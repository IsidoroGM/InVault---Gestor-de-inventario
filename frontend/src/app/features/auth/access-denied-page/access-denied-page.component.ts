import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-access-denied-page',
  imports: [MatButtonModule, RouterLink],
  template: `
    <section class="denied" aria-labelledby="denied-title">
      <span class="code" aria-hidden="true">403</span>
      <p>Acceso restringido</p>
      <h1 id="denied-title">Tu rol no permite abrir esta sección.</h1>
      <a mat-flat-button routerLink="/dashboard">Volver al dashboard</a>
    </section>
  `,
  styles: `
    :host {
      display: grid;
      min-height: calc(100dvh - 9rem);
      place-items: center;
    }
    .denied {
      max-width: 38rem;
      padding: 3rem;
      text-align: center;
    }
    .code {
      display: inline-block;
      margin-bottom: 1rem;
      color: #315cc8;
      font-size: 4rem;
      font-weight: 900;
      letter-spacing: -0.07em;
    }
    p {
      margin: 0 0 0.6rem;
      color: #315cc8;
      font-size: 0.72rem;
      font-weight: 850;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }
    h1 {
      margin: 0 0 1.6rem;
      color: #17223d;
      font-size: clamp(2rem, 5vw, 3.4rem);
      line-height: 1.06;
      letter-spacing: -0.045em;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccessDeniedPageComponent {}
