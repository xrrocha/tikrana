/**
 * Tests for validation/excel.ts
 */

import { describe, it, expect } from 'vitest';
import * as fs from 'fs';
import * as path from 'path';
import {
  validateFileExtension,
  validateFileMagic,
  validateFileSize,
  validateExcelStructure,
  validateSheetExists,
  validateExcelFile,
} from '../../../src/validation/excel';

// Helper to create a minimal valid XLSX file (ZIP format with magic bytes)
function createMockXlsxData(): ArrayBuffer {
  // Real XLSX is a ZIP file - we need actual test files for full validation
  // This creates just the magic bytes for basic tests
  const data = new Uint8Array([0x50, 0x4B, 0x03, 0x04, ...Array(508).fill(0)]);
  return data.buffer;
}

// Helper to create mock XLS file (OLE format)
function createMockXlsData(): ArrayBuffer {
  const data = new Uint8Array([0xD0, 0xCF, 0x11, 0xE0, ...Array(508).fill(0)]);
  return data.buffer;
}

// Load a real test fixture
const fixturesPath = path.join(__dirname, '../../../tests/fixtures/legacy/excel');

function loadFixture(name: string): ArrayBuffer {
  const filePath = path.join(fixturesPath, name);
  const buffer = fs.readFileSync(filePath);
  return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength);
}

describe('validateFileExtension', () => {
  it('accepts .xlsx files', () => {
    expect(validateFileExtension('test.xlsx').valid).toBe(true);
  });

  it('accepts .xls files', () => {
    expect(validateFileExtension('report.xls').valid).toBe(true);
  });

  it('accepts .xlsm files', () => {
    expect(validateFileExtension('macro.xlsm').valid).toBe(true);
  });

  it('accepts .xlsb files', () => {
    expect(validateFileExtension('binary.xlsb').valid).toBe(true);
  });

  it('is case-insensitive', () => {
    expect(validateFileExtension('TEST.XLSX').valid).toBe(true);
    expect(validateFileExtension('Report.XLS').valid).toBe(true);
  });

  it('rejects .csv files', () => {
    const result = validateFileExtension('data.csv');
    expect(result.valid).toBe(false);
    expect(result.errors[0].message).toContain('.csv');
    expect(result.errors[0].message).toContain('.xlsx');
  });

  it('rejects .txt files', () => {
    const result = validateFileExtension('notes.txt');
    expect(result.valid).toBe(false);
  });

  it('rejects files without extension', () => {
    const result = validateFileExtension('noextension');
    expect(result.valid).toBe(false);
  });
});

describe('validateFileMagic', () => {
  it('accepts XLSX magic bytes (ZIP format)', () => {
    const data = createMockXlsxData();
    expect(validateFileMagic(data).valid).toBe(true);
  });

  it('accepts XLS magic bytes (OLE format)', () => {
    const data = createMockXlsData();
    expect(validateFileMagic(data).valid).toBe(true);
  });

  it('rejects invalid magic bytes', () => {
    const data = new Uint8Array([0x00, 0x00, 0x00, 0x00, ...Array(508).fill(0)]).buffer;
    const result = validateFileMagic(data);
    expect(result.valid).toBe(false);
    expect(result.errors[0].message).toContain('not recognized');
  });

  it('rejects PNG files', () => {
    // PNG magic: 89 50 4E 47
    const data = new Uint8Array([0x89, 0x50, 0x4E, 0x47, ...Array(508).fill(0)]).buffer;
    const result = validateFileMagic(data);
    expect(result.valid).toBe(false);
  });

  it('rejects PDF files', () => {
    // PDF magic: 25 50 44 46 (%PDF)
    const data = new Uint8Array([0x25, 0x50, 0x44, 0x46, ...Array(508).fill(0)]).buffer;
    const result = validateFileMagic(data);
    expect(result.valid).toBe(false);
  });
});

describe('validateFileSize', () => {
  it('accepts normal file sizes', () => {
    const data = new ArrayBuffer(10000);
    expect(validateFileSize(data, 'test.xlsx').valid).toBe(true);
  });

  it('rejects empty files', () => {
    const data = new ArrayBuffer(0);
    const result = validateFileSize(data, 'empty.xlsx');
    expect(result.valid).toBe(false);
    expect(result.errors[0].message).toContain('empty');
  });

  it('rejects files too small to be Excel', () => {
    const data = new ArrayBuffer(100);
    const result = validateFileSize(data, 'tiny.xlsx');
    expect(result.valid).toBe(false);
    expect(result.errors[0].message).toContain('too small');
  });

  it('warns about very large files', () => {
    // Mock a 15MB file
    const data = { byteLength: 15 * 1024 * 1024 } as ArrayBuffer;
    const result = validateFileSize(data, 'huge.xlsx');
    expect(result.valid).toBe(true);
    expect(result.warnings.length).toBeGreaterThan(0);
    expect(result.warnings[0].message).toContain('Large file');
  });
});

describe('validateExcelStructure', () => {
  it('validates real pedido-coral.xls fixture', () => {
    const data = loadFixture('pedido-coral.xls');
    const result = validateExcelStructure(data);
    expect(result.valid).toBe(true);
    expect(result.workbook).toBeDefined();
    expect(result.workbook!.SheetNames.length).toBeGreaterThan(0);
  });

  it('validates real pedido-rosado.xlsx fixture', () => {
    const data = loadFixture('pedido-rosado.xlsx');
    const result = validateExcelStructure(data);
    expect(result.valid).toBe(true);
    expect(result.workbook).toBeDefined();
  });

  it('returns error for corrupted data', () => {
    const data = new Uint8Array([0x50, 0x4B, 0x03, 0x04, 0xFF, 0xFF]).buffer;
    const result = validateExcelStructure(data);
    expect(result.valid).toBe(false);
    expect(result.error).toBeDefined();
  });
});

describe('validateSheetExists', () => {
  it('validates sheet at index 0', () => {
    const data = loadFixture('pedido-coral.xls');
    const structure = validateExcelStructure(data);
    const result = validateSheetExists(structure.workbook!, 0, 'coral');
    expect(result.valid).toBe(true);
  });

  it('rejects negative sheet index', () => {
    const data = loadFixture('pedido-coral.xls');
    const structure = validateExcelStructure(data);
    const result = validateSheetExists(structure.workbook!, -1, 'coral');
    expect(result.valid).toBe(false);
    expect(result.errors[0].message).toContain('does not exist');
  });

  it('rejects out of range sheet index', () => {
    const data = loadFixture('pedido-coral.xls');
    const structure = validateExcelStructure(data);
    const sheetCount = structure.workbook!.SheetNames.length;
    const result = validateSheetExists(structure.workbook!, sheetCount + 5, 'coral');
    expect(result.valid).toBe(false);
    expect(result.errors[0].message).toContain('does not exist');
  });
});

describe('validateExcelFile (full validation)', () => {
  // Minimal source config for testing
  const minimalConfig = {
    name: 'test',
    description: 'Test source',
    sheetIndex: 0,
    header: [
      { name: 'DocNum', locator: 'A1' }
    ],
    detail: {
      locator: 'A3',
      properties: [
        { name: 'ItemCode', locator: 'CODIGO' }
      ]
    },
    defaultValues: {}
  };

  it('validates pedido-coral.xls with matching config', () => {
    const data = loadFixture('pedido-coral.xls');
    const coralConfig = {
      name: 'coral',
      description: 'Coral',
      sheetIndex: 0,
      header: [
        { name: 'CardCode', locator: 'B3' }
      ],
      detail: {
        locator: 'A12',
        properties: [
          { name: 'ItemCode', locator: 'CODIGO DE BARRAS' }
        ]
      },
      defaultValues: { WhsCode: 'BD-GYE' }
    };

    const result = validateExcelFile(data, 'pedido-coral.xls', coralConfig);
    expect(result.valid).toBe(true);
    expect(result.workbook).toBeDefined();
    expect(result.errors).toEqual([]);
  });

  it('rejects empty file', () => {
    const data = new ArrayBuffer(0);
    const result = validateExcelFile(data, 'empty.xlsx', minimalConfig);
    expect(result.valid).toBe(false);
    expect(result.errors.some(e => e.message.includes('empty'))).toBe(true);
  });

  it('rejects wrong file extension', () => {
    const data = loadFixture('pedido-coral.xls');
    const result = validateExcelFile(data, 'data.csv', minimalConfig);
    expect(result.valid).toBe(false);
    expect(result.errors.some(e => e.message.includes('.csv'))).toBe(true);
  });

  it('rejects wrong sheet index', () => {
    const data = loadFixture('pedido-coral.xls');
    const wrongSheetConfig = { ...minimalConfig, sheetIndex: 99 };
    const result = validateExcelFile(data, 'pedido-coral.xls', wrongSheetConfig);
    expect(result.valid).toBe(false);
    expect(result.errors.some(e => e.message.includes('Sheet index'))).toBe(true);
  });

  it('collects warnings from all validation steps', () => {
    const data = loadFixture('pedido-coral.xls');
    // Config with header cells that don't exist
    const configWithMissingHeaders = {
      ...minimalConfig,
      header: [
        { name: 'Missing1', locator: 'Z99' },
        { name: 'Missing2', locator: 'AA100' }
      ],
      detail: {
        locator: 'A12',
        properties: [
          { name: 'Code', locator: 'NONEXISTENT_COLUMN' }
        ]
      }
    };
    const result = validateExcelFile(data, 'pedido-coral.xls', configWithMissingHeaders);
    // Should have warnings about empty header cells and missing columns
    expect(result.warnings.length).toBeGreaterThan(0);
  });
});
