/**
 * Error Types and Validation
 *
 * Comprehensive error handling with user-friendly messages.
 * Errors are categorized by type to help users understand and fix issues.
 */

/**
 * Error categories for user-facing messages.
 */
export enum ErrorCategory {
  /** File format or structure issues */
  FILE_FORMAT = 'FILE_FORMAT',
  /** Configuration issues */
  CONFIG = 'CONFIG',
  /** Data extraction issues */
  EXTRACTION = 'EXTRACTION',
  /** Validation failures */
  VALIDATION = 'VALIDATION',
  /** Network/loading issues */
  NETWORK = 'NETWORK',
  /** Unknown/unexpected errors */
  UNKNOWN = 'UNKNOWN',
}

/**
 * Structured error with category and user-friendly message.
 */
export class TikranaError extends Error {
  constructor(
    public readonly category: ErrorCategory,
    public readonly userMessage: string,
    public readonly technicalDetails?: string,
    public readonly suggestions?: string[]
  ) {
    super(userMessage);
    this.name = 'TikranaError';
  }

  /**
   * Format error for display to user.
   */
  toDisplayString(): string {
    let result = this.userMessage;
    if (this.suggestions && this.suggestions.length > 0) {
      result += '\n\nSuggestions:\n' + this.suggestions.map(s => `• ${s}`).join('\n');
    }
    return result;
  }
}

/**
 * File format validation errors.
 */
export function createFileFormatError(details: string, suggestions?: string[]): TikranaError {
  return new TikranaError(
    ErrorCategory.FILE_FORMAT,
    `Invalid file format: ${details}`,
    details,
    suggestions ?? [
      'Ensure the file is a valid Excel file (.xls or .xlsx)',
      'Try opening and re-saving the file in Excel',
      'Check if the file is corrupted or password-protected',
    ]
  );
}

/**
 * Configuration errors.
 */
export function createConfigError(details: string, suggestions?: string[]): TikranaError {
  return new TikranaError(
    ErrorCategory.CONFIG,
    `Configuration error: ${details}`,
    details,
    suggestions ?? [
      'Verify the configuration file is valid YAML/JSON',
      'Check that all required fields are present',
      'Reload the page to re-fetch configuration',
    ]
  );
}

/**
 * Extraction errors.
 */
export function createExtractionError(details: string, suggestions?: string[]): TikranaError {
  return new TikranaError(
    ErrorCategory.EXTRACTION,
    `Data extraction failed: ${details}`,
    details,
    suggestions ?? [
      'Verify the Excel file matches the selected source type',
      'Check that the file contains the expected data structure',
      'Ensure the file has data in the expected sheet and cells',
    ]
  );
}

/**
 * Validation errors.
 */
export function createValidationError(details: string, suggestions?: string[]): TikranaError {
  return new TikranaError(
    ErrorCategory.VALIDATION,
    details,
    details,
    suggestions
  );
}

/**
 * Network/loading errors.
 */
export function createNetworkError(details: string, suggestions?: string[]): TikranaError {
  return new TikranaError(
    ErrorCategory.NETWORK,
    `Failed to load: ${details}`,
    details,
    suggestions ?? [
      'Check your internet connection',
      'Verify the configuration URL is correct',
      'Try refreshing the page',
    ]
  );
}

/**
 * Wrap unknown errors with context.
 */
export function wrapError(error: unknown, context: string): TikranaError {
  if (error instanceof TikranaError) {
    return error;
  }

  const message = error instanceof Error ? error.message : String(error);

  // Try to categorize common errors
  if (message.includes('Invalid cell address')) {
    return createConfigError(`Invalid cell locator in configuration: ${message}`, [
      'Check that all cell locators (e.g., "B3", "A12") are valid',
      'Cell locators must be in Excel A1 notation',
    ]);
  }

  if (message.includes('Sheet index') || message.includes('out of range')) {
    return createExtractionError(`Sheet not found: ${message}`, [
      'Verify the Excel file has the expected number of sheets',
      'Check the sheetIndex in the source configuration',
    ]);
  }

  if (message.includes('Failed to load config')) {
    return createNetworkError(message);
  }

  return new TikranaError(
    ErrorCategory.UNKNOWN,
    `${context}: ${message}`,
    message,
    ['If this error persists, please contact support']
  );
}

/**
 * Validation result with detailed feedback.
 */
export interface ValidationResult {
  valid: boolean;
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
}

export interface ValidationIssue {
  field: string;
  message: string;
  value?: string;
}

/**
 * Create a successful validation result.
 */
export function validResult(): ValidationResult {
  return { valid: true, errors: [], warnings: [] };
}

/**
 * Create a failed validation result.
 */
export function invalidResult(errors: ValidationIssue[], warnings: ValidationIssue[] = []): ValidationResult {
  return { valid: false, errors, warnings };
}
