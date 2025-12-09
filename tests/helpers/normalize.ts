/**
 * Output normalization for snapshot comparison.
 *
 * Normalizations applied:
 * - Convert CRLF → LF (Windows line endings)
 * - Trim trailing whitespace from each line
 * - Ensure single trailing newline
 *
 * NOT normalized (preserve exactly):
 * - Field separators (tabs)
 * - Field values
 * - Column ordering
 * - Number of fields per row
 */
export function normalizeContent(content: string): string {
  return content
    .replace(/\r\n/g, '\n')           // CRLF → LF
    .split('\n')
    .map(line => line.trimEnd())       // Trailing whitespace per line
    .join('\n')
    .replace(/\n*$/, '\n');            // Exactly one trailing newline
}

/**
 * Validate filename pattern without exact match.
 * Allows dynamic parts (timestamps, order numbers) to vary.
 */
export function validateFilename(
  actual: string,
  pattern: RegExp
): { valid: boolean; message?: string } {
  if (pattern.test(actual)) {
    return { valid: true };
  }
  return {
    valid: false,
    message: `Filename "${actual}" does not match pattern ${pattern}`
  };
}
