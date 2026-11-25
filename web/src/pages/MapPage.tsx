import { AppShell } from '../ui/layout/AppShell'
import { BottomSheet } from '../ui/layout/BottomSheet'
import { BackToUkraine } from '../ui/map/BackToUkraine'
import { MapMarkers } from '../ui/map/MapMarkers'
import { MapOverlays } from '../ui/map/MapOverlays'
import { MapView } from '../ui/map/MapView'
import { MusicianPreviewPlaceholder } from '../ui/map/MusicianPreviewPlaceholder'
import { OriginNotice } from '../ui/map/OriginNotice'
import { PinStackList } from '../ui/map/PinStackList'
import { useMapController } from '../ui/map/useMapController'

export default function MapPage() {
  const map = useMapController()
  const { handlers, previewPin, stack } = map
  return (
    <AppShell>
      <div className="n-map-screen">
        <div className="n-map-stage">
          <MapView ref={map.mapRef} level={map.level} onViewport={handlers.onViewport} onBackgroundClick={handlers.closeSheets}>
            <MapOverlays fillOpacity={map.fillOpacity} />
            <MapMarkers
              items={map.items}
              names={map.names}
              placeName={map.placeName}
              numeralRegions={map.numeralRegions}
              playingId={map.playingId}
              selectedId={map.previewId}
              handlers={handlers}
            />
          </MapView>
          {map.showBack && <BackToUkraine onClick={handlers.backToUkraine} />}
          {previewPin ? (
            <BottomSheet testId="musician-preview" label={previewPin.name} onClose={handlers.closeSheets}>
              <MusicianPreviewPlaceholder name={previewPin.name} />
            </BottomSheet>
          ) : (
            stack && (
              <BottomSheet label={stack.title} onClose={handlers.closeSheets}>
                <PinStackList title={stack.title} pins={stack.pins} onSelect={handlers.onPin} />
              </BottomSheet>
            )
          )}
        </div>
        <OriginNotice />
      </div>
    </AppShell>
  )
}
