/**
 * One-time verification that Excel fixtures are static.
 *
 * Run manually before setting up contract tests:
 *   bun run tests/helpers/verify-fixtures.ts
 */
import * as XLSX from 'xlsx';
import { readFileSync, readdirSync } from 'fs';
import { join } from 'path';

const FIXTURES_DIR = './tests/fixtures/excel/legacy';

function checkForDynamicFormulas(workbook: XLSX.WorkBook): string[] {
  const dynamicFormulas: string[] = [];

  for (const sheetName of workbook.SheetNames) {
    const sheet = workbook.Sheets[sheetName];
    if (!sheet) continue;

    for (const cellRef in sheet) {
      if (cellRef.startsWith('!')) continue;

      const cell = sheet[cellRef] as XLSX.CellObject | undefined;
      if (cell?.f) {
        // Check for dynamic formulas
        const formula = cell.f.toUpperCase();
        if (
          formula.includes('TODAY()') ||
          formula.includes('NOW()') ||
          formula.includes('RAND')
        ) {
          dynamicFormulas.push(`${sheetName}!${cellRef}: ${cell.f}`);
        }
      }
    }
  }

  return dynamicFormulas;
}

// Main
const files = readdirSync(FIXTURES_DIR).filter(f => /\.xlsx?$/.test(f));

let hasIssues = false;

for (const file of files) {
  const filePath = join(FIXTURES_DIR, file);
  const buffer = readFileSync(filePath);
  const workbook = XLSX.read(buffer, { type: 'buffer' });

  const dynamicFormulas = checkForDynamicFormulas(workbook);

  if (dynamicFormulas.length > 0) {
    hasIssues = true;
    console.error(`\n❌ ${file} has dynamic formulas:`);
    dynamicFormulas.forEach(f => console.error(`   ${f}`));
  } else {
    console.log(`✓ ${file} is static`);
  }
}

if (hasIssues) {
  console.error('\n⚠️  Some fixtures have dynamic formulas.');
  console.error('   Replace with static values before running contract tests.');
  process.exit(1);
} else {
  console.log('\n✅ All fixtures are static and suitable for contract testing.');
}
