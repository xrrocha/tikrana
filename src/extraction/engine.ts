/**
 * Extraction Engine
 *
 * Orchestrates Excel data extraction based on source configuration.
 * Mirrors the Kotlin excel2erp extraction logic.
 */

import { ExcelReader, ExcelSheet } from '../excel/reader';
import type { SourceConfig, ResultConfig, SourceProperty } from '../config/types';

/**
 * Extracted data from an Excel file.
 */
export interface ExtractedData {
  /** Header fields (single record) */
  header: Record<string, string>;
  /** Detail rows (multiple records) */
  detail: Record<string, string>[];
}

/**
 * Apply regex replacements to a value.
 * Mirrors Kotlin's SourceProperty.convert()
 */
export function applyReplacements(value: string, replacements?: Record<string, string>): string {
  if (!replacements || Object.keys(replacements).length === 0) {
    return value;
  }

  let result = value;
  for (const [pattern, replacement] of Object.entries(replacements)) {
    const regex = new RegExp(pattern, 'g');
    result = result.replace(regex, replacement);
  }
  return result;
}

/**
 * Extract header fields from Excel using cell locators.
 */
export function extractHeader(
  sheet: ExcelSheet,
  headerConfig: SourceProperty[],
  defaultValues: Record<string, string>
): Record<string, string> {
  const result: Record<string, string> = { ...defaultValues };

  for (const prop of headerConfig) {
    const rawValue = sheet.readCell(prop.locator);
    result[prop.name] = applyReplacements(rawValue, prop.replacements);
  }

  return result;
}

/**
 * Extract detail rows from Excel using table locator.
 */
export function extractDetail(
  sheet: ExcelSheet,
  detailConfig: { locator: string; endValue?: string; properties: SourceProperty[] }
): Record<string, string>[] {
  // Read the table
  const rawRows = sheet.readTable(detailConfig.locator, detailConfig.endValue);

  // Map column names to property names and apply replacements
  const propertyMap = new Map(
    detailConfig.properties.map(p => [p.locator, p])
  );

  return rawRows.map(row => {
    const result: Record<string, string> = {};

    for (const [columnName, value] of Object.entries(row)) {
      const prop = propertyMap.get(columnName);
      if (prop) {
        result[prop.name] = applyReplacements(value, prop.replacements);
      }
    }

    return result;
  });
}

/**
 * Extract all data from an Excel file using source configuration.
 */
export function extractFromExcel(
  data: ArrayBuffer | Uint8Array,
  sourceConfig: SourceConfig
): ExtractedData {
  const reader = new ExcelReader(data);
  const sheet = reader.getSheet(sourceConfig.sheetIndex);

  const header = extractHeader(
    sheet,
    sourceConfig.header,
    sourceConfig.defaultValues
  );

  const detail = extractDetail(sheet, sourceConfig.detail);

  return { header, detail };
}

/**
 * Merge user input with extracted header data.
 */
export function mergeUserInput(
  extracted: Record<string, string>,
  userInput: Record<string, string>
): Record<string, string> {
  return { ...extracted, ...userInput };
}

/**
 * Normalize date value to YYYYMMDD format.
 * Handles various input formats and strips non-digit characters.
 * Mirrors Kotlin's Property.normalize() for date fields.
 */
export function normalizeDate(value: string): string {
  // Remove all non-digit characters
  return value.replace(/\D+/g, '');
}

/**
 * Expand property references in a string.
 * Replaces ${name} with the corresponding value from props.
 * Mirrors Kotlin's expand() function.
 */
export function expand(template: string, props: Record<string, string | number | null | undefined>): string {
  return template.replace(/\$\{(\w+)\}/g, (_, name) => {
    const value = props[name];
    if (value === null || value === undefined) {
      return '';
    }
    return String(value);
  });
}

/**
 * Generate content for a result file (header or detail).
 */
export function generateFileContent(
  separator: string,
  prolog: string | undefined,
  properties: { name: string; defaultValue?: string }[],
  records: Record<string, string | number | null | undefined>[],
  epilog?: string
): string {
  const lines: string[] = [];

  // Add prolog if present
  if (prolog) {
    lines.push(prolog.trimEnd());
  }

  // Generate data lines
  for (let index = 0; index < records.length; index++) {
    const record = records[index];
    const values = properties.map(prop => {
      let value = record[prop.name];

      // Use defaultValue if not present
      if (value === null || value === undefined || value === '') {
        value = prop.defaultValue ?? '';
      }

      // Expand ${index} and other placeholders
      if (typeof value === 'string' && value.includes('${')) {
        value = expand(value, { ...record, index });
      }

      return String(value ?? '');
    });
    lines.push(values.join(separator));
  }

  // Add epilog if present
  if (epilog) {
    lines.push(epilog.trimEnd());
  }

  return lines.join('\n');
}

/**
 * Validate that all required properties are present and non-empty.
 * Returns list of missing/empty property names.
 */
export function validateRecord(
  record: Record<string, string | null | undefined>,
  requiredProperties: string[]
): string[] {
  return requiredProperties.filter(name => {
    const value = record[name];
    return value === null || value === undefined || value.trim() === '';
  });
}

/**
 * Full extraction and validation pipeline.
 */
export interface ProcessingResult {
  success: boolean;
  headerContent?: string;
  detailContent?: string;
  zipFilename?: string;
  error?: string;
}

export function processExcel(
  data: ArrayBuffer | Uint8Array,
  sourceConfig: SourceConfig,
  resultConfig: ResultConfig,
  userInput: Record<string, string>
): ProcessingResult {
  try {
    // Extract data from Excel
    const extracted = extractFromExcel(data, sourceConfig);

    // Merge with user input and normalize dates
    const headerData = mergeUserInput(extracted.header, userInput);

    // Normalize date fields in header
    for (const prop of resultConfig.header.properties) {
      if (prop.type === 'date' && headerData[prop.name]) {
        headerData[prop.name] = normalizeDate(headerData[prop.name]);
      }
    }

    // Validate header
    const requiredHeaderProps = resultConfig.header.properties
      .filter(p => p.defaultValue === undefined)
      .map(p => p.name);
    const missingHeader = validateRecord(headerData, requiredHeaderProps);
    if (missingHeader.length > 0) {
      return {
        success: false,
        error: `Missing header properties: ${missingHeader.join(', ')}`,
      };
    }

    // Note: We don't validate detail rows for missing columns because:
    // 1. Some fixtures have mismatched column names (e.g., TIA's "BARRAS" vs "PROVEEDOR COD.BARRAS")
    // 2. Kotlin allows this and just produces empty values
    // 3. Contract tests will catch any parity issues

    // Generate header content (single record as array)
    const headerContent = generateFileContent(
      resultConfig.separator,
      resultConfig.header.prolog,
      resultConfig.header.properties,
      [headerData],
      resultConfig.header.epilog
    );

    // Generate detail content (add defaults to each row)
    const detailWithDefaults = extracted.detail.map(row => {
      const withDefaults: Record<string, string> = {};
      for (const prop of resultConfig.detail.properties) {
        withDefaults[prop.name] = row[prop.name] ?? prop.defaultValue ?? '';
      }
      return withDefaults;
    });

    const detailContent = generateFileContent(
      resultConfig.separator,
      resultConfig.detail.prolog,
      resultConfig.detail.properties,
      detailWithDefaults,
      resultConfig.detail.epilog
    );

    // Generate ZIP filename
    const zipFilename = expand(resultConfig.baseName, {
      ...headerData,
      sourceName: sourceConfig.name,
    }) + '.zip';

    return {
      success: true,
      headerContent,
      detailContent,
      zipFilename,
    };
  } catch (err) {
    return {
      success: false,
      error: err instanceof Error ? err.message : 'Unknown error during extraction',
    };
  }
}
