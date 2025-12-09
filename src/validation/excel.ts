/**
 * Excel File Validation
 *
 * Validates Excel files before processing to provide early, clear feedback.
 */

import * as XLSX from 'xlsx';
import type { SourceConfig } from '../config/types';
import {
  createFileFormatError,
  createExtractionError,
  createValidationError,
  type ValidationResult,
  type ValidationIssue,
  validResult,
  invalidResult,
} from './errors';

/**
 * Supported Excel file extensions.
 */
const VALID_EXTENSIONS = ['.xls', '.xlsx', '.xlsm', '.xlsb'];

/**
 * Excel file magic bytes for format detection.
 */
const XLSX_MAGIC = [0x50, 0x4B, 0x03, 0x04]; // PK.. (ZIP format)
const XLS_MAGIC = [0xD0, 0xCF, 0x11, 0xE0];  // OLE compound document

/**
 * Validate file extension.
 */
export function validateFileExtension(filename: string): ValidationResult {
  const ext = filename.toLowerCase().slice(filename.lastIndexOf('.'));

  if (!VALID_EXTENSIONS.includes(ext)) {
    return invalidResult([{
      field: 'file',
      message: `Invalid file extension "${ext}". Expected: ${VALID_EXTENSIONS.join(', ')}`,
      value: filename,
    }]);
  }

  return validResult();
}

/**
 * Validate file magic bytes to detect format.
 */
export function validateFileMagic(data: ArrayBuffer): ValidationResult {
  const bytes = new Uint8Array(data.slice(0, 8));

  // Check for XLSX (ZIP) format
  const isXlsx = XLSX_MAGIC.every((b, i) => bytes[i] === b);

  // Check for XLS (OLE) format
  const isXls = XLS_MAGIC.every((b, i) => bytes[i] === b);

  if (!isXlsx && !isXls) {
    return invalidResult([{
      field: 'file',
      message: 'File does not appear to be a valid Excel file. The file format signature is not recognized.',
    }]);
  }

  return validResult();
}

/**
 * Validate that file is not empty or too small.
 */
export function validateFileSize(data: ArrayBuffer, filename: string): ValidationResult {
  const errors: ValidationIssue[] = [];

  if (data.byteLength === 0) {
    errors.push({
      field: 'file',
      message: 'File is empty',
      value: filename,
    });
  } else if (data.byteLength < 512) {
    errors.push({
      field: 'file',
      message: `File is too small (${data.byteLength} bytes). This does not appear to be a valid Excel file.`,
      value: filename,
    });
  }

  // Warn about very large files (> 10MB)
  const warnings: ValidationIssue[] = [];
  if (data.byteLength > 10 * 1024 * 1024) {
    warnings.push({
      field: 'file',
      message: `Large file (${(data.byteLength / 1024 / 1024).toFixed(1)} MB). Processing may take longer.`,
      value: filename,
    });
  }

  return errors.length > 0 ? invalidResult(errors, warnings) : { valid: true, errors: [], warnings };
}

/**
 * Try to parse the Excel file and validate basic structure.
 */
export function validateExcelStructure(data: ArrayBuffer): { valid: boolean; workbook?: XLSX.WorkBook; error?: string } {
  try {
    const workbook = XLSX.read(data, {
      type: 'array',
      cellDates: false,
      cellNF: true,
      cellStyles: false,
    });

    if (!workbook.SheetNames || workbook.SheetNames.length === 0) {
      return { valid: false, error: 'Excel file contains no sheets' };
    }

    return { valid: true, workbook };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);

    // Provide user-friendly messages for common errors
    if (message.includes('password')) {
      return { valid: false, error: 'Excel file is password-protected. Please remove the password and try again.' };
    }
    if (message.includes('CFB')) {
      return { valid: false, error: 'File appears to be corrupted or in an unsupported format.' };
    }
    if (message.includes('Unsupported')) {
      return { valid: false, error: `Unsupported Excel format: ${message}` };
    }

    return { valid: false, error: `Failed to read Excel file: ${message}` };
  }
}

/**
 * Validate that the required sheet exists.
 */
export function validateSheetExists(workbook: XLSX.WorkBook, sheetIndex: number, sourceName: string): ValidationResult {
  if (sheetIndex < 0 || sheetIndex >= workbook.SheetNames.length) {
    return invalidResult([{
      field: 'sheetIndex',
      message: `Sheet index ${sheetIndex} does not exist. File has ${workbook.SheetNames.length} sheet(s): ${workbook.SheetNames.join(', ')}`,
      value: `Source: ${sourceName}`,
    }], [{
      field: 'sheetIndex',
      message: 'Verify the Excel file has the expected sheet structure',
    }]);
  }

  return validResult();
}

/**
 * Validate that expected header cells are not empty.
 */
export function validateHeaderCells(
  workbook: XLSX.WorkBook,
  sourceConfig: SourceConfig
): ValidationResult {
  const errors: ValidationIssue[] = [];
  const warnings: ValidationIssue[] = [];

  const sheet = workbook.Sheets[workbook.SheetNames[sourceConfig.sheetIndex]];
  if (!sheet) {
    return invalidResult([{
      field: 'sheet',
      message: `Sheet at index ${sourceConfig.sheetIndex} not found`,
    }]);
  }

  // Check each header cell locator
  for (const prop of sourceConfig.header) {
    const cell = sheet[prop.locator];
    if (!cell || cell.v === undefined || cell.v === null || cell.v === '') {
      warnings.push({
        field: prop.name,
        message: `Cell ${prop.locator} is empty (expected: ${prop.name})`,
        value: prop.locator,
      });
    }
  }

  return { valid: true, errors, warnings };
}

/**
 * Validate that the detail table exists and has data.
 */
export function validateDetailTable(
  workbook: XLSX.WorkBook,
  sourceConfig: SourceConfig
): ValidationResult {
  const errors: ValidationIssue[] = [];
  const warnings: ValidationIssue[] = [];

  const sheet = workbook.Sheets[workbook.SheetNames[sourceConfig.sheetIndex]];
  if (!sheet) {
    return invalidResult([{
      field: 'sheet',
      message: `Sheet at index ${sourceConfig.sheetIndex} not found`,
    }]);
  }

  // Check that the table start cell exists
  const tableStart = sheet[sourceConfig.detail.locator];
  if (!tableStart || tableStart.v === undefined || tableStart.v === null || tableStart.v === '') {
    errors.push({
      field: 'detail.locator',
      message: `Table header cell ${sourceConfig.detail.locator} is empty. Expected table to start here.`,
      value: sourceConfig.detail.locator,
    });
    return invalidResult(errors, warnings);
  }

  // Check for expected column headers
  const expectedColumns = sourceConfig.detail.properties.map(p => p.locator);
  const range = XLSX.utils.decode_range(sheet['!ref'] || 'A1');
  const startCell = XLSX.utils.decode_cell(sourceConfig.detail.locator);

  // Read first row (headers)
  const foundHeaders: string[] = [];
  for (let col = startCell.c; col <= range.e.c; col++) {
    const cellAddr = XLSX.utils.encode_cell({ r: startCell.r, c: col });
    const cell = sheet[cellAddr];
    if (cell && cell.v !== undefined && cell.v !== null && cell.v !== '') {
      foundHeaders.push(String(cell.v));
    } else {
      break; // Stop at first empty header
    }
  }

  // Check which expected columns are missing
  const missingColumns = expectedColumns.filter(col => !foundHeaders.includes(col));
  if (missingColumns.length > 0) {
    warnings.push({
      field: 'columns',
      message: `Expected column(s) not found: ${missingColumns.join(', ')}. Found: ${foundHeaders.join(', ')}`,
      value: missingColumns.join(', '),
    });
  }

  // Check for at least one data row
  const firstDataRowCell = XLSX.utils.encode_cell({ r: startCell.r + 1, c: startCell.c });
  const firstDataCell = sheet[firstDataRowCell];
  if (!firstDataCell || firstDataCell.v === undefined || firstDataCell.v === null || firstDataCell.v === '') {
    errors.push({
      field: 'detail',
      message: 'Table appears to have no data rows',
      value: firstDataRowCell,
    });
  }

  return errors.length > 0 ? invalidResult(errors, warnings) : { valid: true, errors: [], warnings };
}

/**
 * Full Excel file validation.
 */
export interface ExcelValidationResult {
  valid: boolean;
  workbook?: XLSX.WorkBook;
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
}

export function validateExcelFile(
  data: ArrayBuffer,
  filename: string,
  sourceConfig: SourceConfig
): ExcelValidationResult {
  const allErrors: ValidationIssue[] = [];
  const allWarnings: ValidationIssue[] = [];

  // 1. File size validation
  const sizeResult = validateFileSize(data, filename);
  allErrors.push(...sizeResult.errors);
  allWarnings.push(...sizeResult.warnings);
  if (!sizeResult.valid) {
    return { valid: false, errors: allErrors, warnings: allWarnings };
  }

  // 2. File extension validation
  const extResult = validateFileExtension(filename);
  allErrors.push(...extResult.errors);
  if (!extResult.valid) {
    return { valid: false, errors: allErrors, warnings: allWarnings };
  }

  // 3. Magic bytes validation
  const magicResult = validateFileMagic(data);
  allErrors.push(...magicResult.errors);
  if (!magicResult.valid) {
    return { valid: false, errors: allErrors, warnings: allWarnings };
  }

  // 4. Parse Excel structure
  const structureResult = validateExcelStructure(data);
  if (!structureResult.valid) {
    allErrors.push({
      field: 'file',
      message: structureResult.error ?? 'Failed to parse Excel file',
    });
    return { valid: false, errors: allErrors, warnings: allWarnings };
  }

  const workbook = structureResult.workbook!;

  // 5. Sheet exists validation
  const sheetResult = validateSheetExists(workbook, sourceConfig.sheetIndex, sourceConfig.name);
  allErrors.push(...sheetResult.errors);
  allWarnings.push(...sheetResult.warnings);
  if (!sheetResult.valid) {
    return { valid: false, workbook, errors: allErrors, warnings: allWarnings };
  }

  // 6. Header cells validation
  const headerResult = validateHeaderCells(workbook, sourceConfig);
  allWarnings.push(...headerResult.warnings);

  // 7. Detail table validation
  const detailResult = validateDetailTable(workbook, sourceConfig);
  allErrors.push(...detailResult.errors);
  allWarnings.push(...detailResult.warnings);

  return {
    valid: allErrors.length === 0,
    workbook,
    errors: allErrors,
    warnings: allWarnings,
  };
}
