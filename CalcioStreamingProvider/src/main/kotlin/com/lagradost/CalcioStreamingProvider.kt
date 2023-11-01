package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

class CalcioStreamingProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://nopay2.info/embe.php?id="
    override var name = "CalcioStreaming"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Live,

        )
 
  override val mainPage =
        mainPageOf(
            "$mainUrl1" to "Ultime Serie Tv",
            "$mainUrl2" to "Ultimi Film",
        )
 


    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document
        val poster =  fixUrl(document.select("#title-single > div").attr("style").substringAfter("url(").substringBeforeLast(")"))
        val Matchstart = document.select("div.info-wrap > div").textNodes().joinToString("").trim()
        return LiveStreamLoadResponse(
            document.selectFirst(" div.info-t > h1")!!.text(),
            url,
            this.name,
            url,
            poster,
            plot = Matchstart
        )
    }

    private fun matchFound(document: Document) : Boolean {
        return Regex(""""((.|\n)*?).";""").containsMatchIn(
            getAndUnpack(
                document.toString()
            ))
    }

    private fun getUrl(document: Document):String{
        return Regex(""""((.|\n)*?).";""").find(
            getAndUnpack(
                document.toString()
            ))!!.value.replace("""src="""", "").replace(""""""", "").replace(";", "")
    }

    private suspend fun extractVideoLinks(
        url: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        document.select("button.btn").forEach { button ->
            var link = button.attr("data-link")
            var oldLink = link
            var videoNotFound = true
            while (videoNotFound) {
                val doc = app.get(link).document
                link = doc.selectFirst("iframe")?.attr("src") ?: break
                val newpage = app.get(fixUrl(link), referer = oldLink).document
                oldLink = link
                if (newpage.select("script").size >= 6 && matchFound(newpage)){
                    videoNotFound = false
                    callback(
                        ExtractorLink(
                            this.name,
                            button.text(),
                            getUrl(newpage),
                            fixUrl(link),
                            quality = 0,
                            true
                        )
                    )
                }
            }
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        extractVideoLinks(data, callback)

        return true
    }
}
