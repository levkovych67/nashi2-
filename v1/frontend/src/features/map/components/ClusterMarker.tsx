import { clusterLabel, formatClusterCount } from '../utils/clusterPins';

interface ClusterMarkerProps {
  count: number;
  onClick: () => void;
}

/** A glossy aqua bubble standing for several nearby pins; a click zooms in until it splits. */
export function ClusterMarker({ count, onClick }: ClusterMarkerProps) {
  const size =
    count < 10 ? 'h-10 w-10 text-sm' : count < 100 ? 'h-12 w-12 text-sm' : count < 1000 ? 'h-14 w-14 text-base' : 'h-16 w-16 text-sm';

  return (
    <button
      type="button"
      onClick={(event) => {
        event.stopPropagation();
        onClick();
      }}
      aria-label={clusterLabel(count)}
      className={`aero-orb aero-cluster flex items-center justify-center font-bold leading-none ${size}`}
    >
      <span className="relative z-[2]">{formatClusterCount(count)}</span>
    </button>
  );
}
