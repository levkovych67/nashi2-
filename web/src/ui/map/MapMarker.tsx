import { useEffect, useRef, type ReactNode } from 'react'
import { Marker, type MarkerInstance } from 'react-map-gl/maplibre'

const RAISED = { zIndex: 1 }

/** Positions `children` on the map. The children are the control, so MapLibre's generic "Map marker" button role goes. */
export function MapMarker({ lon, lat, raised = false, children }: { lon: number; lat: number; raised?: boolean; children: ReactNode }) {
  const ref = useRef<MarkerInstance>(null)
  useEffect(() => {
    // runs after Marker's own effect has added it to the map (and set the attributes)
    const el = ref.current?.getElement()
    el?.removeAttribute('role')
    el?.removeAttribute('aria-label')
  }, [])
  return (
    <Marker ref={ref} longitude={lon} latitude={lat} anchor="center" style={raised ? RAISED : undefined}>
      {children}
    </Marker>
  )
}
