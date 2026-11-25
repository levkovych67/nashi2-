import type { WikiAttribution as Attribution } from '../utils/wikiAttribution';

const CC_BY_SA = 'https://creativecommons.org/licenses/by-sa/4.0/deed.uk';

/** Credits the Wikipedia article and the photo, as their licenses require. */
export function WikiAttribution({ attribution }: { attribution: Attribution }) {
  return (
    <div className="mb-8 space-y-1 text-sm text-muted-foreground">
      <p>
        Джерело: стаття{' '}
        <a href={attribution.articleUrl} target="_blank" rel="noopener noreferrer" className="aero-link">
          „{attribution.articleTitle}“
        </a>{' '}
        з Вікіпедії,{' '}
        <a href={CC_BY_SA} target="_blank" rel="noopener noreferrer license" className="aero-link">
          CC BY-SA 4.0
        </a>
      </p>
      {attribution.image && (
        <p>
          {attribution.image.url ? (
            <a href={attribution.image.url} target="_blank" rel="noopener noreferrer" className="aero-link">
              {attribution.image.text}
            </a>
          ) : (
            attribution.image.text
          )}
        </p>
      )}
    </div>
  );
}
