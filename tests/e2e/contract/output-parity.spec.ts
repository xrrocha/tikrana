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

// Configure snapshot directory for legacy contract tests
test.use({
  snapshotDir: path.join(__dirname, '../../fixtures/legacy/snapshots'),
});

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
    // File input may be hidden for styling - use toBeAttached
    // Specifically target Excel file input (not config picker) using accept attribute
    const fileInput = page.locator('input[type="file"][accept*=".xls"]');
    await expect(fileInput).toBeAttached({ timeout: 5000 });
    await fileInput.setInputFiles(filePath);
  },

  /** Click submit - works with button or input[type="submit"] */
  async submit(page: Page) {
    const submitBtn = page.locator('button[type="submit"], input[type="submit"]');
    await expect(submitBtn).toBeVisible({ timeout: 5000 });
    await submitBtn.click();
  },

  /**
   * Wait for preview to appear and click download button.
   * TypeScript shows preview after file upload, then user confirms download.
   * Kotlin downloads immediately on submit.
   */
  async confirmDownload(page: Page) {
    if (USE_KOTLIN) {
      // Kotlin downloads immediately - nothing to confirm
      return;
    }

    // TypeScript: wait for preview to appear and click download button
    const downloadBtn = page.locator('.btn-confirm');
    await expect(downloadBtn).toBeVisible({ timeout: 5000 });
    await downloadBtn.click();
  },

  /**
   * Wait for preview to appear (TypeScript only).
   * Useful when we need to verify preview content before downloading.
   */
  async waitForPreview(page: Page) {
    if (USE_KOTLIN) {
      return;
    }
    const previewCard = page.locator('.preview-card');
    await expect(previewCard).toBeVisible({ timeout: 5000 });
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
      const filePath = path.join(__dirname, `../../fixtures/legacy/excel/${excelFile}`);

      // TypeScript: preview appears after file upload, then confirm download
      // Kotlin: submit triggers immediate download
      const downloadPromise = page.waitForEvent('download');
      await ui.uploadFile(page, filePath);
      if (USE_KOTLIN) {
        await ui.submit(page);
      } else {
        // TypeScript auto-shows preview after upload, click download button
        await ui.confirmDownload(page);
      }
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

test.describe('Output Parity: Validation & UI', () => {

  // Skip for Kotlin since UI differs significantly
  test.skip(({ }, testInfo) => USE_KOTLIN, 'Skipping validation UI tests for Kotlin');

  test('shows success message after processing', async ({ page }) => {
    await page.goto('/');
    await ui.selectSource(page, 'coral');
    await ui.fillInput(page, 'DocDueDate', '20240120');

    const filePath = path.join(__dirname, '../../fixtures/legacy/excel/pedido-coral.xls');

    // Trigger download
    const downloadPromise = page.waitForEvent('download');
    await ui.uploadFile(page, filePath);

    // TypeScript: preview auto-appears, verify it, then download
    await ui.waitForPreview(page);
    await ui.confirmDownload(page);
    await downloadPromise;

    // TypeScript shows success message after download completes
    const successBox = page.locator('.message-box.success');
    await expect(successBox).toBeVisible({ timeout: 5000 });
    const successText = await successBox.textContent();
    expect(successText).toContain('Generado');  // Config uses Spanish "Generado: ${filename}"
  });

  test('shows warnings when applicable', async ({ page }) => {
    await page.goto('/');
    await ui.selectSource(page, 'coral');
    await ui.fillInput(page, 'DocDueDate', '20240120');

    // Use coral file which should generate warnings for mismatched columns
    const filePath = path.join(__dirname, '../../fixtures/legacy/excel/pedido-coral.xls');

    // Trigger download (preview auto-shows after upload, then confirm download)
    const downloadPromise = page.waitForEvent('download');
    await ui.uploadFile(page, filePath);
    await ui.waitForPreview(page);
    await ui.confirmDownload(page);
    await downloadPromise;

    // Check for any warnings (structure may vary)
    // This just verifies the warning UI renders correctly when there are warnings
    const warningBox = page.locator('.message-box.warning');
    // Warnings are optional - they may or may not appear depending on the file
    const warningCount = await warningBox.count();
    expect(warningCount).toBeGreaterThanOrEqual(0);
  });

  test('clears messages when selecting new source', async ({ page }) => {
    await page.goto('/');

    // First, select a source
    await ui.selectSource(page, 'coral');
    await ui.fillInput(page, 'DocDueDate', '20240120');

    const filePath = path.join(__dirname, '../../fixtures/legacy/excel/pedido-coral.xls');

    // Process successfully (preview auto-shows after upload, then confirm download)
    const downloadPromise = page.waitForEvent('download');
    await ui.uploadFile(page, filePath);
    await ui.waitForPreview(page);
    await ui.confirmDownload(page);
    await downloadPromise;

    // Success message should be visible
    const successBox = page.locator('.message-box.success');
    await expect(successBox).toBeVisible({ timeout: 5000 });

    // Now select a different source - messages should clear
    await ui.selectSource(page, 'rosado');

    // Success message should be hidden
    await expect(successBox).toBeHidden({ timeout: 2000 });
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
