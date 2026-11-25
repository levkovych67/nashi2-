import { useId, type ReactNode } from 'react';
import { cn } from '@/lib/utils';

/** A titled block of the artist page. */
export function ArtistSection({ title, className, children }: { title: string; className?: string; children: ReactNode }) {
  const headingId = useId();
  return (
    <section aria-labelledby={headingId} className={cn('mb-8', className)}>
      <h2 id={headingId} className="mb-4 text-2xl font-heading font-semibold">
        {title}
      </h2>
      {children}
    </section>
  );
}
