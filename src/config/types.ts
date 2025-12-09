/**
 * Configuration Types
 *
 * TypeScript interfaces defining the YAML configuration schema.
 */

/**
 * A source property defines how to extract a value from Excel.
 * Used for both header cells and detail columns.
 */
export interface SourceProperty {
  /** Property name in the output */
  name: string;
  /** Cell address (e.g., "B3") for header, or column name (e.g., "BARRA") for detail */
  locator: string;
  /** Regex replacements to apply to the extracted value */
  replacements?: Record<string, string>;
}

/**
 * Detail table extraction configuration.
 */
export interface DetailConfig {
  /** A1-style address of the top-left cell (start of header row) */
  locator: string;
  /** Optional value that marks the end of the table (row before this value) */
  endValue?: string;
  /** Properties to extract from each row */
  properties: SourceProperty[];
}

/**
 * Source-specific configuration for extracting data from Excel files.
 */
export interface SourceConfig {
  /** Unique source identifier */
  name: string;
  /** Human-readable description */
  description: string;
  /** Optional logo filename */
  logo?: string;
  /** Sheet index to read from (0-based) */
  sheetIndex: number;
  /** Header properties extracted from specific cells */
  header: SourceProperty[];
  /** Detail table configuration */
  detail: DetailConfig;
  /** Default values for properties not extracted from Excel */
  defaultValues: Record<string, string>;
}

/**
 * Result property with optional default value and type.
 */
export interface ResultProperty {
  /** Property name */
  name: string;
  /** HTML input type (text, date, etc.) */
  type?: string;
  /** User-facing prompt */
  prompt?: string;
  /** Explanatory text */
  fyi?: string;
  /** Default value if not provided */
  defaultValue?: string;
}

/**
 * Output file specification.
 */
export interface FileSpec {
  /** Output filename */
  filename: string;
  /** Text prepended to output */
  prolog?: string;
  /** Text appended to output */
  epilog?: string;
  /** Properties/columns in this file */
  properties: ResultProperty[];
}

/**
 * Result configuration defining output format.
 */
export interface ResultConfig {
  /** Field separator (typically tab) */
  separator: string;
  /** Base filename pattern with ${variable} placeholders */
  baseName: string;
  /** Header file specification */
  header: FileSpec;
  /** Detail file specification */
  detail: FileSpec;
}

/**
 * User input field configuration (fields that need user input, not extracted from Excel).
 */
export interface UserInputField {
  /** Field name */
  name: string;
  /** HTML input type */
  type: string;
  /** User-facing prompt */
  prompt: string;
}

/**
 * Full application configuration.
 */
export interface AppConfig {
  /** Application name */
  name: string;
  /** Application description */
  description: string;
  /** Logo filename */
  logo?: string;
  /** UI parameters */
  parameters: {
    source: string;
    workbook: string;
    submit: string;
    extractionError: string;
  };
  /** Available sources */
  sources: SourceConfig[];
  /** Result output configuration */
  result: ResultConfig;
}

/**
 * Runtime source configuration with computed fields.
 * This is what the UI uses.
 */
export interface RuntimeSource {
  /** Source identifier */
  name: string;
  /** Human-readable description */
  description: string;
  /** Fields requiring user input (not extracted from Excel, no default) */
  userInputFields: UserInputField[];
}

/**
 * Derive runtime sources from full config.
 * Computes which fields need user input based on what's extracted vs defaulted.
 */
export function deriveRuntimeSources(config: AppConfig): RuntimeSource[] {
  const resultHeaderProps = config.result.header.properties;

  return config.sources.map(source => {
    // Find properties that need user input:
    // 1. Defined in result header
    // 2. Not extracted from Excel (not in source.header)
    // 3. No defaultValue in result or source.defaultValues
    const extractedNames = new Set(source.header.map(h => h.name));
    const defaultedNames = new Set([
      ...Object.keys(source.defaultValues),
      ...resultHeaderProps.filter(p => p.defaultValue !== undefined).map(p => p.name),
    ]);

    const userInputFields: UserInputField[] = resultHeaderProps
      .filter(prop => {
        // Not extracted and not defaulted = needs user input
        return !extractedNames.has(prop.name) && !defaultedNames.has(prop.name);
      })
      .map(prop => ({
        name: prop.name,
        type: prop.type ?? 'text',
        prompt: prop.prompt ?? prop.name,
      }));

    return {
      name: source.name,
      description: source.description,
      userInputFields,
    };
  });
}
