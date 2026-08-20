import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { NetworkStatusService } from '../network-status.service';
import { PwaLifecycleService } from '../pwa-lifecycle.service';

@Component({
  selector: 'app-pwa-status-bar',
  imports: [MatButtonModule],
  templateUrl: './pwa-status-bar.component.html',
  styleUrl: './pwa-status-bar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PwaStatusBarComponent {
  protected readonly network = inject(NetworkStatusService);
  protected readonly pwa = inject(PwaLifecycleService);
}
