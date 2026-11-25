import { useDesign } from '../core/design/config'
import { AppShell } from '../ui/layout/AppShell'
import { BottomSheet } from '../ui/layout/BottomSheet'
import { Toast } from '../ui/layout/Toast'
import { resolveVariant } from '../ui/variants'
import { BackToUkraine } from '../ui/map/BackToUkraine'
import type { FilterSheet } from '../ui/map/FilterSheet'
import { MapControls } from '../ui/map/MapControls'
import { MapMarkers } from '../ui/map/MapMarkers'
import { MapOverlays } from '../ui/map/MapOverlays'
import { MapView } from '../ui/map/MapView'
import { MusicianPreviewPlaceholder } from '../ui/map/MusicianPreviewPlaceholder'
import { NearMeButton } from '../ui/map/NearMeButton'
import { OriginNotice } from '../ui/map/OriginNotice'
import { PinStackList } from '../ui/map/PinStackList'
import { ResultsCount } from '../ui/map/ResultsCount'
import { SoundToggle } from '../ui/map/SoundToggle'
import { useMapController } from '../ui/map/useMapController'

export default function MapPage() {
  const map = useMapController()
  const { handlers, previewPin, stack } = map
  const Filters = resolveVariant<Parameters<typeof FilterSheet>[0]>('filters.region', useDesign())
  const filterProps = {
    options: map.regionOptions ?? [],
    selected: map.selectedRegions,
    activeCount: map.activeFilterCount,
    onApply: handlers.applyRegions,
  }
  return (
    <AppShell>
      <div className="n-map-screen">
        <div className="n-map-body">
          {Filters && map.regionOptions && <Filters slot="panel" {...filterProps} />}
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
            <MapControls
              sound={<SoundToggle muted={map.muted} blocked={map.blocked} onToggle={handlers.toggleSound} />}
              count={map.resultsCount !== null && <ResultsCount count={map.resultsCount} />}
            >
              {Filters && map.regionOptions && <Filters slot="button" {...filterProps} />}
              <NearMeButton onClick={handlers.nearMe} />
              {map.showBack && <BackToUkraine onClick={handlers.backToUkraine} />}
            </MapControls>
            <Toast message={map.toast} />
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
        </div>
        <OriginNotice />
      </div>
    </AppShell>
  )
}
