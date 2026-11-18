const sampleRate = 22050;
const keys = [220,247,262,294,330,349];
const tempos = [84,96,108,120,132,144];
const notes = [0,3,7,10,7,5,3,2];
export function synthClip(n) {
  if (!Number.isInteger(n) || n < 1 || n > 6) throw new RangeError('n must be 1..6');
  const length = sampleRate * 15;
  const noteDuration = 30 / tempos[n-1];
  const data = new Int16Array(length);
  for (let i = 0; i < length; i++) {
    const time = i / sampleRate;
    const note = Math.floor(time / noteDuration);
    const t = time - note * noteDuration;
    const phase = 2 * Math.PI * keys[n-1] * 2 ** (notes[note % notes.length] / 12) * t;
    const attackDecay = t < .01 ? t / .01 : Math.max(.6, 1 - .4 * (t - .01) / .08);
    const release = Math.min(1, (noteDuration - t) / .12, (15 - time) / .12);
    const wave = .8 * Math.sin(phase) + .2 * Math.sin(2 * phase);
    data[i] = Math.round(32767 * .4 * wave * attackDecay * release);
  }
  return data;
}
export function writeWav(samples, rate) {
  const buffer = Buffer.alloc(44 + samples.length * 2);
  buffer.write('RIFF',0);
  buffer.writeUInt32LE(buffer.length-8,4);
  buffer.write('WAVEfmt ',8);
  buffer.writeUInt32LE(16,16);
  buffer.writeUInt16LE(1,20);
  buffer.writeUInt16LE(1,22);
  buffer.writeUInt32LE(rate,24);
  buffer.writeUInt32LE(rate*2,28);
  buffer.writeUInt16LE(2,32);
  buffer.writeUInt16LE(16,34);
  buffer.write('data',36);
  buffer.writeUInt32LE(samples.length*2,40);
  for (let i = 0; i < samples.length; i++) buffer.writeInt16LE(samples[i], 44+i*2);
  return buffer;
}
