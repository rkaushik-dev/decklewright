/**
 * The codec layer as the rest of the tree sees it.
 *
 * Above this line nothing imports a format by name. The intake stage calls
 * readImage, the derivative stage calls writeImage, and the verify command
 * calls inspect. The individual decoders are here for the tests and for the
 * one or two places that genuinely need a chunk list.
 */

export {
  CODEC_NAMES,
  codecForBytes,
  codecForPath,
  codecNamed,
  extensionFor,
  formatOf,
  probeImage,
  readImage,
  writeImage,
} from './registry.js';

export { classify, describe, inspect, inspectDeep, isReadable, triage } from './inspect.js';

export {
  asciiBytes,
  concatBytes,
  crc32,
  latin1Bytes,
  readAscii,
  readAsciiZ,
  readUint16BE,
  readUint32BE,
  uint32BE,
  writeUint32BE,
} from './bytes.js';

export { looksLikePnm, probePnm, readPnm, readPnmHeader, writePnm } from './pnm.js';

export {
  PNG_SIGNATURE,
  assemble,
  buildChunk,
  chunkFlags,
  chunksOfType,
  findChunk,
  looksLikePng,
  splitChunks,
} from './png/chunks.js';

export { decodePng, probePng, readPhysical, readText } from './png/decode.js';
export { encodePng, physicalChunk, textChunk } from './png/encode.js';

export {
  MASTER_KEEP,
  SERVICE_KEEP,
  checkChunkPolicy,
  chunkTypes,
  imageDataOf,
  keepOnly,
  readTime,
  stripAncillary,
  timeChunk,
  withMetadata,
} from './png/rewrite.js';

export { probeTiff, readTiff, resolutionOf } from './tiff/reader.js';
export {
  COMPRESSION,
  PHOTOMETRIC,
  RESOLUTION_UNIT,
  looksLikeTiff,
  readTiffHeader,
} from './tiff/tags.js';
export { lzwDecode, lzwEncode } from './tiff/lzw.js';
export { packBits, packedLength, unpackBits } from './tiff/packbits.js';
