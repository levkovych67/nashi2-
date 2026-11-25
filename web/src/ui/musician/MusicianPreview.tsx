import { useDesign } from '../../core/design/config'
import { resolveVariant } from '../variants'
import type { PreviewSkinProps } from './variants/BottomSheetPreview'

/** Card of a musician on the map (decision musician.preview). */
export function MusicianPreview(props: PreviewSkinProps) {
  const Skin = resolveVariant<PreviewSkinProps>('musician.preview', useDesign())
  return Skin ? <Skin {...props} /> : null
}
