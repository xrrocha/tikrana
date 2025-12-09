import { test, expect, Page } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';
import JSZip from 'jszip';
import { normalizeContent, validateFilename } from '../../helpers/normalize.ts';
import { DEMO_USER_INPUTS, DEMO_FILENAME_PATTERNS, DEMO_SOURCES } from '../../fixtures/demo-inputs.ts';

// ESM equivalent of __dirname
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/**
 * Demo tests for the Rey Pepinito scenario.
 *
 * These tests use public, fictional test data that ships with the repository.
 * They verify the Excel processing pipeline works correctly with the demo config.
 *
 * Run with: bun run test:e2e:demo
 */

/**
 * UI abstraction for interacting with the TypeScript app.
 */
const ui = {
  /** Select source from dropdown and wait for form to load */
  async selectSource(page: Page, sourceName: string) {
    const select = page.locator('select[name="source"], select');
    await expect(select).toBeVisible({ timeout: 10000 });
    await select.selectOption(sourceName);
    await page.waitForTimeout(500);
  },

  /** Fill a named input field */
  async fillInput(page: Page, fieldName: string, value: string) {
    const input = page.locator(`input[name="${fieldName}"]`);
    await expect(input).toBeVisible({ timeout: 5000 });

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

  /** Upload Excel file */
  async uploadFile(page: Page, filePath: string) {
    const fileInput = page.locator('input[type="file"][accept*=".xls"]');
    await expect(fileInput).toBeAttached({ timeout: 5000 });
    await fileInput.setInputFiles(filePath);
  },

  /** Wait for preview and click download */
  async confirmDownload(page: Page) {
    const downloadBtn = page.locator('.btn-confirm');
    await expect(downloadBtn).toBeVisible({ timeout: 5000 });
    await downloadBtn.click();
  },

  /** Wait for preview to appear */
  async waitForPreview(page: Page) {
    const previewCard = page.locator('.preview-card');
    await expect(previewCard).toBeVisible({ timeout: 5000 });
  },
};

test.describe('Demo: Rey Pepinito Excel Processing', () => {

  for (const { name: sourceName, file: excelFile } of DEMO_SOURCES) {
    test(`${sourceName}: processes Excel and generates ZIP`, async ({ page }) => {
      await page.goto('/');

      // Select source
      await ui.selectSource(page, sourceName);

      // Fill user inputs (if any for this source)
      const userInputs = DEMO_USER_INPUTS[sourceName] ?? {};
      for (const [field, value] of Object.entries(userInputs)) {
        await ui.fillInput(page, field, value);
      }

      // Upload Excel file
      const filePath = path.join(__dirname, `../../fixtures/demo/excel/${excelFile}`);

      // Process and download
      const downloadPromise = page.waitForEvent('download');
      await ui.uploadFile(page, filePath);
      await ui.confirmDownload(page);
      const download = await downloadPromise;

      // Validate filename pattern
      const filename = download.suggestedFilename();
      const pattern = DEMO_FILENAME_PATTERNS[sourceName];
      if (pattern) {
        const filenameCheck = validateFilename(filename, pattern);
        expect(filenameCheck.valid, filenameCheck.message).toBe(true);
      }

      // Read and verify ZIP contents
      const zipBuffer = await streamToBuffer(await download.createReadStream());
      const zip = await JSZip.loadAsync(zipBuffer);

      const headerContent = await zip.file('cabecera.txt')?.async('string');
      const detailContent = await zip.file('detalle.txt')?.async('string');

      expect(headerContent, 'cabecera.txt should exist in ZIP').toBeDefined();
      expect(detailContent, 'detalle.txt should exist in ZIP').toBeDefined();

      // Normalize and snapshot
      const normalizedHeader = normalizeContent(headerContent!);
      const normalizedDetail = normalizeContent(detailContent!);

      expect(normalizedHeader).toMatchSnapshot(`${sourceName}-cabecera.txt`);
      expect(normalizedDetail).toMatchSnapshot(`${sourceName}-detalle.txt`);
    });
  }
});

test.describe('Demo: UI Behavior', () => {

  test('shows preview before download', async ({ page }) => {
    await page.goto('/');
    await ui.selectSource(page, 'uber-gross');

    const filePath = path.join(__dirname, '../../fixtures/demo/excel/uber-gross.xlsx');

    await ui.uploadFile(page, filePath);
    await ui.waitForPreview(page);

    // Verify preview shows data
    const previewCard = page.locator('.preview-card');
    await expect(previewCard).toBeVisible();

    // Header section should be visible
    const headerSection = previewCard.locator('.preview-section').first();
    await expect(headerSection).toBeVisible();
  });

  test('shows success message after download', async ({ page }) => {
    await page.goto('/');
    await ui.selectSource(page, 'uber-gross');

    const filePath = path.join(__dirname, '../../fixtures/demo/excel/uber-gross.xlsx');

    const downloadPromise = page.waitForEvent('download');
    await ui.uploadFile(page, filePath);
    await ui.confirmDownload(page);
    await downloadPromise;

    const successBox = page.locator('.message-box.success');
    await expect(successBox).toBeVisible({ timeout: 5000 });
  });

  test('clears state when changing source', async ({ page }) => {
    await page.goto('/');

    // Select first source and process
    await ui.selectSource(page, 'uber-gross');
    const filePath = path.join(__dirname, '../../fixtures/demo/excel/uber-gross.xlsx');

    const downloadPromise = page.waitForEvent('download');
    await ui.uploadFile(page, filePath);
    await ui.confirmDownload(page);
    await downloadPromise;

    // Success should be visible
    const successBox = page.locator('.message-box.success');
    await expect(successBox).toBeVisible({ timeout: 5000 });

    // Change source - success should clear
    await ui.selectSource(page, 'cascabel');
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
