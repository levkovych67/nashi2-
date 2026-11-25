import { useDesign } from '../../core/design/config'
import { resolveVariant } from '../variants'

export interface AudioSkinProps {
  musicianId: string
  hasAudio: boolean
}

/** Listening control of a musician (decision audio.player). Reused by the map preview card. */
export function MusicianAudio(props: AudioSkinProps) {
  const Skin = resolveVariant<AudioSkinProps>('audio.player', useDesign())
  return Skin ? <Skin {...props} /> : null
}
