import { useState } from 'react';
import { CalendarDays } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { components } from '@/lib/api/generated/types';
import { pinSummary } from '../utils/pinLabels';

type MapPinDTO = components['schemas']['MapPinDTO'];

const PAGE = 50;

interface ResultsListProps {
  /** Already filtered and sorted by name. The parent re-keys the list when the filters change. */
  pins: MapPinDTO[];
  onSelect: (pin: MapPinDTO) => void;
}

/** Search results: 50 rows, then «Показати ще». A row click flies the map to the pin and opens its card. */
export function ResultsList({ pins, onSelect }: ResultsListProps) {
  const [limit, setLimit] = useState(PAGE);

  if (pins.length === 0) {
    return <p className="px-4 py-6 text-center text-sm text-muted-foreground">Нічого не знайдено</p>;
  }

  return (
    <div className="pb-3">
      <ul className="space-y-0.5 px-2">
        {pins.slice(0, limit).map((pin) => (
          <li key={pin.id ?? `${pin.slug}-${pin.latitude}-${pin.longitude}`}>
            <button
              type="button"
              onClick={() => onSelect(pin)}
              className="aero-hover-row flex min-h-[52px] w-full items-center gap-3 px-2 py-1.5 text-left"
            >
              <PinThumb pin={pin} />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-semibold text-[var(--aero-text)]">{pin.name}</span>
                <span className="block truncate text-xs text-[var(--aero-text-2)]">{pinSummary(pin)}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
      {pins.length > limit && (
        <div className="px-4 pt-2">
          <Button variant="outline" className="w-full" onClick={() => setLimit((current) => current + PAGE)}>
            Показати ще
          </Button>
        </div>
      )}
    </div>
  );
}

/** The same glass ring as the map pin: the photo or the first letter; events get the green calendar orb. */
function PinThumb({ pin }: { pin: MapPinDTO }) {
  if (pin.type === 'EVENT') {
    return (
      <span className="aero-orb aero-orb--grass flex h-9 w-9 flex-none items-center justify-center" aria-hidden="true">
        <CalendarDays className="relative z-[2] h-4 w-4" strokeWidth={2.3} />
      </span>
    );
  }
  return (
    <span className="aero-pin h-9 w-9 flex-none" aria-hidden="true">
      <span className="aero-pin__face relative text-sm font-semibold">
        <span>{pin.name?.charAt(0) || '?'}</span>
        {pin.avatarUrl && (
          <img
            src={pin.avatarUrl}
            alt=""
            loading="lazy"
            decoding="async"
            className="absolute inset-0"
            onError={(event) => {
              event.currentTarget.style.visibility = 'hidden';
            }}
          />
        )}
      </span>
    </span>
  );
}
