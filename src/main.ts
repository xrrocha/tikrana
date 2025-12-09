/**
 * Tikrana Main Application
 *
 * Browser-native Excel to ERP data translation.
 * Loads configuration from YAML/JSON and processes Excel files.
 */

import Alpine from 'alpinejs';
import { parseYamlConfig, getSourceConfig } from './config/loader';
import { deriveRuntimeSources, type AppConfig, type RuntimeSource } from './config/types';
import { processExcel } from './extraction/engine';
import { downloadZip } from './output/zip';
import { TikranaError, createNetworkError, createConfigError, wrapError } from './validation/errors';
import type { ValidationIssue } from './validation/errors';

// Configuration URL - can be overridden via data attribute or query param
const DEFAULT_CONFIG_URL = '/tikrana.yaml';
const CONFIG_STORAGE_KEY = 'tikrana-config';

interface PreviewData {
  header: Record<string, string | null>;
  detail: Record<string, string | null>[];
  headerContent: string;
  detailContent: string;
  zipFilename: string;
  /** Ordered property names for header display */
  headerProperties: string[];
  /** Ordered property names for detail display */
  detailProperties: string[];
}

interface AppState {
  // State
  loading: boolean;
  needsConfigFile: boolean;
  configFileName: string;
  config: AppConfig | null;
  sources: RuntimeSource[];
  selectedSource: string;
  dynamicFields: { name: string; type: string; prompt: string }[];
  formData: Record<string, string>;
  file: File | null;
  error: string;
  errorSuggestions: string[];
  warnings: ValidationIssue[];
  processing: boolean;
  success: boolean;
  successMessage: string;
  preview: PreviewData | null;

  // Computed
  canSubmit: boolean;

  // Methods
  init(): Promise<void>;
  applyConfig(configText: string, source: string): void;
  onConfigFileChange(event: Event): Promise<void>;
  changeConfig(): void;
  onSourceChange(): void;
  onFileChange(event: Event): Promise<void>;
  processForPreview(): Promise<void>;
  process(): Promise<void>;
  confirmDownload(): Promise<void>;
  cancelPreview(): void;
  clearMessages(): void;
  formatError(err: unknown): void;
  getSelectedSourceConfig(): { description: string; logo?: string } | null;
}

// Alpine.js app definition
Alpine.data('app', (): AppState => ({
  loading: true,
  needsConfigFile: false,
  configFileName: '',
  config: null,
  sources: [],
  selectedSource: '',
  dynamicFields: [],
  formData: {},
  file: null,
  error: '',
  errorSuggestions: [],
  warnings: [],
  processing: false,
  success: false,
  successMessage: '',
  preview: null,

  get canSubmit(): boolean {
    if (!this.selectedSource || !this.file || this.processing) return false;
    // Check all required fields are filled
    for (const field of this.dynamicFields) {
      if (!this.formData[field.name]) return false;
    }
    return true;
  },

  clearMessages() {
    this.error = '';
    this.errorSuggestions = [];
    this.warnings = [];
    this.success = false;
    this.successMessage = '';
    this.preview = null;
  },

  formatError(err: unknown) {
    if (err instanceof TikranaError) {
      this.error = err.userMessage;
      this.errorSuggestions = err.suggestions ?? [];
    } else if (err instanceof Error) {
      this.error = err.message;
      this.errorSuggestions = [];
    } else {
      this.error = 'An unexpected error occurred';
      this.errorSuggestions = [];
    }
  },

  async init() {
    console.log('Tikrana app initializing...');

    try {
      // Get config URL from data attribute or query param
      const appElement = document.querySelector('[x-data="app()"]');
      const configUrl = appElement?.getAttribute('data-config')
        || new URLSearchParams(window.location.search).get('config')
        || DEFAULT_CONFIG_URL;

      let configText: string | null = null;
      let configSource = '';

      // Strategy 1: Try fetch (works for http/https)
      try {
        const response = await fetch(configUrl);
        if (response.ok) {
          configText = await response.text();
          configSource = configUrl;
        }
      } catch {
        // fetch failed (likely file:// protocol)
        console.log('Fetch failed, trying localStorage cache...');
      }

      // Strategy 2: Try localStorage cache
      if (!configText) {
        const cached = localStorage.getItem(CONFIG_STORAGE_KEY);
        if (cached) {
          try {
            const cacheData = JSON.parse(cached);
            configText = cacheData.content;
            configSource = `cached: ${cacheData.fileName}`;
            this.configFileName = cacheData.fileName;
            console.log(`Using cached config: ${cacheData.fileName}`);
          } catch {
            console.log('Invalid cache, clearing...');
            localStorage.removeItem(CONFIG_STORAGE_KEY);
          }
        }
      }

      // Strategy 3: Need user to select config file
      if (!configText) {
        console.log('No config available, showing file picker...');
        this.needsConfigFile = true;
        this.loading = false;
        return;
      }

      // Parse and apply configuration
      this.applyConfig(configText, configSource);

    } catch (err) {
      this.formatError(err);
      console.error('Config load error:', err);
    } finally {
      this.loading = false;
    }
  },

  /** Parse and apply configuration from text content */
  applyConfig(configText: string, source: string) {
    try {
      this.config = parseYamlConfig(configText);
    } catch (parseErr) {
      throw createConfigError(
        `Invalid configuration file: ${parseErr instanceof Error ? parseErr.message : String(parseErr)}`,
        [
          'Verify the configuration file is valid YAML',
          'Check for syntax errors in the config file',
        ]
      );
    }

    this.sources = deriveRuntimeSources(this.config);

    if (this.sources.length === 0) {
      throw createConfigError('No sources defined in configuration', [
        'Add at least one source definition to the config file',
      ]);
    }

    this.needsConfigFile = false;
    console.log(`Tikrana loaded: ${this.sources.length} sources from ${source}`);
  },

  /** Handle config file selection */
  async onConfigFileChange(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.loading = true;
    this.error = '';
    this.errorSuggestions = [];

    try {
      const configText = await file.text();
      this.configFileName = file.name;

      // Cache to localStorage for future sessions
      localStorage.setItem(CONFIG_STORAGE_KEY, JSON.stringify({
        fileName: file.name,
        content: configText,
        timestamp: Date.now(),
      }));

      this.applyConfig(configText, file.name);
    } catch (err) {
      this.formatError(err);
      console.error('Config file error:', err);
    } finally {
      this.loading = false;
    }
  },

  /** Allow user to change/reload config */
  changeConfig() {
    localStorage.removeItem(CONFIG_STORAGE_KEY);
    this.config = null;
    this.sources = [];
    this.selectedSource = '';
    this.configFileName = '';
    this.needsConfigFile = true;
    this.clearMessages();
  },

  onSourceChange() {
    const source = this.sources.find(s => s.name === this.selectedSource);
    this.dynamicFields = source?.userInputFields ?? [];
    this.formData = {};
    this.file = null;
    this.clearMessages();
    // Initialize form data with empty strings
    for (const field of this.dynamicFields) {
      this.formData[field.name] = '';
    }
  },

  async onFileChange(event: Event) {
    const input = event.target as HTMLInputElement;
    this.file = input.files?.[0] ?? null;

    // Clear previous results but keep form state
    this.error = '';
    this.errorSuggestions = [];
    this.warnings = [];
    this.success = false;
    this.successMessage = '';
    this.preview = null;

    // If we have a file, source selected, and all required fields filled, process immediately
    if (this.file && this.selectedSource && this.config) {
      // Check if all required fields are filled
      const allFieldsFilled = this.dynamicFields.every(field => this.formData[field.name]);
      if (allFieldsFilled) {
        await this.processForPreview();
      }
    }
  },

  async processForPreview() {
    if (!this.file || !this.selectedSource || !this.config) return;

    this.processing = true;

    // Allow UI to update before heavy processing
    await new Promise(resolve => requestAnimationFrame(resolve));

    try {
      // Read file as ArrayBuffer
      const arrayBuffer = await this.file.arrayBuffer();

      // Get source configuration
      const sourceConfig = getSourceConfig(this.config, this.selectedSource);
      if (!sourceConfig) {
        throw createConfigError(`Unknown source type: ${this.selectedSource}`, [
          'Select a valid source from the dropdown',
          'Reload the page to refresh the configuration',
        ]);
      }

      // Process Excel file with full validation
      const result = processExcel(
        arrayBuffer,
        sourceConfig,
        this.config.result,
        this.formData,
        this.file.name
      );

      // Show warnings even if successful
      if (result.warnings && result.warnings.length > 0) {
        this.warnings = result.warnings;
      }

      if (!result.success) {
        if (result.error) {
          throw result.error;
        }
        throw new Error('Processing failed');
      }

      // Store preview data for user confirmation
      // Only show fields that are extracted from Excel or provided by user (not constants)
      const extractedHeaderFields = new Set(sourceConfig.header.map(p => p.name));
      const userInputFields = new Set(this.dynamicFields.map(f => f.name));
      const extractedDetailFields = new Set(sourceConfig.detail.properties.map(p => p.name));

      // Filter to show only meaningful fields in config order
      const headerProperties = this.config.result.header.properties
        .map(p => p.name)
        .filter(name => extractedHeaderFields.has(name) || userInputFields.has(name));
      const detailProperties = this.config.result.detail.properties
        .map(p => p.name)
        .filter(name => extractedDetailFields.has(name));

      this.preview = {
        header: result.extractedData!.header,
        detail: result.extractedData!.detail,
        headerContent: result.headerContent!,
        detailContent: result.detailContent!,
        zipFilename: result.zipFilename!,
        headerProperties,
        detailProperties,
      };
    } catch (err) {
      this.formatError(err);
      console.error('Processing error:', err);
    } finally {
      this.processing = false;
    }
  },

  getSelectedSourceConfig() {
    if (!this.config || !this.selectedSource) return null;
    return this.config.sources.find(s => s.name === this.selectedSource) ?? null;
  },

  async process() {
    this.clearMessages();

    if (!this.file || !this.selectedSource || !this.config) {
      this.error = 'Please select a source and file';
      this.errorSuggestions = [];
      return;
    }

    // Reprocess to update preview with any form field changes
    await this.processForPreview();
  },

  async confirmDownload() {
    if (!this.preview || !this.config) return;

    try {
      // Generate and download ZIP
      await downloadZip(
        this.preview.zipFilename,
        this.config.result.header.filename,
        this.preview.headerContent,
        this.config.result.detail.filename,
        this.preview.detailContent
      );

      // Show success message (use config template or default)
      this.success = true;
      const successTemplate = this.config.parameters?.successMessage ?? 'Generated: ${filename}';
      this.successMessage = successTemplate.replace('${filename}', this.preview.zipFilename);
      console.log(`Generated: ${this.preview.zipFilename}`);

      // Clear preview after download
      this.preview = null;
    } catch (err) {
      this.formatError(err);
      console.error('Download error:', err);
    }
  },

  cancelPreview() {
    this.preview = null;
    this.warnings = [];
  }
}));

Alpine.start();
