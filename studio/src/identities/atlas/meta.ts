import type { IdentityMeta } from '../types'

export default {
  id: 'atlas',
  title: 'Редакційний атлас',
  concept:
    'Тонкі лінії та друкарський синій відсилають до географічних атласів. Заголовки Lora надають біографіям вигляду редакційного портрета. На світлому паперовому тлі підписи зручно читати поруч із дрібними позначками.',
  keywords: ['Папір', 'гравюра', 'координати'],
  voice: {
    tone: 'Стриманий, дослідницький',
    examples: ['Відкрийте місто та прочитайте історію музиканта.'],
  },
  moodboardAlt: 'Топографічні ізолінії й сітка на папері',
  moodboardCredits: ['Авторська графічна композиція дизайн-студії «Наші».'],
  fonts: [
    {
      family: 'Lora',
      role: 'display',
      license: 'OFL-1.1',
      source: 'https://github.com/google/fonts/tree/main/ofl/lora',
      files: ['lora.woff2'],
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
