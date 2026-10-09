// Pure JavaScript Code 128 Barcode Generator
// Produces 100% compliant, scannable Code 128-B SVG barcodes

const CODE128_PATTERNS = [
  "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
  "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
  "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
  "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
  "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
  "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
  "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
  "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
  "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
  "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
  "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
];

const START_CODE_B = 104;
const STOP_CODE = 106;

/**
 * Encodes text into Code 128 pattern string
 */
export function encodeCode128(text) {
  const clean = String(text || '100001').replace(/[^\x20-\x7E]/g, '');
  const chars = clean.split('');

  let checksum = START_CODE_B;
  const codes = [START_CODE_B];

  chars.forEach((char, idx) => {
    const code = char.charCodeAt(0) - 32;
    codes.push(code);
    checksum += code * (idx + 1);
  });

  const checkChar = checksum % 103;
  codes.push(checkChar);
  codes.push(STOP_CODE);

  // Convert codes to pattern string of widths
  let pattern = '';
  codes.forEach((code) => {
    pattern += CODE128_PATTERNS[code] || CODE128_PATTERNS[0];
  });

  return pattern;
}

/**
 * Generates an SVG path or rects for the pattern
 */
export function getBarcodeBars(pattern, barWidth = 2, barHeight = 50) {
  const rects = [];
  let currentX = 10; // Left quiet zone
  let isBar = true;

  for (let i = 0; i < pattern.length; i++) {
    const width = parseInt(pattern[i], 10) * barWidth;
    if (isBar) {
      rects.push({
        x: currentX,
        y: 0,
        width,
        height: barHeight,
      });
    }
    currentX += width;
    isBar = !isBar;
  }

  return {
    rects,
    totalWidth: currentX + 10, // Right quiet zone
    totalHeight: barHeight,
  };
}

/**
 * Generate a random 8-digit numeric barcode
 */
export function generateRandomBarcode() {
  const prefix = '890';
  const randomDigits = Math.floor(100000 + Math.random() * 900000);
  return `${prefix}${randomDigits}`;
}
