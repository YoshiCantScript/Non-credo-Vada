package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

class CalcioStreamingProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://nopay2.info"
    override var name = "CalcioStreaming"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Live,

        )
 
  override val mainPage =
        mainPageOf(
            "$mainUrl/embe.php?id=1" to "Ultime Serie Tv",
            "$mainUrl/embe.php?id=2" to "Ultimi Film",
        )
 

override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val url = request.data.replace("number", page.toString())
        val soup = app.get(url, referer = url.substringBefore("page")).document
        val home = soup.select("article.item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

private fun Element.toSearchResult(): SearchResponse? {
        val title =
            this.selectFirst("img")?.attr("alt") ?: throw ErrorLoadingException("No Title found")
        val link =
            this.selectFirst("a")?.attr("href") ?: throw ErrorLoadingException("No Link found")
        val posterUrl = fixUrl(
            this.selectFirst("img")?.attr("src") ?: throw ErrorLoadingException("No Poster found")
        )
        val year = Regex("\\(([^)]*)\\)").find(
            title
        )?.groupValues?.getOrNull(1)?.toIntOrNull()
        return newMovieSearchResponse(
            title,
            link,
            TvType.TvSeries
        ) {
            this.year = year
            addPoster(posterUrl)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val body = FormBody.Builder()
            .addEncoded("do", "search")
            .addEncoded("subaction", "search")
            .addEncoded("story", query)
            .addEncoded("sortby", "news_read")
            .build()
        val doc = app.post(
            "$mainUrl/index.php",
            requestBody = body
        ).document

        return doc.select("div.card").mapNotNull { series ->
            series.toSearchResult()
        }
    }

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
