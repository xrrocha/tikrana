/**
 * Unit tests for Excel reader module.
 */

import { describe, it, expect } from 'vitest';
import {
  parseCellAddress,
  colToLetter,
  formatCellValue,
  formatDate,
  formatNumber,
} from '../../../src/excel/reader';

describe('parseCellAddress', () => {
  it('parses simple addresses', () => {
    expect(parseCellAddress('A1')).toEqual({ row: 0, col: 0 });
    expect(parseCellAddress('B3')).toEqual({ row: 2, col: 1 });
    expect(parseCellAddress('Z1')).toEqual({ row: 0, col: 25 });
  });

  it('parses double-letter columns', () => {
    expect(parseCellAddress('AA1')).toEqual({ row: 0, col: 26 });
    expect(parseCellAddress('AB10')).toEqual({ row: 9, col: 27 });
    expect(parseCellAddress('AZ5')).toEqual({ row: 4, col: 51 });
  });

  it('parses triple-letter columns', () => {
    expect(parseCellAddress('AAA1')).toEqual({ row: 0, col: 702 });
  });

  it('is case-insensitive', () => {
    expect(parseCellAddress('a1')).toEqual({ row: 0, col: 0 });
    expect(parseCellAddress('b3')).toEqual({ row: 2, col: 1 });
  });

  it('throws on invalid addresses', () => {
    expect(() => parseCellAddress('')).toThrow('Invalid cell address');
    expect(() => parseCellAddress('1A')).toThrow('Invalid cell address');
    expect(() => parseCellAddress('A')).toThrow('Invalid cell address');
    expect(() => parseCellAddress('1')).toThrow('Invalid cell address');
  });
});

describe('colToLetter', () => {
  it('converts single-letter columns', () => {
    expect(colToLetter(0)).toBe('A');
    expect(colToLetter(1)).toBe('B');
    expect(colToLetter(25)).toBe('Z');
  });

  it('converts double-letter columns', () => {
    expect(colToLetter(26)).toBe('AA');
    expect(colToLetter(27)).toBe('AB');
    expect(colToLetter(51)).toBe('AZ');
    expect(colToLetter(52)).toBe('BA');
  });

  it('converts triple-letter columns', () => {
    expect(colToLetter(702)).toBe('AAA');
  });

  it('round-trips with parseCellAddress', () => {
    for (let col = 0; col < 100; col++) {
      const letter = colToLetter(col);
      const parsed = parseCellAddress(`${letter}1`);
      expect(parsed.col).toBe(col);
    }
  });
});

describe('formatDate', () => {
  it('formats dates as YYYYMMDD', () => {
    expect(formatDate(new Date(2024, 0, 15))).toBe('20240115'); // Jan 15, 2024
    expect(formatDate(new Date(2024, 11, 31))).toBe('20241231'); // Dec 31, 2024
  });

  it('pads single-digit months and days', () => {
    expect(formatDate(new Date(2024, 0, 1))).toBe('20240101'); // Jan 1, 2024
    expect(formatDate(new Date(2024, 8, 5))).toBe('20240905'); // Sep 5, 2024
  });
});

describe('formatNumber', () => {
  it('formats integers without decimals', () => {
    expect(formatNumber(42)).toBe('42');
    expect(formatNumber(0)).toBe('0');
    expect(formatNumber(1234567890)).toBe('1234567890');
  });

  it('formats decimals with up to 2 places', () => {
    expect(formatNumber(3.14)).toBe('3.14');
    expect(formatNumber(2.5)).toBe('2.5');
    expect(formatNumber(1.1)).toBe('1.1');
  });

  it('rounds to 2 decimal places', () => {
    expect(formatNumber(3.14159)).toBe('3.14');
    expect(formatNumber(2.999)).toBe('3');
    expect(formatNumber(2.995)).toBe('3'); // Rounds up
  });

  it('removes trailing zeros', () => {
    expect(formatNumber(2.10)).toBe('2.1');
    expect(formatNumber(2.00)).toBe('2');
  });

  it('handles negative numbers', () => {
    expect(formatNumber(-42)).toBe('-42');
    expect(formatNumber(-3.14)).toBe('-3.14');
  });
});

describe('formatCellValue', () => {
  it('returns empty string for null/undefined/empty', () => {
    expect(formatCellValue(null)).toBe('');
    expect(formatCellValue(undefined)).toBe('');
    expect(formatCellValue('')).toBe('');
  });

  it('formats numbers', () => {
    expect(formatCellValue(42)).toBe('42');
    expect(formatCellValue(3.14)).toBe('3.14');
  });

  it('formats booleans', () => {
    expect(formatCellValue(true)).toBe('true');
    expect(formatCellValue(false)).toBe('false');
  });

  it('formats strings as-is', () => {
    expect(formatCellValue('hello')).toBe('hello');
    expect(formatCellValue('  spaces  ')).toBe('  spaces  ');
  });

  it('formats Date objects', () => {
    expect(formatCellValue(new Date(2024, 0, 15))).toBe('20240115');
  });
});
