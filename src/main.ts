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
const DEFAULT_CONFIG_URL = '/config.yaml';

interface PreviewData {
  header: Record<string, string | null>;
  detail: Record<string, string | null>[];
  headerContent: string;
  detailContent: string;
  zipFilename: string;
}

interface AppState {
  // State
  loading: boolean;
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
  onSourceChange(): void;
  onFileChange(event: Event): void;
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

      // Load configuration
      const response = await fetch(configUrl);
      if (!response.ok) {
        throw createNetworkError(
          `Failed to load configuration from ${configUrl} (HTTP ${response.status})`,
          [
            'Check that the configuration file exists',
            'Verify the URL is correct',
            'Try refreshing the page',
          ]
        );
      }

      const configText = await response.text();
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

      console.log(`Tikrana loaded: ${this.sources.length} sources from ${configUrl}`);
    } catch (err) {
      this.formatError(err);
      console.error('Config load error:', err);
    } finally {
      this.loading = false;
    }
  },

  onSourceChange() {
    const source = this.sources.find(s => s.name === this.selectedSource);
    this.dynamicFields = source?.userInputFields ?? [];
    this.formData = {};
    this.clearMessages();
    // Initialize form data with empty strings
    for (const field of this.dynamicFields) {
      this.formData[field.name] = '';
    }
  },

  onFileChange(event: Event) {
    const input = event.target as HTMLInputElement;
    this.file = input.files?.[0] ?? null;
    this.clearMessages();
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

    this.processing = true;

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
      this.preview = {
        header: result.extractedData!.header,
        detail: result.extractedData!.detail,
        headerContent: result.headerContent!,
        detailContent: result.detailContent!,
        zipFilename: result.zipFilename!,
      };
    } catch (err) {
      this.formatError(err);
      console.error('Processing error:', err);
    } finally {
      this.processing = false;
    }
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
