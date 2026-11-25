import type { ReactNode } from 'react'

/**
 * Full-size layer over the map for its controls. It lets pointer events through except on the controls themselves,
 * and is the positioned parent of the filter panel and sheet.
 */
export function MapControls({ sound, count, children }: { sound: ReactNode; count: ReactNode; children: ReactNode }) {
  return (
    <div className="n-map-ui">
      <div className="n-map-ui__row">{children}</div>
      <div className="n-map-ui__sound">{sound}</div>
      <div className="n-map-ui__count">{count}</div>
    </div>
  )
}
