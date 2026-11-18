const letters = {
  а:'a', б:'b', в:'v', г:'h', ґ:'g', д:'d', е:'e', ж:'zh', з:'z', и:'y',
  і:'i', к:'k', л:'l', м:'m', н:'n', о:'o', п:'p', р:'r', с:'s', т:'t',
  у:'u', ф:'f', х:'kh', ц:'ts', ч:'ch', ш:'sh', щ:'shch', ь:''
};
const positionals = { є:['ye','ie'], ї:['yi','i'], й:['y','i'], ю:['yu','iu'], я:['ya','ia'] };
export function transliterate(text) {
  const chars = [...text.toLowerCase().normalize('NFC')];
  let value = '';
  let initial = true;
  for (let i = 0; i < chars.length; i++) {
    const char = chars[i];
    if (/['’ʼ`"«»“”\u0301]/u.test(char)) continue;
    if (char === 'з' && chars[i + 1] === 'г') {
      value += 'zgh';
      i++;
    } else {
      value += positionals[char]?.[initial ? 0 : 1] ?? letters[char] ?? char;
    }
    initial = !/[\p{L}\p{N}]/u.test(char);
  }
  return value;
}
export function slugify(name, taken = new Set()) {
  const base = transliterate(name).normalize('NFKD').replace(/\p{M}/gu, '')
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'muzykant';
  let slug = base;
  for (let suffix = 2; taken.has(slug); suffix++) slug = base + '-' + suffix;
  taken.add(slug);
  return slug;
}
