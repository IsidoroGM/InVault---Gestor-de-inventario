# Preserving units of measurement

The application does not implement stock-unit conversion. Reassigning a stocked
product from kilograms to litres, or relabelling a unit used by products, would
reinterpret quantities without a corresponding movement. These transitions are
therefore prevented by the backend and reflected in the editing forms.

## Rules

- A new, unused unit may change its code, name and symbol. Once assigned through
  product creation/update, its identity is permanently fixed. Description and
  active status remain editable. Selecting another unit for an unused product
  does not release the original unit's identity.
- A product without batches may select another active unit. Creating its first
  batch permanently fixes its unit, including a zero-quantity batch. Consuming or
  blocking every batch does not release this rule.
- A product may retain an inactive unit while editing other details. It may not
  newly select an inactive unit. The form displays an existing unit even if it is
  missing from the active unit catalogue.
- A different measurement needs a new unit/product. Values are never silently
  converted. Existing product names, descriptions and minimum-stock thresholds
  remain editable under their existing permissions and validation.

## Implementation and concurrency

Flyway **V6** adds `units.identity_locked` and `products.unit_locked`, both
non-null booleans. It fixes all units currently referenced by products and all
products with existing batches. Existing quantities, movement rows and audit
snapshots are unchanged. Flags are monotonic in the public application services.

Product creation locks the selected unit row, fixes its identity and saves the
product in the same transaction as its audit. Product update locks the product
row and any selected unit; it refuses a different unit once fixed. First-batch
creation also locks the product row and fixes its unit before saving the batch.
Unit editing locks the unit row before checking its identity. Deactivation uses
the same row locks so it cannot overwrite a concurrently established flag.

This serializes the two critical races: creating a product while someone renames
its unit, and creating a first batch while someone changes the product's unit.
Neither a failed audit nor an aborted creation leaves a flag partially applied;
the business record, flag and audit commit or roll back together.

The DTOs expose `identityLocked` and `unitLocked`. Forms disable protected fields,
explain the rule and retain original identifiers in their requests. The server
still validates stale forms and older clients; a field being enabled in a stale
page does not authorize the change. Violations return HTTP 400 with an explanation
shown in Spanish by the PWA.

For older Hibernate-managed development data, updates also check existing
references when a flag is still false. Production must use Flyway's backfill;
the normal `ddl-auto=update` development convenience is not a release migration.
Historical changes made before this feature are not reconstructed or converted.

## Rollout and verification

Back up the company, replace its backend with this version and apply V6 before
publishing the frontend. Do not keep an older backend accepting writes during
this transition: it does not enforce these rules. A rollback that permits writes
must preserve the rules or restore the pre-update company backup using the
documented isolated recovery procedure.

The real MySQL suite verifies:

- V5 → V6 backfill with a zero-stock legacy batch and production schema validation.
- Correction before use, changing an unused product's unit and permanent identity
  of a previously selected unit.
- Consumed stock, protected code/name/symbol, permitted descriptions/status and
  retaining an inactive unit while renaming a product.
- Both concurrent races with an observed InnoDB `LOCK WAIT`.
- Concurrent unit/product deactivation waits for creation and retains its newly
  committed identity flag while changing active status.
- Rollback of product/batch creation and their flags after a real audit failure.
- Persistence after application restart. Encrypted recovery verifies both flags.

These tests operate on separate randomly named databases and restricted users;
they do not modify an installed or production company database.
