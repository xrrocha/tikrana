/**
 * Tests for validation/errors.ts
 */

import { describe, it, expect } from 'vitest';
import {
  ErrorCategory,
  TikranaError,
  createFileFormatError,
  createConfigError,
  createExtractionError,
  createValidationError,
  createNetworkError,
  wrapError,
  validResult,
  invalidResult,
} from '../../../src/validation/errors';

describe('TikranaError', () => {
  it('creates error with all properties', () => {
    const error = new TikranaError(
      ErrorCategory.FILE_FORMAT,
      'User message',
      'Technical details',
      ['Suggestion 1', 'Suggestion 2']
    );

    expect(error.category).toBe(ErrorCategory.FILE_FORMAT);
    expect(error.userMessage).toBe('User message');
    expect(error.technicalDetails).toBe('Technical details');
    expect(error.suggestions).toEqual(['Suggestion 1', 'Suggestion 2']);
    expect(error.message).toBe('User message');
    expect(error.name).toBe('TikranaError');
  });

  it('toDisplayString includes suggestions', () => {
    const error = new TikranaError(
      ErrorCategory.FILE_FORMAT,
      'File is corrupted',
      undefined,
      ['Try re-downloading', 'Contact support']
    );

    const display = error.toDisplayString();
    expect(display).toContain('File is corrupted');
    expect(display).toContain('Suggestions:');
    expect(display).toContain('• Try re-downloading');
    expect(display).toContain('• Contact support');
  });

  it('toDisplayString without suggestions', () => {
    const error = new TikranaError(
      ErrorCategory.UNKNOWN,
      'Something went wrong'
    );

    expect(error.toDisplayString()).toBe('Something went wrong');
  });
});

describe('Error factory functions', () => {
  it('createFileFormatError has correct category', () => {
    const error = createFileFormatError('Not an Excel file');
    expect(error.category).toBe(ErrorCategory.FILE_FORMAT);
    expect(error.userMessage).toContain('Invalid file format');
    expect(error.suggestions).toBeDefined();
    expect(error.suggestions!.length).toBeGreaterThan(0);
  });

  it('createConfigError has correct category', () => {
    const error = createConfigError('Missing field: sources');
    expect(error.category).toBe(ErrorCategory.CONFIG);
    expect(error.userMessage).toContain('Configuration error');
  });

  it('createExtractionError has correct category', () => {
    const error = createExtractionError('Sheet not found');
    expect(error.category).toBe(ErrorCategory.EXTRACTION);
    expect(error.userMessage).toContain('Data extraction failed');
  });

  it('createValidationError uses message directly', () => {
    const error = createValidationError('Missing required fields: DocNum');
    expect(error.category).toBe(ErrorCategory.VALIDATION);
    expect(error.userMessage).toBe('Missing required fields: DocNum');
  });

  it('createNetworkError has correct category', () => {
    const error = createNetworkError('HTTP 404');
    expect(error.category).toBe(ErrorCategory.NETWORK);
    expect(error.userMessage).toContain('Failed to load');
  });

  it('allows custom suggestions', () => {
    const error = createFileFormatError('Bad file', ['Custom suggestion']);
    expect(error.suggestions).toEqual(['Custom suggestion']);
  });
});

describe('wrapError', () => {
  it('passes through TikranaError unchanged', () => {
    const original = createFileFormatError('Test error');
    const wrapped = wrapError(original, 'Context');
    expect(wrapped).toBe(original);
  });

  it('wraps regular Error with context', () => {
    const error = new Error('Something failed');
    const wrapped = wrapError(error, 'Processing');
    expect(wrapped.category).toBe(ErrorCategory.UNKNOWN);
    expect(wrapped.userMessage).toContain('Processing');
    expect(wrapped.userMessage).toContain('Something failed');
  });

  it('wraps non-Error values', () => {
    const wrapped = wrapError('string error', 'Context');
    expect(wrapped.userMessage).toContain('string error');
  });

  it('categorizes invalid cell address errors', () => {
    const error = new Error('Invalid cell address: XYZ');
    const wrapped = wrapError(error, 'Parsing');
    expect(wrapped.category).toBe(ErrorCategory.CONFIG);
    expect(wrapped.userMessage).toContain('Invalid cell locator');
  });

  it('categorizes sheet index errors', () => {
    const error = new Error('Sheet index 5 out of range');
    const wrapped = wrapError(error, 'Reading');
    expect(wrapped.category).toBe(ErrorCategory.EXTRACTION);
    expect(wrapped.userMessage).toContain('Sheet not found');
  });

  it('categorizes config load errors', () => {
    const error = new Error('Failed to load config from /config.yaml');
    const wrapped = wrapError(error, 'Init');
    expect(wrapped.category).toBe(ErrorCategory.NETWORK);
  });
});

describe('ValidationResult helpers', () => {
  it('validResult returns valid state', () => {
    const result = validResult();
    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
    expect(result.warnings).toEqual([]);
  });

  it('invalidResult returns invalid state with errors', () => {
    const errors = [{ field: 'file', message: 'Too small' }];
    const warnings = [{ field: 'size', message: 'Large file' }];
    const result = invalidResult(errors, warnings);

    expect(result.valid).toBe(false);
    expect(result.errors).toEqual(errors);
    expect(result.warnings).toEqual(warnings);
  });

  it('invalidResult defaults warnings to empty', () => {
    const result = invalidResult([{ field: 'test', message: 'error' }]);
    expect(result.warnings).toEqual([]);
  });
});
