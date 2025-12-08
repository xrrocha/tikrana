# Tikrana

**Tikrana** (Quechua: *transmutation*) is a browser-native, offline-capable framework for declarative data translation between arbitrary file formats.

## Vision

Convert data between formats—Excel, CSV, JSON, ZIP archives—through configuration rather than code. Define *what* to transform, not *how*.

```
Source File → Format Adapter (read) → Canonical Object Graph → Format Adapter (write) → Target File
```

## Key Principles

- **Lingua Franca Architecture**: A canonical JavaScript object graph as intermediate representation reduces n² converters to 2n adapters
- **Metadata-Driven**: Transformations defined through schemas and format mappings, not hand-written code
- **Browser-Native**: Runs entirely client-side—no server, works offline, distributable as a single HTML file
- **Privacy by Design**: Files never leave the user's machine

## Core Concepts

| Concept | Description |
|---------|-------------|
| **Schema** | Format-agnostic structure definition (entities, properties, relationships) |
| **Format Mapping** | How a schema binds to a specific file format (cells, columns, paths) |
| **Format Adapter** | Bidirectional converter: read format → object graph, object graph → write format |
| **Preprocessing** | Value-level transformations during I/O (date parsing, code mapping) |

## Use Case Example

Convert Excel purchase orders to ERP import files (ZIP containing delimited text):

1. Define the **schema**: Order with header fields and line items
2. Define the **Excel format mapping**: which cells contain header data, where the line items table starts
3. Define the **delimited format mapping**: field order, separators, file names
4. Run the transformation—no code required

## Technology Stack

- **Language**: TypeScript
- **Runtime**: Browser-only, offline-capable
- **Distribution**: Single HTML file (bundled)
- **Excel Parsing**: read-excel-file / SheetJS
- **ZIP Handling**: JSZip

## Status

🚧 **Architectural exploration phase** — not yet ready for production use.

## License

[AGPL-3.0](LICENSE)

## Etymology

*Tikrana* comes from Quechua, meaning "transmutation" or "the means of transformation"—capturing the essence of what the framework does: transmute data from one form to another.
