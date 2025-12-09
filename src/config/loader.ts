/**
 * Configuration Loader
 *
 * Loads application configuration from YAML or JSON.
 * Configuration defines:
 * - sources: extraction mappings for different input formats
 * - result: output format specification
 */

import YAML from 'yaml';
import type { AppConfig, SourceConfig, ResultConfig, SourceProperty, DetailConfig, FileSpec, ResultProperty } from './types';

/**
 * Raw YAML structure as read from configuration file.
 */
interface RawYamlConfig {
  port?: number;
  assetsDir?: string;
  config: {
    name: string;
    description: string;
    logo?: string;
    parameters: {
      htmx?: string;
      source: string;
      workbook: string;
      submit: string;
      successMessage?: string;
      extractionError: string;
    };
    sources: RawSourceConfig[];
    result: RawResultConfig;
  };
}

interface RawSourceConfig {
  name: string;
  description: string;
  logo?: string;
  sheetIndex?: number;
  defaultValues?: Record<string, string>;
  header?: RawSourceProperty[];
  detail: {
    locator: string;
    endValue?: string;
    properties: RawSourceProperty[];
  };
}

interface RawSourceProperty {
  name: string;
  locator: string;
  replacements?: Record<string, string>;
}

interface RawResultConfig {
  separator: string;
  baseName: string;
  header: RawFileSpec;
  detail: RawFileSpec;
}

interface RawFileSpec {
  filename: string;
  prolog?: string;
  epilog?: string;
  properties: RawResultProperty[];
}

interface RawResultProperty {
  name: string;
  type?: string;
  prompt?: string;
  fyi?: string;
  defaultValue?: string | number;
}

/**
 * Parse YAML config string into AppConfig.
 */
export function parseYamlConfig(yamlString: string): AppConfig {
  const raw = YAML.parse(yamlString) as RawYamlConfig;
  return transformConfig(raw.config);
}

/**
 * Parse JSON config string into AppConfig.
 */
export function parseJsonConfig(jsonString: string): AppConfig {
  const raw = JSON.parse(jsonString) as RawYamlConfig['config'];
  return transformConfig(raw);
}

/**
 * Load config from a file (browser: fetch, Node: fs).
 * Detects format from extension or content.
 */
export async function loadConfigFromUrl(url: string): Promise<AppConfig> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to load config from ${url}: ${response.status} ${response.statusText}`);
  }

  const content = await response.text();

  // Detect format from extension or content
  if (url.endsWith('.yaml') || url.endsWith('.yml') || content.trimStart().startsWith('port:') || content.trimStart().startsWith('config:')) {
    return parseYamlConfig(content);
  } else {
    return parseJsonConfig(content);
  }
}

/**
 * Transform raw config to typed AppConfig.
 */
function transformConfig(raw: RawYamlConfig['config']): AppConfig {
  return {
    name: raw.name,
    description: raw.description,
    logo: raw.logo,
    parameters: {
      source: raw.parameters.source,
      workbook: raw.parameters.workbook,
      submit: raw.parameters.submit,
      successMessage: raw.parameters.successMessage,
      extractionError: raw.parameters.extractionError,
    },
    sources: raw.sources.map(transformSource),
    result: transformResult(raw.result),
  };
}

function transformSource(raw: RawSourceConfig): SourceConfig {
  return {
    name: raw.name,
    description: raw.description,
    logo: raw.logo,
    sheetIndex: raw.sheetIndex ?? 0,
    defaultValues: raw.defaultValues ?? {},
    header: (raw.header ?? []).map(transformSourceProperty),
    detail: transformDetail(raw.detail),
  };
}

function transformSourceProperty(raw: RawSourceProperty): SourceProperty {
  return {
    name: raw.name,
    locator: raw.locator,
    replacements: raw.replacements,
  };
}

function transformDetail(raw: RawSourceConfig['detail']): DetailConfig {
  return {
    locator: raw.locator,
    endValue: raw.endValue,
    properties: raw.properties.map(transformSourceProperty),
  };
}

function transformResult(raw: RawResultConfig): ResultConfig {
  return {
    separator: raw.separator,
    baseName: raw.baseName,
    header: transformFileSpec(raw.header),
    detail: transformFileSpec(raw.detail),
  };
}

function transformFileSpec(raw: RawFileSpec): FileSpec {
  return {
    filename: raw.filename,
    prolog: raw.prolog,
    epilog: raw.epilog,
    properties: raw.properties.map(transformResultProperty),
  };
}

function transformResultProperty(raw: RawResultProperty): ResultProperty {
  return {
    name: raw.name,
    type: raw.type,
    prompt: raw.prompt,
    fyi: raw.fyi,
    // Convert defaultValue to string (YAML may parse numbers)
    defaultValue: raw.defaultValue !== undefined ? String(raw.defaultValue) : undefined,
  };
}

/**
 * Get source config by name.
 */
export function getSourceConfig(config: AppConfig, sourceName: string): SourceConfig | undefined {
  return config.sources.find(s => s.name === sourceName);
}
