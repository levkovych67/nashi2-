import { parseArgs } from 'node:util';
import { mkdir, mkdtemp, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildGeo } from './lib/geo.mjs';

const defaultOut = fileURLToPath(new URL('../geo/', import.meta.url));
const { values } = parseArgs({ options: {
  src: { type: 'string', default: join(defaultOut, 'source', 'geoBoundaries-UKR-ADM1.geojson') },
  out: { type: 'string', default: defaultOut },
} });

try {
  const input = JSON.parse(await readFile(values.src, 'utf8'));
  const { regions, outline, meta } = await buildGeo(input);
  const files = [
    ['regions.geojson', regions], ['ukraine-outline.geojson', outline], ['regions-meta.json', meta],
  ];
  await mkdir(values.out, { recursive: true });
  const staging = await mkdtemp(join(values.out, '.build-geo-'));
  try {
    for (const [name, value] of files) {
      const indent = name === 'regions-meta.json' ? 2 : 0;
      await writeFile(join(staging, name), JSON.stringify(value, null, indent) + '\n');
    }
    for (const [name] of files) await rename(join(staging, name), join(values.out, name));
  } finally {
    await rm(staging, { recursive: true, force: true });
  }
  console.log(`Generated ${meta.length} regions and Ukraine's outline in ${values.out}`);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
