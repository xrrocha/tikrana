/**
 * Demo Test Fixtures
 *
 * Fixed user inputs and test sources for the Rey Pepinito demo scenario.
 * These are public, fictional test fixtures that work out-of-the-box.
 *
 * Source analysis (what's extracted vs needs user input):
 * - el-dorado: Extracts NumAtCard, DocDate. Needs: DocDueDate
 * - cascabel: Extracts NumAtCard, DocDate. Needs: DocDueDate
 * - la-nanita: Empty header. Needs: DocDate, DocDueDate, NumAtCard
 * - la-pinta: Extracts NumAtCard. Needs: DocDate, DocDueDate
 * - uber-gross: Extracts NumAtCard, DocDate, DocDueDate. Needs: nothing
 *
 * Note: CardCode has defaultValue per source, so not needed from user.
 */
export const DEMO_USER_INPUTS: Record<string, Record<string, string>> = {
  'el-dorado': {
    DocDueDate: '20240215',
  },
  'cascabel': {
    DocDueDate: '20240215',
  },
  'la-nanita': {
    DocDate: '20240201',
    DocDueDate: '20240215',
    NumAtCard: 'DEMO-LN-001',
  },
  'la-pinta': {
    DocDate: '20240201',
    DocDueDate: '20240215',
  },
  'uber-gross': {
    // All fields extracted from Excel - no user input needed
  },
};

/**
 * Filename patterns for demo sources.
 * Format: erp-pedido-${sourceName}-${NumAtCard}.zip
 */
export const DEMO_FILENAME_PATTERNS: Record<string, RegExp> = {
  'el-dorado': /^erp-pedido-el-dorado-.+\.zip$/,
  'cascabel': /^erp-pedido-cascabel-.+\.zip$/,
  'la-nanita': /^erp-pedido-la-nanita-DEMO-LN-001\.zip$/,
  'la-pinta': /^erp-pedido-la-pinta-.+\.zip$/,
  'uber-gross': /^erp-pedido-uber-gross-.+\.zip$/,
};

/**
 * Demo source metadata for contract tests.
 */
export const DEMO_SOURCES = [
  { name: 'el-dorado', file: 'el-dorado.xlsx' },
  { name: 'cascabel', file: 'cascabel.xlsx' },
  { name: 'la-nanita', file: 'la-nanita.xlsx' },
  { name: 'la-pinta', file: 'la-pinta.xlsx' },
  { name: 'uber-gross', file: 'uber-gross.xlsx' },
] as const;
