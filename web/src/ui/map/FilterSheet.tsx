import { useCallback, useState } from 'react'
import { BottomSheet } from '../layout/BottomSheet'
import { useCopy } from '../../core/i18n/copy'
import { RegionFilter, type RegionOption } from './RegionFilter'
import { useDesktop } from './useDesktop'

/**
 * filters.region = SHEET_LIST. From 1024 px a left panel (`slot="panel"`, a flex sibling that narrows the map);
 * below, a «Фільтри» button (`slot="button"`, with the number of active conditions) that opens the same list in a
 * bottom sheet positioned against the map overlay it is rendered in. The other slot renders nothing.
 */
export function FilterSheet({ slot, options, selected, activeCount, onApply }: {
  slot: 'panel' | 'button'
  options: RegionOption[]
  selected: string[]
  activeCount: number
  onApply(regions: string[]): void
}) {
  const t = useCopy()
  const desktop = useDesktop()
  const [open, setOpen] = useState(false)
  const close = useCallback(() => setOpen(false), [])
  const filter = (
    <RegionFilter
      key={selected.join(',')}
      options={options}
      selected={selected}
      onApply={(regions) => {
        onApply(regions)
        close()
      }}
      onReset={() => {
        onApply([])
        close()
      }}
    />
  )

  if (desktop !== (slot === 'panel')) return null
  if (desktop) {
    return (
      <aside className="n-filter-panel" aria-label={t('filters.title')}>
        <h2 className="n-sheet__title">{t('filters.title')}</h2>
        {filter}
      </aside>
    )
  }
  return (
    <>
      <button type="button" className="n-map-button" data-testid="filters-open" aria-expanded={open} onClick={() => setOpen(true)}>
        {t('filters.title')}
        {activeCount > 0 && ` (${activeCount})`}
      </button>
      {open && (
        <BottomSheet label={t('filters.title')} testId="filters-sheet" onClose={close}>
          <h2 className="n-sheet__title">{t('filters.title')}</h2>
          {filter}
        </BottomSheet>
      )}
    </>
  )
}
