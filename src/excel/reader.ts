/**
 * Excel Reader Module
 *
 * SheetJS-based Excel reader for extracting data from spreadsheets.
 * Supports both .xls (BIFF) and .xlsx (Open XML) formats.
 *
 * Key concepts:
 * - Cell locator: Excel A1 notation (e.g., "B3") for single cell access
 * - Table locator: A1 notation marking top-left of a table (header row)
 * - Column locator: Column header name within a table (e.g., "BARRA")
 */

import * as XLSX from 'xlsx';

/**
 * Parse Excel A1-style cell reference to 0-based row/column indices.
 * Examples: "A1" -> {row: 0, col: 0}, "B3" -> {row: 2, col: 1}, "AA10" -> {row: 9, col: 26}
 */
export function parseCellAddress(address: string): { row: number; col: number } {
  const match = address.match(/^([A-Z]+)(\d+)$/i);
  if (!match) {
    throw new Error(`Invalid cell address: ${address}`);
  }

  const colStr = match[1].toUpperCase();
  const rowStr = match[2];

  // Convert column letters to 0-based index (A=0, B=1, ..., Z=25, AA=26, ...)
  let col = 0;
  for (let i = 0; i < colStr.length; i++) {
    col = col * 26 + (colStr.charCodeAt(i) - 64);
  }
  col -= 1; // Convert to 0-based

  const row = parseInt(rowStr, 10) - 1; // Convert to 0-based

  return { row, col };
}

/**
 * Convert 0-based column index to Excel column letters.
 * Examples: 0 -> "A", 1 -> "B", 26 -> "AA"
 */
export function colToLetter(col: number): string {
  let result = '';
  let n = col + 1; // Convert to 1-based
  while (n > 0) {
    const remainder = (n - 1) % 26;
    result = String.fromCharCode(65 + remainder) + result;
    n = Math.floor((n - 1) / 26);
  }
  return result;
}

/**
 * Format a cell value to string.
 * - Dates: YYYYMMDD format
 * - Numbers: Up to 12 integer digits, up to 2 decimal places, dot as separator
 * - Strings: As-is
 * - Empty/null: empty string
 */
export function formatCellValue(value: unknown, isDateCell: boolean = false): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }

  // SheetJS returns dates as JavaScript Date objects when cellDates: true
  if (value instanceof Date) {
    return formatDate(value);
  }

  // SheetJS date serial number (when cellDates: false)
  if (typeof value === 'number' && isDateCell) {
    const date = XLSX.SSF.parse_date_code(value);
    if (date) {
      const year = date.y.toString().padStart(4, '0');
      const month = (date.m).toString().padStart(2, '0');
      const day = date.d.toString().padStart(2, '0');
      return `${year}${month}${day}`;
    }
  }

  if (typeof value === 'number') {
    return formatNumber(value);
  }

  if (typeof value === 'boolean') {
    return value.toString();
  }

  return String(value);
}

/**
 * Format date as YYYYMMDD (ISO basic date format).
 */
export function formatDate(date: Date): string {
  const year = date.getFullYear().toString().padStart(4, '0');
  const month = (date.getMonth() + 1).toString().padStart(2, '0');
  const day = date.getDate().toString().padStart(2, '0');
  return `${year}${month}${day}`;
}

/**
 * Format number with up to 2 decimal places.
 * - Up to 12 integer digits
 * - Up to 2 decimal places (only if needed)
 * - Dot as decimal separator
 */
export function formatNumber(num: number): string {
  // Handle integers - no decimal places
  if (Number.isInteger(num)) {
    return num.toString();
  }

  // Round to 2 decimal places and remove trailing zeros
  const rounded = Math.round(num * 100) / 100;
  const str = rounded.toFixed(2);

  // Remove trailing zeros after decimal point
  return str.replace(/\.?0+$/, '') || '0';
}

/**
 * Workbook wrapper providing high-level access to Excel data.
 */
export class ExcelReader {
  private workbook: XLSX.WorkBook;

  /**
   * Create an ExcelReader from raw Excel data or a pre-parsed workbook.
   *
   * @param data Raw Excel file data (ArrayBuffer/Uint8Array) or pre-parsed XLSX.WorkBook
   */
  constructor(data: ArrayBuffer | Uint8Array | XLSX.WorkBook) {
    // If it's already a workbook (from validation), use it directly
    if (this.isWorkbook(data)) {
      this.workbook = data;
    } else {
      this.workbook = XLSX.read(data, {
        type: 'array',
        cellDates: false,  // Keep dates as serial numbers for precise formatting
        cellNF: true,      // Keep number formats for date detection
        cellStyles: false, // Don't need styles
      });
    }
  }

  /**
   * Type guard to check if data is already a parsed workbook.
   */
  private isWorkbook(data: ArrayBuffer | Uint8Array | XLSX.WorkBook): data is XLSX.WorkBook {
    return data !== null &&
           typeof data === 'object' &&
           'SheetNames' in data &&
           'Sheets' in data;
  }

  /**
   * Get sheet by index (0-based).
   */
  getSheet(index: number): ExcelSheet {
    const sheetName = this.workbook.SheetNames[index];
    if (!sheetName) {
      throw new Error(`Sheet index ${index} out of range (0-${this.workbook.SheetNames.length - 1})`);
    }
    return new ExcelSheet(this.workbook.Sheets[sheetName]);
  }

  /**
   * Get sheet by name.
   */
  getSheetByName(name: string): ExcelSheet {
    const sheet = this.workbook.Sheets[name];
    if (!sheet) {
      throw new Error(`Sheet "${name}" not found. Available: ${this.workbook.SheetNames.join(', ')}`);
    }
    return new ExcelSheet(sheet);
  }

  get sheetNames(): string[] {
    return this.workbook.SheetNames;
  }
}

/**
 * Sheet wrapper providing cell and table access.
 */
export class ExcelSheet {
  constructor(private sheet: XLSX.WorkSheet) {}

  /**
   * Read a single cell by A1-style address (e.g., "B3").
   */
  readCell(address: string): string {
    const { row, col } = parseCellAddress(address);
    return this.readCellByIndex(row, col);
  }

  /**
   * Read a single cell by 0-based row/column indices.
   */
  readCellByIndex(row: number, col: number): string {
    const cellAddress = colToLetter(col) + (row + 1);
    const cell = this.sheet[cellAddress] as XLSX.CellObject | undefined;

    if (!cell) {
      return '';
    }

    // Check if cell is a date based on number format
    const isDate = cell.t === 'n' && cell.z && isDateFormat(cell.z);

    return formatCellValue(cell.v, isDate);
  }

  /**
   * Read a table starting at the given A1-style address.
   * The first row is treated as column headers.
   *
   * @param locator A1-style address of top-left cell (header row start)
   * @param endValue Optional value that marks end of table (exclusive)
   * @returns Array of row objects keyed by column header
   */
  readTable(locator: string, endValue?: string): Record<string, string>[] {
    const { row: startRow, col: startCol } = parseCellAddress(locator);

    // Find last column (first empty cell in header row)
    let lastCol = startCol;
    while (this.readCellByIndex(startRow, lastCol + 1) !== '') {
      lastCol++;
    }

    // Read header labels
    const labels: string[] = [];
    for (let col = startCol; col <= lastCol; col++) {
      labels.push(this.readCellByIndex(startRow, col));
    }

    // Read data rows until empty first cell or endValue
    const rows: Record<string, string>[] = [];
    let currentRow = startRow + 1;

    while (true) {
      const firstCellValue = this.readCellByIndex(currentRow, startCol);

      // Check termination conditions
      if (endValue !== undefined) {
        // endValue mode: stop when we hit the endValue
        if (firstCellValue === endValue) {
          break;
        }
        // Also stop if cell is empty (safety)
        if (firstCellValue.trim() === '') {
          break;
        }
      } else {
        // Default mode: stop when first cell is empty
        if (firstCellValue.trim() === '') {
          break;
        }
      }

      // Read row data
      const rowData: Record<string, string> = {};
      for (let i = 0; i < labels.length; i++) {
        const col = startCol + i;
        rowData[labels[i]] = this.readCellByIndex(currentRow, col);
      }
      rows.push(rowData);
      currentRow++;
    }

    return rows;
  }
}

/**
 * Check if a number format string represents a date.
 * This is a simplified check - SheetJS uses more sophisticated detection.
 */
function isDateFormat(format: string): boolean {
  // Common date format patterns
  const datePatterns = [
    /[dmy]/i,           // Contains d, m, or y
    /\[.*\]/,           // Contains locale-specific markers like [DBNum1]
  ];

  // Patterns that indicate NOT a date (even if containing d/m/y)
  const notDatePatterns = [
    /0\.0/,             // Decimal number format
    /#/,                // Number placeholder
    /\$/,               // Currency
    /%/,                // Percentage
  ];

  // Check if it looks like a date and not a number
  const looksLikeDate = datePatterns.some(p => p.test(format));
  const looksLikeNumber = notDatePatterns.some(p => p.test(format));

  return looksLikeDate && !looksLikeNumber;
}
