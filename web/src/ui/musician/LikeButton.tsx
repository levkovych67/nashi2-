import { useDesign } from '../../core/design/config'
import { useLike } from '../../core/likes/useLike'
import { resolveVariant } from '../variants'

export interface LikeSkinProps {
  count: number
  onTap(): void
}

/** Like control of a musician (decision like.button): every tap counts at once, requests are batched by the core. Reused by the map preview card. */
export function LikeButton({ musicianId, serverCount }: { musicianId: string; serverCount: number }) {
  const { count, tap } = useLike(musicianId, serverCount)
  const Skin = resolveVariant<LikeSkinProps>('like.button', useDesign())
  return Skin ? <Skin count={count} onTap={tap} /> : null
}
