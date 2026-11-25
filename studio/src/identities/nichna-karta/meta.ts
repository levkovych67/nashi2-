import type { IdentityMeta } from '../types'

export default {
  id: 'nichna-karta',
  title: 'Нічна карта звуку',
  concept:
    'Темна підкладка підкреслює бурштинові сигнали. Хвиля у знаку повторює рух звукового індикатора. Спокійні підписи залишають більше уваги прослуховуванню.',
  keywords: ['Ефір', 'частота', 'бурштин'],
  voice: {
    tone: 'Спокійний і лаконічний',
    examples: ['Послухайте, як звучить це місто.'],
  },
  moodboardAlt: 'Бурштинова звукова хвиля на координатній сітці',
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
