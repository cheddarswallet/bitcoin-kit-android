package io.cheddarswallet.litecoinkit

import io.cheddarswallet.bitcoincore.extensions.hexToByteArray
import io.cheddarswallet.bitcoincore.extensions.toHexString
import org.junit.Assert.assertEquals
import org.junit.Test

class ScryptHasherTest {

    @Test
    fun scryptHashing() {

        val inputHex = listOf(
                "020000004c1271c211717198227392b029a64a7971931d351b387bb80db027f270411e398a07046f7d4a08dd815412a8712f874a7ebf0507e3878bd24e20a3b73fd750a667d2f451eac7471b00de6659",
                "0200000011503ee6a855e900c00cfdd98f5f55fffeaee9b6bf55bea9b852d9de2ce35828e204eef76acfd36949ae56d1fbe81c1ac9c0209e6331ad56414f9072506a77f8c6faf551eac7471b00389d01",
                "02000000a72c8a177f523946f42f22c3e86b8023221b4105e8007e59e81f6beb013e29aaf635295cb9ac966213fb56e046dc71df5b3f7f67ceaeab24038e743f883aff1aaafaf551eac7471b0166249b",
                "010000007824bc3a8a1b4628485eee3024abd8626721f7f870f8ad4d2f33a27155167f6a4009d1285049603888fe85a84b6c803a53305a8d497965a5e896e1a00568359589faf551eac7471b0065434e",
                "0200000050bfd4e4a307a8cb6ef4aef69abc5c0f2d579648bd80d7733e1ccc3fbc90ed664a7f74006cb11bde87785f229ecd366c2d4e44432832580e0608c579e4cb76f383f7f551eac7471b00c36982"
        )

        val expectedHex = listOf(
                "00000000002bef4107f882f6115e0b01f348d21195dacd3582aa2dabd7985806",
                "00000000003a0d11bdd5eb634e08b7feddcfbbf228ed35d250daf19f1c88fc94",
                "00000000000b40f895f288e13244728a6c2d9d59d8aff29c65f8dd5114a8ca81",
                "00000000003007005891cd4923031e99d8e8d72f6e8e7edc6a86181897e105fe",
                "000000000018f0b426a4afc7130ccb47fa02af730d345b4fe7c7724d3800ec8c"
        )

        val hasher = ScryptHasher()

        inputHex.forEachIndexed { index, hexString ->
            val hex = hexString.hexToByteArray()
            val result = hasher.hash(hex).toHexString()

            assertEquals(expectedHex[index], result)
        }

    }

}
