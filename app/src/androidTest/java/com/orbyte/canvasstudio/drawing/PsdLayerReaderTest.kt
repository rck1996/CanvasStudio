package com.orbyte.canvasstudio.drawing

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.DeflaterOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PsdLayerReaderTest {
    @Test
    fun readsBottomFirstWithUnicodeOffsetsOpacityFlagsAndOwnedBitmaps() {
        val bottom = Record("Fondo", left = -1, channels = rgb(raw(255, 0), raw(0, 255), raw(0, 0)))
        val middle = Record("Vacía", right = 0, channels = emptyList())
        val top = Record(
            name = "Top",
            top = 1,
            bottom = 2,
            blend = "mul ",
            opacity = 128,
            clipping = 1,
            flags = 2,
            tags = bytes {
                tag("luni", bytes {
                    val name = "Óleo 🖌"
                    writeInt(name.length + 1)
                    write(name.toByteArray(Charsets.UTF_16BE))
                    writeShort(0)
                })
                tag("lspf", bytes { writeInt(1) })
            },
            // RGBA channels need not be stored in channel ID order.
            channels = listOf(-1 to raw(128, 255), 2 to raw(255, 0), 0 to raw(0, 255), 1 to raw(0, 255)),
        )
        val input = fixture(listOf(bottom, middle, top), negativeLayerCount = true)
        val imported = requireNotNull(PsdLayerReader.read(input.inputStream()))
        val bitmap = requireNotNull(imported.layers.last().bitmap)
        imported.use {
            assertEquals(3, it.width)
            assertEquals(2, it.height)
            assertFalse(it.hasEmbeddedProfile)
            assertEquals(listOf("Fondo", "Vacía", "Óleo 🖌"), it.layers.map(PsdLayerReader.Layer::name))
            assertEquals(-1, it.layers[0].left)
            assertEquals(0xffff0000.toInt(), it.layers[0].bitmap!!.getPixel(0, 0))
            assertNull(it.layers[1].bitmap)
            val layer = it.layers[2]
            assertEquals(1, layer.top)
            assertEquals(128f / 255, layer.opacity, 0f)
            assertEquals(LayerBlendMode.MULTIPLY, layer.blendMode)
            assertFalse(layer.visible)
            assertTrue(layer.clipping)
            assertTrue(layer.alphaLocked)
            assertEquals(0x800000ff.toInt(), bitmap.getPixel(0, 0))
            assertEquals(0xffffff00.toInt(), bitmap.getPixel(1, 0))
        }
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun decodesPackBitsLiteralRepeatAndNoOp() {
        val record = Record(
            right = 4,
            bottom = 2,
            channels = rgb(
                packBits(intArrayOf(3, 255, 0, 255, 0, 128), intArrayOf(253, 255)),
                raw(0, 0, 0, 0, 0, 0, 0, 0),
                raw(0, 255, 0, 255, 0, 0, 0, 0),
            ),
        )
        requireNotNull(PsdLayerReader.read(fixture(listOf(record)).inputStream())).use {
            val actual = IntArray(8)
            it.layers.single().bitmap!!.getPixels(actual, 0, 4, 0, 0, 4, 2)
            val red = 0xffff0000.toInt()
            val blue = 0xff0000ff.toInt()
            assertArrayEquals(intArrayOf(red, blue, red, blue, red, red, red, red), actual)
        }
    }

    @Test
    fun decodesZipAndZipPredictionPerRow() {
        val record = Record(
            bottom = 2,
            channels = rgb(
                zip(3, 250, 11, 3, 251), // [250, 5], [3, 254], wrapping at 256.
                zip(2, 128, 128, 0, 0),
                raw(0, 0, 255, 255),
            ),
        )
        requireNotNull(PsdLayerReader.read(fixture(listOf(record)).inputStream())).use {
            val bitmap = it.layers.single().bitmap!!
            val actual = IntArray(4)
            bitmap.getPixels(actual, 0, 2, 0, 0, 2, 2)
            assertArrayEquals(intArrayOf(0xfffa8000.toInt(), 0xff058000.toInt(), 0xff0300ff.toInt(), 0xfffe00ff.toInt()), actual)
        }
    }

    @Test
    fun onlyReturnsNullForValidCompositeWithoutLayers() {
        val data = fixture(emptyList())
        assertNull(PsdLayerReader.read(data.inputStream()))
        assertRejected(data.copyOf(data.size - 1), "truncado")
    }

    @Test
    fun reportsEmbeddedIccWithoutConvertingPixels() {
        val resources = bytes {
            writeBytes("8BIM")
            writeShort(0x040f)
            writeByte(2)
            writeBytes("IC")
            writeByte(0)
            section(byteArrayOf(1, 2, 3)) // Contents are intentionally not interpreted.
            writeByte(0)
        }
        val layers = listOf(Record(channels = rgb(raw(255, 0), raw(0, 0), raw(0, 0))))
        requireNotNull(PsdLayerReader.read(fixture(layers, resources = resources).inputStream())).use {
            assertTrue(it.hasEmbeddedProfile)
            assertEquals(0xffff0000.toInt(), it.layers.single().bitmap!!.getPixel(0, 0))
        }
        assertRejected(fixture(layers, resources = resources.copyOf(resources.size - 1)), "recursos de imagen")
    }

    @Test
    fun rejectsEditableMasksGroupsAndUnsupportedBlendModes() {
        assertRejected(fixture(listOf(Record(channels = listOf(-2 to raw())))), "máscaras editables")
        assertRejected(fixture(listOf(Record(mask = byteArrayOf(1)))), "máscaras editables")
        assertRejected(fixture(listOf(Record(tags = bytes { tag("lsct", bytes { writeInt(1) }) }))), "grupos")
        assertRejected(fixture(listOf(Record(tags = bytes { tag("SoLd", byteArrayOf()) }))), "SoLd")
        assertRejected(fixture(listOf(Record(blend = "diss"))), "Modo de fusión")
        assertRejected(fixture(listOf(Record(), Record(clipping = 1), Record(clipping = 1))), "cadena de recortes")
        assertRejected(fixture(emptyList(), globalTags = bytes { tag("Layr", byteArrayOf()) }), "Layr")
    }

    @Test
    fun rejectsMalformedLengthsDuplicateChannelsAndExpansionBeyondLayerSize() {
        val duplicate = Record(channels = listOf(0 to raw(0, 0), 0 to raw(0, 0)))
        assertRejected(fixture(listOf(duplicate)), "duplicados")
        val runsAcrossRow = Record(channels = rgb(packBits(intArrayOf(254, 255)), raw(0, 0), raw(0, 0)))
        assertRejected(fixture(listOf(runsAcrossRow)), "excede el ancho")
        val zipBomb = Record(channels = rgb(zip(2, 1, 2, 3), raw(0, 0), raw(0, 0)))
        assertRejected(fixture(listOf(zipBomb)), "más píxeles")
        val oversized = Record(right = 30_000, bottom = 30_000)
        assertRejected(fixture(listOf(oversized)), "límite de píxeles")
        val largeStack = List(2) { Record(right = 6_000, bottom = 4_000) }
        assertRejected(fixture(largeStack), "presupuesto de memoria")
        val invalidSection = fixture(listOf(Record())).copyOf()
        // Offset 34: layer/mask length. It must not jump past the reader's byte cap.
        repeat(4) { invalidSection[34 + it] = 0xff.toByte() }
        assertRejected(invalidSection, "capas y máscaras")
    }

    @Test
    fun leavesCallerStreamOpen() {
        var closed = false
        val input = object : ByteArrayInputStream(fixture(emptyList())) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        input.use {
            assertNull(PsdLayerReader.read(it))
            assertFalse(closed)
        }
        assertTrue(closed)
    }

    private fun assertRejected(data: ByteArray, message: String) {
        val error = assertThrows(IllegalArgumentException::class.java) { PsdLayerReader.read(data.inputStream()) }
        assertTrue(error.message, error.message.orEmpty().contains(message, ignoreCase = true))
    }

    private data class Record(
        val name: String = "Raster",
        val left: Int = 0,
        val top: Int = 0,
        val right: Int = left + 2,
        val bottom: Int = 1,
        val blend: String = "norm",
        val opacity: Int = 255,
        val clipping: Int = 0,
        val flags: Int = 0,
        val mask: ByteArray = byteArrayOf(),
        val tags: ByteArray = byteArrayOf(),
        val channels: List<Pair<Int, ByteArray>> = rgb(raw(0, 0), raw(0, 0), raw(0, 0)),
    )

    private fun fixture(
        records: List<Record>,
        negativeLayerCount: Boolean = false,
        globalTags: ByteArray = byteArrayOf(),
        resources: ByteArray = byteArrayOf(),
    ): ByteArray = bytes {
        writeBytes("8BPS")
        writeShort(1)
        write(ByteArray(6))
        writeShort(if (negativeLayerCount) 4 else 3)
        writeInt(2)
        writeInt(3)
        writeShort(8)
        writeShort(3)
        writeInt(0) // RGB mode data
        section(resources)
        section(if (records.isEmpty() && globalTags.isEmpty()) byteArrayOf() else bytes {
            section(if (records.isEmpty()) byteArrayOf() else bytes {
                writeShort(if (negativeLayerCount) -records.size else records.size)
                records.forEach { layer ->
                    writeInt(layer.top)
                    writeInt(layer.left)
                    writeInt(layer.bottom)
                    writeInt(layer.right)
                    writeShort(layer.channels.size)
                    layer.channels.forEach { (id, body) -> writeShort(id); writeInt(body.size) }
                    writeBytes("8BIM")
                    writeBytes(layer.blend)
                    writeByte(layer.opacity)
                    writeByte(layer.clipping)
                    writeByte(layer.flags)
                    writeByte(0)
                    section(bytes {
                        section(layer.mask)
                        writeInt(0) // Default blending ranges.
                        val name = layer.name.toByteArray(Charsets.ISO_8859_1)
                        writeByte(name.size)
                        write(name)
                        write(ByteArray((4 - (name.size + 1) % 4) % 4))
                        write(layer.tags)
                    })
                }
                records.forEach { layer -> layer.channels.forEach { (_, body) -> write(body) } }
                if (size() % 2 == 1) writeByte(0)
            })
            writeInt(0) // Global layer mask.
            write(globalTags)
        })
        writeShort(0)
        write(ByteArray(6 * if (negativeLayerCount) 4 else 3))
    }

    private companion object {
        fun bytes(write: DataOutputStream.() -> Unit): ByteArray {
            val out = ByteArrayOutputStream()
            DataOutputStream(out).use(write)
            return out.toByteArray()
        }

        fun DataOutputStream.section(body: ByteArray) {
            writeInt(body.size)
            write(body)
        }

        fun DataOutputStream.tag(key: String, body: ByteArray) {
            writeBytes("8BIM")
            writeBytes(key)
            section(body)
            if (body.size % 2 == 1) writeByte(0)
        }

        fun rgb(r: ByteArray, g: ByteArray, b: ByteArray) = listOf(0 to r, 1 to g, 2 to b)

        fun raw(vararg values: Int) = bytes {
            writeShort(0)
            values.forEach(::writeByte)
        }

        fun packBits(vararg rows: IntArray) = bytes {
            writeShort(1)
            rows.forEach { writeShort(it.size) }
            rows.forEach { row -> row.forEach(::writeByte) }
        }

        fun zip(compression: Int, vararg values: Int) = bytes {
            writeShort(compression)
            val out = ByteArrayOutputStream()
            DeflaterOutputStream(out).use { stream -> values.forEach { stream.write(it) } }
            write(out.toByteArray())
        }
    }
}
