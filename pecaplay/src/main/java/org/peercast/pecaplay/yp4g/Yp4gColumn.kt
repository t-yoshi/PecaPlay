package org.peercast.pecaplay.yp4g

import org.unbescape.html.HtmlEscape

enum class Yp4gColumn(
       val convert: (String) -> Any = Converter.None,
       /**Long|String*/
       val type: Class<*> = String::class.java) {
    Name(Converter.UnescapeHtml), // #0
    Id(Converter.ChannelId),
    Ip,
    Url,
    Genre(Converter.UnescapeHtml),
    Description(Converter.Description),//#5
    Listeners(Converter.Number, Long::class.java),
    Relays(Converter.Number, Long::class.java),
    Bitrate(Converter.Number, Long::class.java),
    Type(Converter.UnescapeHtml),
    TrackArtist(Converter.UnescapeHtml),//#10
    TrackAlbum(Converter.UnescapeHtml),
    TrackTitle(Converter.UnescapeHtml),
    TrackContact(Converter.UnescapeHtml),
    NameUrl,
    Age, //#15
    Status(Converter.UnescapeHtml),
    Comment(Converter.UnescapeHtml),
    Direct,
    // __END_OF_index_txt num=19

    YpName,
    YpUrl; //#20

    companion object {
        private object Converter {
            val None: (String) -> String = {
                it
            }

            val UnescapeHtml: (String) -> String = {
                HtmlEscape.unescapeHtml(it).trim()
            }

            val Number: (String) -> Long = {
                requireNotNull(it.toLongOrNull()) { "'$it' is not number." }
            }

            val ChannelId: (String) -> String = {
                require(it.length == 32) { "channel-id length is not 32." }
                it
            }

            val Description: (String) -> String = {
                UnescapeHtml(it).replace(PAT_DESCRIPTION, "")
            }


            private val PAT_DESCRIPTION = Regex("""( - )?<(\dM(bps)? )?(Over|Open|Free)>$""")
        }
    }
}