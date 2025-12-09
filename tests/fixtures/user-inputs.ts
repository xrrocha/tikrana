/**
 * Fixed user inputs for contract tests.
 * These values are used by BOTH Kotlin and TypeScript tests.
 *
 * IMPORTANT: These are constants, not dynamic values.
 * Tests should NEVER use Date.now(), random values, etc.
 *
 * User inputs are fields that:
 * 1. Are NOT extracted from Excel (no locator in source.header)
 * 2. Have NO defaultValue in result.header.properties
 * 3. Have a `prompt` field (meaning user must provide them)
 *
 * Analysis per source:
 * - coral: Extracts NumAtCard, DocDate from Excel. Needs: DocDueDate
 * - rosado: Extracts NumAtCard, DocDate from Excel. Needs: DocDueDate
 * - santamaria: Empty header (extracts nothing). Needs: DocDate, DocDueDate, NumAtCard
 * - supermaxi: Extracts NumAtCard, DocDate, DocDueDate from Excel. Needs: nothing
 * - tia: Empty header (extracts nothing). Needs: DocDate, DocDueDate, NumAtCard
 *
 * Note: CardCode has defaultValue per source, so not needed from user.
 */
export const FIXED_USER_INPUTS: Record<string, Record<string, string>> = {
  coral: {
    DocDueDate: '20240120',  // Fixed date, YYYYMMDD format (no dashes per excel2erp output)
  },
  rosado: {
    DocDueDate: '20240120',
  },
  santamaria: {
    DocDate: '20240115',
    DocDueDate: '20240120',
    NumAtCard: 'TEST-SM-001',
  },
  supermaxi: {
    // SuperMaxi extracts all needed fields from Excel
    // No user input required
  },
  tia: {
    DocDate: '20240115',
    DocDueDate: '20240120',
    NumAtCard: 'TEST-TIA-001',
  },
} as const;

/**
 * Expected filename patterns for each source.
 * Used for validation (not exact match due to dynamic parts from Excel).
 *
 * Pattern: sap-pedido-${sourceName}-${NumAtCard}.zip
 * NumAtCard comes from Excel for coral/rosado/supermaxi, from user for santamaria/tia
 */
export const FILENAME_PATTERNS: Record<string, RegExp> = {
  coral: /^sap-pedido-coral-.+\.zip$/,
  rosado: /^sap-pedido-rosado-.+\.zip$/,
  santamaria: /^sap-pedido-santamaria-TEST-SM-001\.zip$/,
  supermaxi: /^sap-pedido-supermaxi-.+\.zip$/,
  tia: /^sap-pedido-tia-TEST-TIA-001\.zip$/,
} as const;

/**
 * Source metadata for contract tests.
 */
export const TEST_SOURCES = [
  { name: 'coral', file: 'pedido-coral.xls' },
  { name: 'rosado', file: 'pedido-rosado.xlsx' },
  { name: 'santamaria', file: 'pedido-santamaria.xlsx' },
  { name: 'supermaxi', file: 'pedido-supermaxi.xlsx' },
  { name: 'tia', file: 'pedido-tia.xlsx' },
] as const;
