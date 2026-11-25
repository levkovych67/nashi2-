export function cleanBiography(text = '') {
  return (text ?? '').replace(/\(\d{8}\)/g, '').replace(/\s+/gu,' ').trim();
}
export function firstSentence(text) {
  let depth = 0;
  for (let i = 0; i < text.length; i++) {
    if ('(«'.includes(text[i])) depth++;
    if (')»'.includes(text[i])) depth = Math.max(0, depth - 1);
    if (!depth && /[.!?…]/u.test(text[i]) && text[i+1] === ' ' && /[\p{Lu}«]/u.test(text[i+2] || ' ')) {
      return text.slice(0, i+1);
    }
  }
  return text;
}
export function summarize(text, max) {
  if (text.length <= max) return text;
  let rest = text;
  let result = '';
  while (rest) {
    const sentence = firstSentence(rest);
    const next = result ? result + ' ' + sentence : sentence;
    if (next.length > max) break;
    result = next;
    rest = rest.slice(sentence.length).trimStart();
  }
  if (result) return result;
  const prefix = text.slice(0, max - 1);
  const space = text[max-1] === ' ' ? prefix.length : prefix.lastIndexOf(' ');
  return (space > 0 ? prefix.slice(0, space) : prefix).trimEnd() + '…';
}
export function taglineFrom(sentence) {
  const dash = sentence.indexOf(' — ');
  if (dash < 0) return null;
  return sentence.slice(dash + 3).replace(/[.!?…]+$/u, '').trim() || null;
}
