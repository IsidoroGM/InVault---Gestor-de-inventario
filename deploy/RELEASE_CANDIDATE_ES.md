# Entrega candidata de InVault 0.1.0-rc.2

Fecha de preparación: 25 de octubre de 2026.
Etiqueta de código: `v0.1.0-rc.2`.
Versión del backend y frontend: `0.1.0-rc.2`.

## Procedencia y cierre de la candidata

La candidata rc.1 cerró la fase 1 con el PR #15 y conserva su etiqueta histórica.
La rc.2 incorpora las pruebas, el análisis de seguridad y las correcciones del PR #16.
El cierre de la nueva candidata consiste en revisar
los cambios y comentarios, corregir la documentación, alinear las versiones,
comprobar los cuatro workflows, integrar en main, comprobar de nuevo el commit integrado y crear una
etiqueta Git anotada sobre ese commit. La etiqueta permite volver al mismo código.

Las [notas de versión](../CHANGELOG.md) enumeran funcionalidad y compatibilidad.
El [resumen técnico](RESUMEN_TECNICO_ES.md) explica los mecanismos paso a paso.
Los resultados de GitHub Actions permiten comprobar las pruebas del commit elegido.

La entrega fija las fuentes. Las imágenes de ensayo de CI son temporales; para
instalar se construyen y conservan imágenes identificadas con su descriptor. El
identificador de las imágenes y copias es el SHA completo del commit, mientras que
`0.1.0-rc.2` es el nombre legible de esta candidata.

## Alcance revisado

- Aislamiento de empresa: proyecto, base, volumen, credenciales, origen y JWT.
- Movimientos: clave por usuario, transacción que une saldo/auditoría/respuesta y
  reintentos explícitos conservando la petición original en la PWA.
- Acceso: reserva persistente de intentos, espera HTTP 429 y limpieza de cabeceras
  de origen antes del backend.
- Unidades: marcas permanentes y bloqueos para impedir que un saldo cambie de medida.
- Operación: copia age, validación de empresa antes de recuperar, comprobación de
  copias, readiness de MySQL, diagnóstico y revisión de la imagen desplegada.
- Construcción: snapshot del commit y exclusión de configuración local del JAR.

La revisión técnica del autor y los ensayos automatizados se complementan con la
validación funcional y de seguridad de las fases siguientes.

## Comprobaciones antes de etiquetar

1. El árbol Git está limpio; no se incluyen archivos de empresa ni claves.
2. Backend, frontend y lockfile declaran la misma versión de aplicación.
3. PR #16 sin conversaciones pendientes y con los cuatro workflows correctos.
4. Integración normal del PR, conservando su historial, sin saltar controles.
5. Los workflows del commit integrado en main terminan correctamente.
6. La etiqueta anotada apunta a ese commit y se comprueba también en origin.

Batería esperada: 92 pruebas Java habituales y 24 MySQL; 84 Angular; formato,
auditoría npm y build; 34 casos de diagnóstico, 9 de versión y 16 de monitor de
copias; construcción/inspección del JAR y ensayos reales de copias/proxies/readiness.
Además: 24 recorridos de navegador, carga de 600 solicitudes, CodeQL Java/JavaScript,
análisis de las imágenes y 12 controles de políticas. Ver [QUALITY_VERIFICATION_ES.md](QUALITY_VERIFICATION_ES.md).

## Obtener el código de la candidata

Desde un clon limpio del repositorio, una vez publicada la etiqueta:

```powershell
git fetch origin --tags
git switch --detach v0.1.0-rc.2
git rev-parse HEAD
git status --short
$release = ./deploy/build-release.ps1
$release
```

La selección queda en un commit fijo. `build-release.ps1` requiere Git, Docker y
PowerShell 7.3+; rechaza cambios pendientes, construye desde `git archive` y devuelve
las imágenes, el descriptor y el overlay. Conservar los artefactos de esa ejecución.

La preparación de cada empresa sigue [COMPANY_DEPLOYMENTS.md](COMPANY_DEPLOYMENTS.md).
La instalación utiliza [RELEASES.md](RELEASES.md) y `up --no-build --wait` con el
mismo proyecto, archivo privado y overlay en todas las actualizaciones.

Antes de actualizar datos existentes, crear una copia con [BACKUPS.md](BACKUPS.md).
Primero debe estar disponible el backend con Flyway V1–V6; después se utiliza la PWA
actualizada. No utilizar un backend antiguo para escribir sobre esa transición.
Una recuperación se ensaya en un entorno aislado; no se borran volúmenes operativos.

## Fases siguientes

| Fase | Resultado pendiente |
| --- | --- |
| 2. Calidad ampliada | Controles implementados; el cierre de rc.2 exige todos los resultados correctos sobre el commit integrado. |
| 3. Entorno real | Servidor, dominio, HTTPS y dos empresas de prueba con almacenamiento independiente. |
| 4. Aceptación | Completar [PWA_ACCEPTANCE.md](PWA_ACCEPTANCE.md) en Windows, Android e iPad, con varios roles y dispositivos. |
| 5. Operación | Activar copias, réplica externa, alertas y métricas; restaurar en el entorno elegido. |
| 6. Piloto | Uso con una empresa, correcciones de incidencias y preparación de la entrega definitiva. |

Compartir un solo host/IP entre empresas requiere adaptar el proxy HTTPS; los
proyectos independientes por sí solos no resuelven la colisión de puertos 80/443.
Cada empresa conserva su base en el servidor, compartida por sus dispositivos.
Las operaciones de inventario necesitan conexión.
