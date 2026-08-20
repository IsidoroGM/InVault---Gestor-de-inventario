import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, Data, RouterLink } from '@angular/router';

@Component({
  selector: 'app-feature-placeholder',
  imports: [MatButtonModule, MatCardModule, RouterLink],
  templateUrl: './feature-placeholder.component.html',
  styleUrl: './feature-placeholder.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FeaturePlaceholderComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly routeData = toSignal(this.route.data, { initialValue: {} as Data });

  protected readonly eyebrow = computed(() => this.value('eyebrow', 'Próximamente'));
  protected readonly title = computed(() => this.value('title', 'Módulo en preparación'));
  protected readonly description = computed(() =>
    this.value('description', 'Esta ruta queda preparada para una subfase posterior.'),
  );

  private value(key: string, fallback: string): string {
    const value: unknown = this.routeData()[key];
    return typeof value === 'string' ? value : fallback;
  }
}
