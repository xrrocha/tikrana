# Tikrana: A Declarative Data Translation Framework

## Overview

Tikrana is a browser-native, offline-capable framework for declarative data translation between arbitrary file formats. It embodies the principle that data transformation between formats should be driven by metadata (schemas) rather than hand-written, format-specific code.

The name "Tikrana" comes from Quechua, meaning "transmutation" or "the means of transformation"—capturing the essence of what the framework does: transmute data from one form to another.

## The Problem Domain

Organizations routinely need to convert data between formats:

- Spreadsheets to ERP import files
- CSV exports to JSON APIs
- Database extracts to fixed-width legacy formats
- JSON payloads to XML documents

The naive approach—writing bespoke conversion code for each source/target pair—leads to:

1. **Combinatorial explosion**: With *n* formats, you need up to *n(n-1)* converters
2. **Code duplication**: Each converter re-implements similar parsing/serialization logic
3. **Brittleness**: Format changes require code changes
4. **Knowledge silos**: Conversion logic is buried in code, inaccessible to non-programmers

## The Solution: Lingua Franca Architecture

Tikrana adopts a *lingua franca* approach that reduces *n(n-1)* converters to *2n* adapters:

```
                    ┌─────────────────────────────┐
                    │   Canonical Object Graph    │
                    │      (JavaScript Objects)   │
                    └──────────────┬──────────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        │                          │                          │
        ▼                          ▼                          ▼
┌───────────────┐          ┌───────────────┐          ┌───────────────┐
│  Excel Adapter │          │  CSV Adapter  │          │  JSON Adapter │
│  read / write  │          │  read / write │          │  read / write │
└───────────────┘          └───────────────┘          └───────────────┘
```

Each format requires only two operations:

- **Read**: Parse the format into the canonical object graph
- **Write**: Serialize the canonical object graph to the format

Any format can then convert to any other format by reading into the canonical form and writing out.

## Core Concepts

### The Canonical Object Graph

The canonical representation is a JavaScript object graph—the natural in-memory data structure for browser-based applications. This can be:

- A primitive value (string, number, boolean, null)
- An array of values
- An object with named properties
- Any nesting/combination of the above

This is deliberately identical to what JSON can represent, ensuring universal interoperability while leveraging JavaScript's native capabilities for manipulation.

### Schema: The Format-Agnostic Structure Definition

A **schema** describes the structure of the canonical object graph independently of any file format. It defines:

- What entities exist (e.g., "Order", "LineItem")
- What properties each entity has
- Property types and constraints
- Relationships between entities (nesting, references)

The schema does NOT describe:

- Where data lives in any particular file format
- How values are encoded in files
- File-specific details like cell addresses or column positions

This separation is crucial: the same schema can be populated from Excel, CSV, JSON, or any other supported format.

### Format Adapters: Bidirectional Converters

A **format adapter** handles the conversion between a specific file format and the canonical object graph. Each adapter provides:

- **Reader**: Given a file and a format mapping, produce an object graph
- **Writer**: Given an object graph and a format mapping, produce a file

Adapters are symmetric at the data level—anything that can be read can be written, and vice versa.

### Format Mapping: Binding Schema to Format

A **format mapping** describes how a schema maps to a specific file format:

- For Excel: which sheets, which cells, which table regions
- For CSV: which columns, what delimiters
- For JSON: which paths, what structure

The same schema can have multiple format mappings—one per format it needs to interoperate with.

### Preprocessing Transformations

Raw values extracted from files often need normalization before entering the canonical object graph:

- Date strings need parsing ("2024-01-15" → Date object)
- Numbers need locale handling ("1.234,56" → 1234.56)
- Codes need mapping (legacy codes → current codes)
- Strings need cleaning (trim whitespace, normalize case)

**Preprocessing transformations** are:

- Defined per-field in the format mapping
- Applied during read (after extraction, before graph construction)
- Format-specific (different sources may encode the same data differently)

These are distinct from object graph transformations (see below).

### Object Graph Transformations (Optional)

Sometimes the output structure differs from the input structure. **Object graph transformations** operate on the canonical representation itself:

- Restructuring (flatten nested data, or nest flat data)
- Enrichment (add computed fields, lookup reference data)
- Filtering (exclude certain records)
- Aggregation (summarize detail records)

This is conceptually similar to XSLT's role in XML processing: consume an input structure, apply transformations, produce an output structure (potentially conforming to a different schema).

For many use cases—including simple format conversion—no object graph transformation is needed. The same object graph read from the source is written to the target.

## Architectural Principles

### 1. Metadata-Driven (Blackbox Framework)

Tikrana is a *blackbox framework*: concrete applications are created through configuration, not code. Users declare:

- The schema (what structure to expect)
- The format mappings (how to read/write each format)
- Any preprocessing transformations

The framework handles the mechanics of reading, transforming, and writing.

### 2. Extensible Format Support

Adding a new format requires implementing a format adapter—a well-defined interface with read and write operations. The core framework is format-agnostic.

Initial formats:

- Excel (.xlsx, .xls)
- Delimited (CSV, TSV, custom separators)
- JSON
- ZIP archives (containing multiple files of other formats)

Future formats can be added without modifying the core.

### 3. Layered Transformation

Transformations are cleanly separated:

```
┌─────────────────────────────────────────────────────────────┐
│                     File (Source Format)                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│          Preprocessing (value-level, format-specific)       │
│          • Parse dates, numbers                             │
│          • Map codes                                        │
│          • Normalize strings                                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│               Canonical Object Graph (Input Schema)         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│          Object Graph Transformation (optional)             │
│          • Restructure                                      │
│          • Enrich                                           │
│          • Filter/Aggregate                                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Canonical Object Graph (Output Schema)         │
│              (may be same as input if no transformation)    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    File (Target Format)                     │
└─────────────────────────────────────────────────────────────┘
```

### 4. Browser-Native, Offline-Capable

Tikrana runs entirely in the browser with no server dependency. This enables:

- Distribution as a single HTML file
- Operation without network connectivity
- Data privacy (files never leave the user's machine)
- Zero installation

### 5. Visual Configuration

While schemas and mappings can be authored as JSON/JavaScript, Tikrana provides a visual editor for IT personnel and power users who understand data structures but may not write code. The editor:

- Guides schema construction
- Validates mappings against schemas
- Provides immediate feedback
- Generates valid configuration

## Domain Theory

### Frozen Spots (What Doesn't Change)

The core algorithm is invariant:

```
read(sourceFile, formatMapping, schema)
  → objectGraph
  → transform(objectGraph)  // optional
  → write(objectGraph, formatMapping, schema)
  → targetFile
```

This pipeline is the *frozen spot*—it never changes regardless of formats or schemas.

### Hot Spots (What Changes Per Application)

1. **Schema**: The structure being processed
2. **Format Mappings**: How schemas bind to specific formats
3. **Preprocessing Rules**: Value-level transformations during I/O
4. **Object Graph Transformations**: Structural transformations (when needed)
5. **Format Adapters**: The set of supported formats (extensible)

## Relationship to Prior Art

### XSLT

XSLT transforms XML documents by:

1. Parsing XML into an in-memory representation (the "infoset")
2. Applying transformation rules
3. Serializing the result (to XML, HTML, or text)

Tikrana follows the same pattern but:

- Uses JavaScript objects as the canonical representation (not XML DOM)
- Supports arbitrary input/output formats (not just XML)
- Targets browser execution (not server-side processing)

### ETL Tools

Enterprise ETL (Extract-Transform-Load) tools address similar problems but typically:

- Require server infrastructure
- Target database-to-database workflows
- Are heavyweight for simple format conversion

Tikrana is lightweight, client-side, and focused on file-to-file translation.

### Data Serialization Libraries

Libraries like Protocol Buffers, Avro, or MessagePack define schemas and serialize to binary formats. Tikrana differs by:

- Targeting human-readable formats (Excel, CSV, JSON)
- Emphasizing bidirectional conversion between formats
- Running in-browser without compilation

## Implementation Approach

### Technology Stack

- **Language**: TypeScript (for type safety and IDE support)
- **UI Framework**: Alpine.js (lightweight, declarative, no build step required for development)
- **Excel Parsing**: read-excel-file / SheetJS (browser-compatible)
- **ZIP Handling**: JSZip (browser-compatible)
- **Bundling**: Vite (for single-file distribution)

### Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Visual Config Editor                      │
│                       (Alpine.js)                            │
├─────────────────────────────────────────────────────────────┤
│                     Runtime Engine                           │
│                  (Pure TypeScript, no DOM)                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   Reader    │  │ Transformer │  │       Writer        │  │
│  │  Pipeline   │  │  Pipeline   │  │      Pipeline       │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                    Format Adapters                           │
│      Excel  │  Delimited  │   JSON   │   ZIP Archive        │
└─────────────────────────────────────────────────────────────┘
```

The runtime engine is framework-agnostic—it can be driven by the visual editor, a CLI, or programmatic API.

## Example: Order Processing

Consider converting purchase orders from Excel (human-authored) to ERP import format (ZIP of delimited files).

### Schema (what we're processing)

```typescript
type Order = {
  header: {
    orderNumber: string;
    orderDate: Date;
    dueDate: Date;
    customerCode: string;
  };
  lines: Array<{
    itemCode: string;
    quantity: number;
  }>;
}
```

### Excel Format Mapping (how to read)

```yaml
entity: Order
sheet: 0
header:
  orderNumber: { cell: "B3" }
  orderDate: { cell: "B7", preprocess: parseDate("YYYY-MM-DD") }
  customerCode: { cell: "B4" }
lines:
  origin: "A12"
  columns:
    itemCode: { header: "BARCODE", preprocess: mapCodes(legacyCodeTable) }
    quantity: { header: "QTY" }
```

### Delimited Format Mapping (how to write)

```yaml
entity: Order
files:
  - name: "header.txt"
    separator: "\t"
    fields: [orderNumber, orderDate, dueDate, customerCode]
  - name: "lines.txt"
    separator: "\t"
    source: lines
    fields: [orderNumber, lineNumber, itemCode, quantity]
```

### ZIP Output Mapping

```yaml
archive:
  filename: "order-${orderNumber}.zip"
  entries:
    - "header.txt"
    - "lines.txt"
```

The framework reads the Excel file using the Excel mapping, constructs the Order object graph, then writes it using the delimited mapping wrapped in a ZIP archive.

No transformation is needed—the same Order structure serves both input and output.

## Future Directions

### Additional Formats

- XML (with XPath-based mapping)
- YAML
- Fixed-width (legacy mainframe formats)
- Database (via SQL.js for browser-based SQLite)

### Advanced Transformations

- Declarative transformation language (for common patterns)
- JavaScript escape hatch (for complex logic)
- External data enrichment (REST API calls)

### Validation

- Schema-based validation of input data
- Constraint checking before write
- Error reporting with source location

### Templates

- Excel templates with formatting preserved
- Placeholder substitution in templates
- Conditional sections

---

## Summary

Tikrana is a declarative data translation framework based on:

1. **Lingua franca architecture**: A canonical JavaScript object graph as intermediate representation
2. **Bidirectional format adapters**: Each format implements read and write
3. **Schema-driven processing**: Structure is defined once, mapped to multiple formats
4. **Layered transformations**: Preprocessing (value-level) and object graph transformation (structural) are separate concerns
5. **Browser-native execution**: No server, offline-capable, distributable as single file

The framework enables non-programmers to configure data translations while providing extension points for developers when complex transformations are needed.
