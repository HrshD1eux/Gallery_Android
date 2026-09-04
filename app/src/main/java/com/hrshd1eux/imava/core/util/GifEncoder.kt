package com.hrshd1eux.imava.core.util

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.abs

/**
 * Pure Kotlin standard GIF89a animated GIF encoder.
 * Zero external dependencies, memory-efficient.
 */
class GifEncoder {
    private var width = 0
    private var height = 0
    private var delayMs = 100 // 10 fps default
    private var repeatCount = 0 // 0 = loop forever
    private var isFirstFrame = true
    private var out: OutputStream? = null

    fun setDelay(ms: Int) {
        delayMs = ms
    }

    fun setRepeat(repeat: Int) {
        repeatCount = repeat
    }

    fun start(outputStream: OutputStream): Boolean {
        out = outputStream
        try {
            writeString("GIF89a")
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun addFrame(bitmap: Bitmap): Boolean {
        val stream = out ?: return false
        try {
            width = bitmap.width
            height = bitmap.height

            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // Quantize to 256 colors
            val (palette, indexedPixels) = quantizeColors(pixels)

            if (isFirstFrame) {
                // Logical Screen Descriptor
                writeShort(width)
                writeShort(height)
                // Global Color Table Flag: 1, Color Resolution: 7 (8 bits), Sort: 0, Size: 7 (256 colors)
                stream.write(0xF7)
                stream.write(0) // Background Color Index
                stream.write(0) // Pixel Aspect Ratio
                stream.write(palette)

                // Netscape Loop Extension
                if (repeatCount >= 0) {
                    stream.write(0x21) // Extension Introducer
                    stream.write(0xFF) // Application Extension
                    stream.write(11)   // Block Size
                    writeString("NETSCAPE2.0")
                    stream.write(3)    // Sub-block Length
                    stream.write(1)    // Sub-block ID
                    writeShort(repeatCount)
                    stream.write(0)    // Block Terminator
                }
                isFirstFrame = false
            }

            // Graphic Control Extension
            stream.write(0x21) // Extension Introducer
            stream.write(0xF9) // Graphic Control Label
            stream.write(4)    // Block Size
            stream.write(0)    // Packed fields (no transparency, don't dispose)
            writeShort(delayMs / 10) // Delay time in hundredths of a second
            stream.write(0)    // Transparent Color Index
            stream.write(0)    // Block Terminator

            // Image Descriptor
            stream.write(0x2C) // Image Separator
            writeShort(0)      // Left
            writeShort(0)      // Top
            writeShort(width)
            writeShort(height)
            stream.write(0)    // Local Color Table Flag (using Global)

            // LZW Compress Image Data
            writeLzwData(indexedPixels, 8, stream)

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun finish(): Boolean {
        val stream = out ?: return false
        try {
            stream.write(0x3B) // GIF Trailer
            stream.flush()
            out = null
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun quantizeColors(pixels: IntArray): Pair<ByteArray, ByteArray> {
        val colorMap = HashMap<Int, Byte>(256)
        val paletteList = ArrayList<Int>(256)
        val indexed = ByteArray(pixels.size)

        // Sample colors uniformly
        for (i in pixels.indices) {
            val color = pixels[i]
            // Reduce bit depth to 5-5-5 for fast clustering
            val r = (color shr 16 and 0xFF) and 0xF8
            val g = (color shr 8 and 0xFF) and 0xF8
            val b = (color and 0xFF) and 0xF8
            val reduced = (r shl 16) or (g shl 8) or b

            var index = colorMap[reduced]
            if (index == null) {
                if (paletteList.size < 256) {
                    val idx = paletteList.size.toByte()
                    paletteList.add(reduced)
                    colorMap[reduced] = idx
                    index = idx
                } else {
                    // Find nearest color in palette
                    index = findNearestPaletteIndex(reduced, paletteList)
                }
            }
            indexed[i] = index
        }

        val paletteBytes = ByteArray(256 * 3)
        for (i in 0 until paletteList.size) {
            val c = paletteList[i]
            paletteBytes[i * 3] = (c shr 16 and 0xFF).toByte()
            paletteBytes[i * 3 + 1] = (c shr 8 and 0xFF).toByte()
            paletteBytes[i * 3 + 2] = (c and 0xFF).toByte()
        }

        return Pair(paletteBytes, indexed)
    }

    private fun findNearestPaletteIndex(color: Int, palette: List<Int>): Byte {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        var bestIdx = 0
        var bestDist = Int.MAX_VALUE

        for (i in palette.indices) {
            val palColor = palette[i]
            val pr = (palColor shr 16) and 0xFF
            val pg = (palColor shr 8) and 0xFF
            val pb = palColor and 0xFF
            val dist = abs(r - pr) + abs(g - pg) + abs(b - pb)
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
                if (dist == 0) break
            }
        }
        return bestIdx.toByte()
    }

    private fun writeLzwData(pixels: ByteArray, colorDepth: Int, out: OutputStream) {
        val initCodeSize = colorDepth.coerceAtLeast(2)
        out.write(initCodeSize)

        val clearCode = 1 shl initCodeSize
        val eoiCode = clearCode + 1
        var nextCode = eoiCode + 1
        var codeSize = initCodeSize + 1
        var codeMask = (1 shl codeSize) - 1

        val buffer = ByteArrayOutputStream()
        var curAccum = 0
        var curBits = 0

        fun writeBits(code: Int) {
            curAccum = curAccum or (code shl curBits)
            curBits += codeSize
            while (curBits >= 8) {
                buffer.write(curAccum and 0xFF)
                curAccum = curAccum shr 8
                curBits -= 8
            }
        }

        fun flushBits() {
            if (curBits > 0) {
                buffer.write(curAccum and 0xFF)
                curAccum = 0
                curBits = 0
            }
        }

        writeBits(clearCode)

        val prefixTable = ShortArray(5003) { -1 }
        val suffixTable = ByteArray(5003)
        val codeTable = ShortArray(5003) { -1 }

        var prefix = pixels[0].toInt() and 0xFF

        for (i in 1 until pixels.size) {
            val suffix = pixels[i].toInt() and 0xFF
            val hash = ((prefix shl 8) or suffix) % 5003
            var found = false
            var h = hash

            while (codeTable[h] != (-1).toShort()) {
                if (prefixTable[h].toInt() == prefix && suffixTable[h].toInt() and 0xFF == suffix) {
                    prefix = codeTable[h].toInt()
                    found = true
                    break
                }
                h = (h + 1) % 5003
            }

            if (!found) {
                writeBits(prefix)

                if (nextCode < 4096) {
                    prefixTable[h] = prefix.toShort()
                    suffixTable[h] = suffix.toByte()
                    codeTable[h] = nextCode.toShort()
                    nextCode++

                    if (nextCode > codeMask && codeSize < 12) {
                        codeSize++
                        codeMask = (1 shl codeSize) - 1
                    }
                } else {
                    writeBits(clearCode)
                    prefixTable.fill(-1)
                    codeTable.fill(-1)
                    nextCode = eoiCode + 1
                    codeSize = initCodeSize + 1
                    codeMask = (1 shl codeSize) - 1
                }
                prefix = suffix
            }
        }

        writeBits(prefix)
        writeBits(eoiCode)
        flushBits()

        // Write in 254-byte sub-blocks
        val compressed = buffer.toByteArray()
        var offset = 0
        while (offset < compressed.size) {
            val blockSize = minOf(254, compressed.size - offset)
            out.write(blockSize)
            out.write(compressed, offset, blockSize)
            offset += blockSize
        }
        out.write(0) // Sub-block terminator
    }

    private fun writeShort(value: Int) {
        val stream = out ?: return
        stream.write(value and 0xFF)
        stream.write((value shr 8) and 0xFF)
    }

    private fun writeString(str: String) {
        val stream = out ?: return
        for (ch in str) {
            stream.write(ch.code)
        }
    }
}
