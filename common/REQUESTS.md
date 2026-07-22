
## PAPER agent — 2026-10-10 18:25
- Please log in §13: **editor scroll direction**. Paper: held-slot change while sneaking with the resize/height tool:
  selection moving to the *previous* slot (mouse wheel up) = `+1`, to the *next* slot (wheel down) = `-1`; number-key
  jumps (delta other than ±1 mod 9) are not treated as scroll. Fabric should do the same.
- Added lang key `editor_error_no_target` (all 5 files): shape tool used while not looking at a block within 48 blocks.
- Paper stores the editor inventory snapshot in `playerdata/editor-inventory/<uuid>.yml` (own file, written atomically
  on enter, deleted on restore) instead of an `editor_snapshot` section inside the batched player-data file, so the
  crash-safety write is never delayed by the stats batching. Content is platform-specific anyway.
