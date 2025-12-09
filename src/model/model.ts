/**
 * Rich Domain Model
 *
 * Type-safe, validated configuration model. The MD prefix denotes MetaData types
 * that enforce validity at both compile-time (via TypeScript's type system) and
 * runtime (via constructor validation).
 */

// === Foundational interfaces ===

export interface Showable {
  show(): string;
}

// === Image types ===

export type MDImageType = 'png' | 'jpeg' | 'gif' | 'svg+xml' | 'webp';

export class MDFilename implements Showable {
  constructor(public readonly value: string) {
    const trimmed = value.trim();
    if (trimmed.length === 0 || trimmed !== value) {
      throw new Error(`Invalid filename: "${value}" (must be non-empty and not start/end with whitespace)`);
    }
  }

  show(): string {
    return this.value;
  }
}

export class MDBase64Image implements Showable {
  private static readonly BASE64_REGEX =
    /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/;

  constructor(
    public readonly type: MDImageType,
    public readonly payload: string
  ) {
    if (payload.length === 0 || !MDBase64Image.BASE64_REGEX.test(payload)) {
      throw new Error('Invalid base64 payload');
    }
  }

  show(): string {
    return `data:image/${this.type};base64,${this.payload}`;
  }
}

// === Root configuration ===

export interface MDAppConfig {
  name: string;
  description: string;
  logo?: MDFilename | MDBase64Image;
}
