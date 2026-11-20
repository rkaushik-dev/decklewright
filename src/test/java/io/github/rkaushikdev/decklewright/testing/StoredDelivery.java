package io.github.rkaushikdev.decklewright.testing;

import io.github.rkaushikdev.decklewright.codec.ImageFormat;
import io.github.rkaushikdev.decklewright.derive.DerivedFile;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A delivery exactly as earlier releases wrote it to disk, kept verbatim so every later release is
 * held to reading it.
 *
 * <p>Accession {@code g4181-1907}, three derivatives whose bytes are short ascii stand ins, the
 * sidecar, the provenance chain and the checksum file. The digests, sizes and chain links in the
 * texts were worked out independently of this code base, from the file bytes, with the standard
 * {@code sha256sum} and {@code md5sum} tools; the json is two space indented with the keys in the
 * order the writer puts them, and each file ends with a line feed.
 */
public final class StoredDelivery {

    /** The accession the delivery is of. */
    public static final String ACCESSION = "g4181-1907";

    /** When it was made, as every text records it. */
    public static final String AT = "2025-12-18T12:00:00.000Z";

    /** What made it. */
    public static final String AGENT = "decklewright 1.4.0";

    /** The digest of the whole delivery, over all six files. */
    public static final String SET_DIGEST = "7bfecb8eb3ff0d7eaa703ce7e404dce76e9963e77e0fffc604155ce44970c8e5";

    /** The sha256 of the master's bytes. */
    public static final String MASTER_SHA256 = "579a31a25af650401bf6dca0fb654b9daa06bc8dedd083f0ae7c0e22aff4cda3";

    /** The sha256 of the service copy's bytes. */
    public static final String SERVICE_SHA256 = "ec67c4e90b28081fa7374772c21255e3dabb079308aa7f1be8ba8d5eade35913";

    /** The sha256 of the thumbnail's bytes. */
    public static final String THUMB_SHA256 = "69e4903e237522ecb9862fda328bb1b18316d1787cade76622aae0d911e21d32";

    /** The sidecar, {@code sidecar.json}. */
    public static final String SIDECAR =
            """
            {
              "version": 2,
              "accession": "g4181-1907",
              "profile": {
                "name": "house",
                "title": "house style"
              },
              "created": "2025-12-18T12:00:00.000Z",
              "capture": {
                "pixels": {
                  "width": 300,
                  "height": 240
                },
                "layout": "rgb",
                "depth": 8,
                "skewDegrees": 0,
                "confidence": 0.986,
                "spreadDegrees": 0.0625,
                "target": true,
                "neatline": false,
                "scaleBar": true
              },
              "crop": {
                "rect": {
                  "x": 84.5,
                  "y": 70,
                  "width": 171,
                  "height": 120
                },
                "deskewDegrees": 0,
                "ppi": {
                  "x": 400,
                  "y": 400
                },
                "rules": [
                  "start",
                  "bleed"
                ],
                "overhangs": false
              },
              "derivatives": [
                {
                  "name": "master",
                  "file": "g4181-1907_master.png",
                  "format": "png",
                  "ppi": {
                    "x": 400,
                    "y": 400
                  },
                  "bytes": 13,
                  "digests": {
                    "sha256": "579a31a25af650401bf6dca0fb654b9daa06bc8dedd083f0ae7c0e22aff4cda3",
                    "md5": "acb3d59aded008677852fb0f28ab9756"
                  },
                  "notes": []
                },
                {
                  "name": "service",
                  "file": "g4181-1907_service.png",
                  "format": "png",
                  "ppi": {
                    "x": 200,
                    "y": 200
                  },
                  "bytes": 14,
                  "digests": {
                    "sha256": "ec67c4e90b28081fa7374772c21255e3dabb079308aa7f1be8ba8d5eade35913",
                    "md5": "bccab74061f599da60dddadd1a7646e6"
                  },
                  "notes": []
                },
                {
                  "name": "thumb",
                  "file": "g4181-1907_thumb.png",
                  "format": "png",
                  "ppi": {
                    "x": 37.5,
                    "y": 37.5
                  },
                  "bytes": 12,
                  "digests": {
                    "sha256": "69e4903e237522ecb9862fda328bb1b18316d1787cade76622aae0d911e21d32",
                    "md5": "9452eb46abcff3c3031402566a55bf31"
                  },
                  "notes": [
                    "the text chunks were not carried"
                  ]
                }
              ],
              "quality": {
                "stars": 3,
                "wanted": 3,
                "passed": true,
                "worst": "noise",
                "missing": [],
                "checks": [
                  {
                    "name": "sampling",
                    "ran": true,
                    "stars": 4,
                    "measured": 400,
                    "note": "400 ppi against 400"
                  },
                  {
                    "name": "noise",
                    "ran": false,
                    "stars": 0,
                    "measured": null,
                    "note": "no flat patch to measure"
                  }
                ]
              },
              "warnings": [
                "no neatline was asked for"
              ]
            }
            """;

    /** The provenance chain, {@code provenance.json}: seven events, each pointing at the one before. */
    public static final String PROVENANCE =
            """
            [
              {
                "type": "capture-read",
                "at": "2025-12-18T12:00:00.000Z",
                "agent": "decklewright 1.4.0",
                "detail": "the capture was read off the stand",
                "values": {
                  "warnings": 1
                },
                "previous": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
              },
              {
                "type": "sheet-found",
                "at": "2025-12-18T12:00:00.000Z",
                "agent": "decklewright 1.4.0",
                "detail": "the sheet was found on the bed",
                "values": {
                  "confidence": 0.986,
                  "skew": 0
                },
                "previous": "9047e68804cddb85d77441c0d6a91705c90a55913d6119c2d56e93f21c689470"
              },
              {
                "type": "crop-planned",
                "at": "2025-12-18T12:00:00.000Z",
                "agent": "decklewright 1.4.0",
                "detail": "the crop was planned by 2 rules",
                "values": {
                  "width": 171,
                  "height": 120
                },
                "previous": "4cf06a3984ed5d749d51778cd0c92064cab2448f8ed586b890640102c2f976fd"
              },
              {
                "type": "derivative-made",
                "at": "2025-12-18T12:00:00.000Z",
                "agent": "decklewright 1.4.0",
                "detail": "master was written as png",
                "values": {
                  "file": "g4181-1907_master.png"
                },
                "previous": "c46626ab93bd7b7104320696ff212841d159f66752407a2b9917a2bdbf7eab95"
              },
              {
                "type": "derivative-made",
                "at": "2025-12-18T12:00:00.000Z",
                "agent": "decklewright 1.4.0",
                "detail": "service was written as png",
                "values": {
                  "file": "g4181-1907_service.png"
                },
                "previous": "971eab0ee84d6e6320a3ec369b6bd4c47adf808816c27709f3fa67ffbb3f5fea"
              },
              {
                "type": "derivative-made",
                "at": "2025-12-18T12:00:00.000Z",
                "agent": "decklewright 1.4.0",
                "detail": "thumb was written as png",
                "values": {
                  "file": "g4181-1907_thumb.png"
                },
                "previous": "376dc7a8105b4b5e0c2e641c4e0bb2fa5c9b09fdaaddd592631099fdd28da6e2"
              },
              {
                "type": "quality-checked",
                "at": "2025-12-18T12:00:00.000Z",
                "agent": "decklewright 1.4.0",
                "detail": "graded at 3 of 4",
                "values": {
                  "stars": 3,
                  "passed": true
                },
                "previous": "2af7a33ea3fc6304b6fb111c5d469e64a3e85a01f5142806c4c9d9a9d4241809"
              }
            ]
            """;

    /** The digest of the chain, which is the digest of its last event. */
    public static final String CHAIN_DIGEST = "fcee9fc461b205d17629eb851630181cc2852c46ebc5c52a77253fca3fc42cd9";

    /** The checksum file, {@code sha256sums.txt}: the three images and nothing else. */
    public static final String SUMS =
            """
            579a31a25af650401bf6dca0fb654b9daa06bc8dedd083f0ae7c0e22aff4cda3  g4181-1907_master.png
            ec67c4e90b28081fa7374772c21255e3dabb079308aa7f1be8ba8d5eade35913  g4181-1907_service.png
            69e4903e237522ecb9862fda328bb1b18316d1787cade76622aae0d911e21d32  g4181-1907_thumb.png
            """;

    private StoredDelivery() {}

    /** The three derivatives, as the run that delivered them made them. */
    public static List<DerivedFile> files() {
        return List.of(
                new DerivedFile(
                        "master",
                        "g4181-1907_master.png",
                        utf8("master pixels"),
                        ImageFormat.PNG,
                        Sampling.of(400),
                        List.of()),
                new DerivedFile(
                        "service",
                        "g4181-1907_service.png",
                        utf8("service pixels"),
                        ImageFormat.PNG,
                        Sampling.of(200),
                        List.of()),
                new DerivedFile(
                        "thumb",
                        "g4181-1907_thumb.png",
                        utf8("thumb pixels"),
                        ImageFormat.PNG,
                        Sampling.of(37.5),
                        List.of("the text chunks were not carried")));
    }

    /** Every file of the delivery by name, in the order they were packed. */
    public static Map<String, byte[]> entries() {
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (DerivedFile file : files()) {
            out.put(file.fileName(), file.bytes());
        }
        out.put("sidecar.json", utf8(SIDECAR));
        out.put("provenance.json", utf8(PROVENANCE));
        out.put("sha256sums.txt", utf8(SUMS));
        return out;
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
