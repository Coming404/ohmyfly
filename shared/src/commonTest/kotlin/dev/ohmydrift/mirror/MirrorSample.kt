package dev.ohmydrift.mirror

import okio.ByteString.Companion.decodeBase64

/** CC0 github/gitignore fixed sample, only for hermetic tests; production always requests HTTP. */
internal object MirrorSample {
    val bytes: ByteArray get() = BASE64.decodeBase64()!!.toByteArray()
    private const val BASE64 =
        "IyBDb21waWxlZCBjbGFzcyBmaWxlCiouY2xhc3MKCiMgTG9nIGZpbGUKKi5sb2cKCiMgQmx1ZUog" +
        "ZmlsZXMKKi5jdHh0CgojIE1vYmlsZSBUb29scyBmb3IgSmF2YSAoSjJNRSkKLm10ai50bXAvCgoj" +
        "IFBhY2thZ2UgRmlsZXMgIwoqLmphcgoqLndhcgoqLm5hcgoqLmVhcgoqLnppcAoqLnRhci5negoq" +
        "LnJhcgoKIyB2aXJ0dWFsIG1hY2hpbmUgY3Jhc2ggbG9ncywgc2VlIGh0dHA6Ly93d3cuamF2YS5j" +
        "b20vZW4vZG93bmxvYWQvaGVscC9lcnJvcl9ob3RzcG90LnhtbApoc19lcnJfcGlkKgpyZXBsYXlf" +
        "cGlkKgoKIyBLb3RsaW4gR3JhZGxlIHBsdWdpbiBkYXRhLCBzZWUgaHR0cHM6Ly9rb3RsaW5sYW5n" +
        "Lm9yZy9kb2NzL3doYXRzbmV3MjAuaHRtbCNuZXctZGlyZWN0b3J5LWZvci1rb3RsaW4tZGF0YS1p" +
        "bi1ncmFkbGUtcHJvamVjdHMKLmtvdGxpbi8="
}
