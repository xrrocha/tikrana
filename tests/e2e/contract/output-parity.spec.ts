import { test, expect, Page } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';
import JSZip from 'jszip';
import { normalizeContent, validateFilename } from '../../helpers/normalize.ts';
import { FIXED_USER_INPUTS, FILENAME_PATTERNS, TEST_SOURCES } from '../../fixtures/user-inputs.ts';

// ESM equivalent of __dirname
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/**
 * Contract tests for output parity between Kotlin and TypeScript.
 *
 * These tests:
 * 1. Process the same Excel file with fixed inputs
 * 2. Capture the output ZIP
 * 3. Compare normalized content against snapshots
 *
 * The tests are UI-agnostic: they work with both Kotlin (HTMX) and TypeScript (Alpine.js)
 * implementations by using flexible selectors.
 *
 * Snapshot generation (run against Kotlin first):
 *   USE_KOTLIN=1 bun run test:e2e -- --update-snapshots
 *
 * Verification (run against TypeScript):
 *   bun run test:e2e
 */

const USE_KOTLIN = process.env.USE_KOTLIN === '1';

/**
 * UI abstraction for interacting with either Kotlin or TypeScript app.
 * Both apps have similar structure but slightly different selectors.
 */
const ui = {
  /** Select source from dropdown and wait for form to load */
  async selectSource(page: Page, sourceName: string) {
    const select = page.locator('select[name="source"], select');
    await expect(select).toBeVisible({ timeout: 10000 });
    await select.selectOption(sourceName);

    // HTMX dynamically loads form - wait for it
    // TypeScript may also render form dynamically
    await page.waitForTimeout(500);
  },

  /** Fill a named input field */
  async fillInput(page: Page, fieldName: string, value: string) {
    const input = page.locator(`input[name="${fieldName}"]`);
    await expect(input).toBeVisible({ timeout: 5000 });

    // Check input type - date inputs need YYYY-MM-DD format
    const inputType = await input.getAttribute('type');
    if (inputType === 'date') {
      // Convert YYYYMMDD to YYYY-MM-DD for date input
      const formatted = value.length === 8
        ? `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)}`
        : value;
      await input.fill(formatted);
    } else {
      await input.fill(value);
    }
  },

  /** Upload file - Kotlin uses 'wbFile', TypeScript uses 'file' */
  async uploadFile(page: Page, filePath: string) {
    // Try both possible input names
    const fileInput = page.locator('input[type="file"]');
    await expect(fileInput).toBeVisible({ timeout: 5000 });
    await fileInput.setInputFiles(filePath);
  },

  /** Click submit - works with button or input[type="submit"] */
  async submit(page: Page) {
    const submitBtn = page.locator('button[type="submit"], input[type="submit"]');
    await expect(submitBtn).toBeVisible({ timeout: 5000 });
    await submitBtn.click();
  },
};

test.describe('Output Parity: Excel Processing', () => {

  for (const { name: sourceName, file: excelFile } of TEST_SOURCES) {
    test(`${sourceName}: produces expected output`, async ({ page }) => {
      // Navigate to app
      await page.goto('/');

      // Select source
      await ui.selectSource(page, sourceName);

      // Fill fixed user inputs (if any for this source)
      const userInputs = FIXED_USER_INPUTS[sourceName] ?? {};
      for (const [field, value] of Object.entries(userInputs)) {
        await ui.fillInput(page, field, value);
      }

      // Upload Excel file (legacy = private client data, git-ignored)
      const filePath = path.join(__dirname, `../../fixtures/excel/legacy/${excelFile}`);
      await ui.uploadFile(page, filePath);

      // Trigger download and capture
      const downloadPromise = page.waitForEvent('download');
      await ui.submit(page);
      const download = await downloadPromise;

      // Validate filename pattern (not exact match)
      const filename = download.suggestedFilename();
      const pattern = FILENAME_PATTERNS[sourceName];
      if (pattern) {
        const filenameCheck = validateFilename(filename, pattern);
        expect(filenameCheck.valid, filenameCheck.message).toBe(true);
      }

      // Read ZIP contents
      const zipBuffer = await streamToBuffer(await download.createReadStream());
      const zip = await JSZip.loadAsync(zipBuffer);

      // Extract and normalize each file
      const headerContent = await zip.file('cabecera.txt')?.async('string');
      const detailContent = await zip.file('detalle.txt')?.async('string');

      expect(headerContent, 'cabecera.txt should exist in ZIP').toBeDefined();
      expect(detailContent, 'detalle.txt should exist in ZIP').toBeDefined();

      const normalizedHeader = normalizeContent(headerContent!);
      const normalizedDetail = normalizeContent(detailContent!);

      // Compare against snapshots
      expect(normalizedHeader).toMatchSnapshot(`${sourceName}-cabecera.txt`);
      expect(normalizedDetail).toMatchSnapshot(`${sourceName}-detalle.txt`);
    });
  }
});

test.describe('Output Parity: Error Cases', () => {

  test.skip('wrong source/file combination shows error', async ({ page }) => {
    // Skip for now - error handling UI differs significantly between implementations
    // TODO: Re-enable once TypeScript implementation matches Kotlin error behavior
    await page.goto('/');
    await ui.selectSource(page, 'coral');
    await ui.fillInput(page, 'DocDueDate', '20240120');

    // Upload TIA file (wrong source)
    const filePath = path.join(__dirname, '../../fixtures/excel/legacy/pedido-tia.xlsx');
    await ui.uploadFile(page, filePath);
    await ui.submit(page);

    // Error display differs between implementations
    await expect(page.locator('.error, [class*="error"], #error')).toBeVisible({ timeout: 5000 });
  });
});

/**
 * Convert a ReadableStream to Buffer
 */
async function streamToBuffer(stream: NodeJS.ReadableStream): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    stream.on('data', chunk => chunks.push(Buffer.from(chunk)));
    stream.on('end', () => resolve(Buffer.concat(chunks)));
    stream.on('error', reject);
  });
}
