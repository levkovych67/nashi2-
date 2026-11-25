import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { MapContainer, type MapFocus, type MapInsets } from '@/features/map/components/MapContainer';
import { MapFilterPanel } from '@/features/map/components/MapFilterPanel';
import { MapFilterSheet } from '@/features/map/components/MapFilterSheet';
import type { MapFilterUiProps } from '@/features/map/components/FilterControls';
import { PinPreviewDrawer } from '@/features/map/components/PinPreviewDrawer';
import { parseFilters, writeFilters } from '@/features/map/utils/filterParams';
import { EMPTY_FILTERS, facetCounts, filterPins, type PinFilters } from '@/features/map/utils/pinFilters';
import { sortByName } from '@/features/map/utils/pinLabels';
import { useMapPins } from '@/lib/api/hooks/useMapPins';
import { useRegions } from '@/lib/api/hooks/useLookup';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { toast } from '@/hooks/use-toast';
import { SEO } from '@/components/SEO';
import type { components } from '@/lib/api/generated/types';

type MapPinDTO = components['schemas']['MapPinDTO'];

const NO_PINS: MapPinDTO[] = [];
// What the floating filter UI covers: the 360 px panel with its 12 px margin and a 12 px gap, or the 58 px bottom bar
// with its 24 px margin (clear of the taskbar orb) and a 12 px gap.
const PANEL_INSETS: MapInsets = { left: 384 };
const BAR_INSETS: MapInsets = { bottom: 94 };

const hasPlace = (pin: MapPinDTO) => pin.latitude != null && pin.longitude != null;
const findArtist = (pins: MapPinDTO[], slug: string) =>
  pins.find((pin) => pin.type === 'ARTIST' && pin.slug === slug && hasPlace(pin));

function withoutArtist(params: URLSearchParams): URLSearchParams {
  const next = new URLSearchParams(params);
  next.delete('artist');
  return next;
}

const structuredData = {
  '@context': 'https://schema.org',
  '@type': 'WebApplication',
  name: 'НАШІ - Карта культурних подій України',
  description: 'Інтерактивна карта українських митців та культурних подій',
  applicationCategory: 'MultimediaApplication',
  operatingSystem: 'Web',
  offers: {
    '@type': 'Offer',
    price: '0',
    priceCurrency: 'UAH',
  },
};

export function MapPage() {
  // The filters live in the address (q, type, region, period, genre); `artist` opens the map on one artist.
  const [searchParams, setSearchParams] = useSearchParams();
  // Parsed again only when a filter param changes, not when `artist` comes and goes.
  const filtersQuery = writeFilters(new URLSearchParams(), parseFilters(searchParams)).toString();
  const filters = useMemo(() => parseFilters(new URLSearchParams(filtersQuery)), [filtersQuery]);
  const artistSlug = searchParams.get('artist');

  // All pins load once; filtering happens here, and the map stays mounted through every change.
  const { data, isLoading, error } = useMapPins();
  const { data: regions } = useRegions();
  const pins = data ?? NO_PINS;
  // A failed refetch keeps the pins already loaded; only a map without any pins is an error.
  const pinsFailed = !!error && !data;
  const visiblePins = useMemo(() => filterPins(pins, filters), [pins, filters]);
  const counts = useMemo(() => facetCounts(pins, filters), [pins, filters]);
  const results = useMemo(() => sortByName(visiblePins), [visiblePins]);

  const isDesktop = useMediaQuery('(min-width: 1024px)');
  const [selectedPin, setSelectedPin] = useState<MapPinDTO | null>(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [focus, setFocus] = useState<MapFocus | null>(null);
  const [searchKey, setSearchKey] = useState(0);

  const openPin = (pin: MapPinDTO) => {
    setSelectedPin(pin);
    setIsDrawerOpen(true);
  };
  const focusPin = (pin: MapPinDTO) => {
    if (hasPlace(pin)) {
      // A new key every time, so the same pin can be flown to again after the map was moved.
      setFocus((previous) => ({
        longitude: pin.longitude!,
        latitude: pin.latitude!,
        key: String(Number(previous?.key ?? 0) + 1),
      }));
    }
    openPin(pin);
  };

  // `?artist=<slug>`: once the pins are in, fly to that artist and open the card.
  const [handledArtist, setHandledArtist] = useState<string | null>(null);
  if (artistSlug !== handledArtist && (artistSlug === null || data)) {
    setHandledArtist(artistSlug);
    const pin = artistSlug && data ? findArtist(data, artistSlug) : undefined;
    if (pin) focusPin(pin);
  }

  // Not on the map: say so once and drop the slug from the address.
  const missingArtist = artistSlug && data && !findArtist(data, artistSlug) ? artistSlug : null;
  const toastedArtist = useRef<string | null>(null);
  useEffect(() => {
    if (!missingArtist) {
      toastedArtist.current = null;
      return;
    }
    if (toastedArtist.current === missingArtist) return;
    toastedArtist.current = missingArtist;
    toast({ title: 'Цього митця немає на карті' });
    setSearchParams(withoutArtist, { replace: true });
  }, [missingArtist, setSearchParams]);

  const closeDrawer = () => {
    setIsDrawerOpen(false);
    if (artistSlug) setSearchParams(withoutArtist, { replace: true });
  };

  const changeFilters = useCallback(
    (next: PinFilters) => setSearchParams((current) => writeFilters(current, next), { replace: true }),
    [setSearchParams],
  );
  const search = useCallback(
    (q: string) => setSearchParams((current) => writeFilters(current, { ...parseFilters(current), q }), { replace: true }),
    [setSearchParams],
  );
  const reset = () => {
    setSearchKey((key) => key + 1);
    changeFilters(EMPTY_FILTERS);
  };

  const filterUi: MapFilterUiProps = {
    filters,
    counts,
    regions: regions ?? [],
    results: data ? results : null,
    resultsFailed: pinsFailed,
    resultsKey: filtersQuery,
    searchKey,
    onChange: changeFilters,
    onSearch: search,
    onReset: reset,
    onSelect: focusPin,
  };

  return (
    <>
      <SEO
        title="Карта культурних подій"
        description="Інтерактивна карта українських митців, подій та культурних локацій. Відкрийте для себе культурну спадщину України."
        keywords="карта України, культурні події, українські митці, карта подій, культурна спадщина"
        structuredData={structuredData}
      />

      {/* The map fills the screen under the header (4rem and its 1px bottom line) without a page scroll. Below 1024 px
          it also ends at the taskbar, so the bar at the bottom of the map stays in view. */}
      <div className="relative h-[calc(100dvh_-_4rem_-_1px_-_var(--aero-taskbar-height)_-_env(safe-area-inset-bottom))] w-full lg:h-[calc(100dvh_-_4rem_-_1px)]">
        <MapContainer
          pins={visiblePins}
          onPinClick={openPin}
          regions={filters.regions}
          focus={focus}
          insets={isDesktop ? PANEL_INSETS : BAR_INSETS}
        />

        {isDesktop ? <MapFilterPanel {...filterUi} /> : <MapFilterSheet {...filterUi} />}

        {/* Loading state */}
        {isLoading && (
          <div className="absolute inset-0 z-20 flex items-center justify-center" role="status">
            <div className="aero-glass px-8 py-6 text-center">
              <div className="aero-spinner mx-auto mb-3" aria-hidden="true" />
              <p className="text-sm text-muted-foreground">Завантаження карти...</p>
            </div>
          </div>
        )}

        {/* Error state */}
        {pinsFailed && (
          <div className="absolute inset-0 z-20 flex items-center justify-center p-4" role="alert">
            <div className="aero-glass max-w-md p-6 text-center">
              <p className="mb-2 font-semibold text-[var(--aero-danger)]">Помилка завантаження даних</p>
              <p className="text-sm text-muted-foreground">Не вдалося завантажити карту. Спробуйте оновити сторінку.</p>
            </div>
          </div>
        )}

        <PinPreviewDrawer pin={selectedPin} isOpen={isDrawerOpen} onClose={closeDrawer} />
      </div>
    </>
  );
}
