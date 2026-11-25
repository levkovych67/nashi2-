const fields='name kind originPlaceId birthPlaceId summary genres links youtubeChannelUrl wikipediaUrl contact images'.split(' ')
export const REQUIRED_COPY_KEYS=[
  ...'app.name app.tagline map.originNotice map.soundOn map.soundOff map.soundBlockedHint map.nearMe map.nearMeDenied map.resultsCount map.backToUkraine cluster.ariaLabel pin.ariaLabel pin.noAudio preview.openProfile preview.play preview.stop musician.originLabel.BIRTH musician.originLabel.FORMATION musician.originLabel.ORIGIN musician.precisionNote.CITY musician.likeAria musician.likesCount musician.listenOn musician.youtubeChannel musician.textSource musician.photoCredit musician.noAudio filters.title filters.region filters.reset filters.apply list.empty submit.title submit.intro submit.consent submit.send submit.sending submit.success errors.network errors.notFound errors.rateLimited errors.generic'.split(' '),
  ...fields.flatMap(f=>['submit.field.'+f+'.label','submit.field.'+f+'.hint']),
  ...'VALIDATION_FAILED PAYLOAD_TOO_LARGE UNSUPPORTED_MEDIA_TYPE RATE_LIMITED INTERNAL UNAVAILABLE'.split(' ').map(k=>'submit.error.'+k)
]
