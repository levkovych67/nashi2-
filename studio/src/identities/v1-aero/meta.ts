import type { IdentityMeta } from '../types'

export default {
  id: 'v1-aero',
  title: 'V1 Aero',
  concept:
    'Блакитне скло й круглий знак відсилають до першої версії. Зелена суша відділяється від води навіть у малому масштабі. Заокруглення та світлі відблиски об’єднують елементи карти.',
  keywords: ['Скло', 'відблиск', 'рельєф'],
  voice: {
    tone: 'Привітний і розмовний',
    examples: ['Знайдіть музику поруч.'],
  },
  moodboardAlt: 'Прозорі блакитні диски та хвилі рельєфу',
  moodboardCredits: ['Авторська графічна композиція дизайн-студії «Наші».'],
  fonts: [
    {
      family: 'Source Sans 3',
      role: 'display',
      license: 'OFL-1.1',
      source: 'https://github.com/google/fonts/tree/main/ofl/sourcesans3',
      files: ['source-sans-3.woff2'],
    },
    {
      family: 'Source Sans 3',
      role: 'body',
      license: 'OFL-1.1',
      source: 'https://github.com/google/fonts/tree/main/ofl/sourcesans3',
      files: ['source-sans-3.woff2'],
    },
  ],
} satisfies IdentityMeta
