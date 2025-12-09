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

// Configuration URL - can be overridden via data attribute or query param
const DEFAULT_CONFIG_URL = '/config.yaml';

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
  processing: boolean;

  // Computed
  canSubmit: boolean;

  // Methods
  init(): Promise<void>;
  onSourceChange(): void;
  onFileChange(event: Event): void;
  process(): Promise<void>;
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
  processing: false,

  get canSubmit(): boolean {
    if (!this.selectedSource || !this.file || this.processing) return false;
    // Check all required fields are filled
    for (const field of this.dynamicFields) {
      if (!this.formData[field.name]) return false;
    }
    return true;
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
        throw new Error(`Failed to load config from ${configUrl}: ${response.status}`);
      }

      const configText = await response.text();
      this.config = parseYamlConfig(configText);
      this.sources = deriveRuntimeSources(this.config);

      console.log(`Tikrana loaded: ${this.sources.length} sources from ${configUrl}`);
    } catch (err) {
      this.error = err instanceof Error ? err.message : 'Failed to load configuration';
      console.error('Config load error:', err);
    } finally {
      this.loading = false;
    }
  },

  onSourceChange() {
    const source = this.sources.find(s => s.name === this.selectedSource);
    this.dynamicFields = source?.userInputFields ?? [];
    this.formData = {};
    this.error = '';
    // Initialize form data with empty strings
    for (const field of this.dynamicFields) {
      this.formData[field.name] = '';
    }
  },

  onFileChange(event: Event) {
    const input = event.target as HTMLInputElement;
    this.file = input.files?.[0] ?? null;
    this.error = '';
  },

  async process() {
    this.error = '';

    if (!this.file || !this.selectedSource || !this.config) {
      this.error = 'Please select a source and file';
      return;
    }

    this.processing = true;

    try {
      // Read file as ArrayBuffer
      const arrayBuffer = await this.file.arrayBuffer();

      // Get source configuration
      const sourceConfig = getSourceConfig(this.config, this.selectedSource);
      if (!sourceConfig) {
        throw new Error(`Unknown source: ${this.selectedSource}`);
      }

      // Process Excel file
      const result = processExcel(
        arrayBuffer,
        sourceConfig,
        this.config.result,
        this.formData
      );

      if (!result.success) {
        throw new Error(result.error ?? 'Processing failed');
      }

      // Generate and download ZIP
      await downloadZip(
        result.zipFilename!,
        this.config.result.header.filename,
        result.headerContent!,
        this.config.result.detail.filename,
        result.detailContent!
      );

      console.log(`Generated: ${result.zipFilename}`);
    } catch (err) {
      this.error = err instanceof Error ? err.message : 'Unknown error occurred';
      console.error('Processing error:', err);
    } finally {
      this.processing = false;
    }
  }
}));

Alpine.start();
