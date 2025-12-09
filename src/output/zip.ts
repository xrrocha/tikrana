/**
 * ZIP Output Module
 *
 * Generates ZIP files containing cabecera.txt and detalle.txt
 * and triggers browser download.
 */

import JSZip from 'jszip';

/**
 * Create a ZIP file containing header and detail content.
 */
export async function createZip(
  headerFilename: string,
  headerContent: string,
  detailFilename: string,
  detailContent: string
): Promise<Blob> {
  const zip = new JSZip();

  zip.file(headerFilename, headerContent);
  zip.file(detailFilename, detailContent);

  return zip.generateAsync({ type: 'blob' });
}

/**
 * Trigger a browser download of a Blob.
 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

/**
 * Create and download a ZIP file.
 */
export async function downloadZip(
  zipFilename: string,
  headerFilename: string,
  headerContent: string,
  detailFilename: string,
  detailContent: string
): Promise<void> {
  const blob = await createZip(headerFilename, headerContent, detailFilename, detailContent);
  downloadBlob(blob, zipFilename);
}
