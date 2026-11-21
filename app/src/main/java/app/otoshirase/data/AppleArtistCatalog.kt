package app.otoshirase.data

/** ArtistCatalog を iTunes Search API で実装したもの */
object AppleArtistCatalog : ArtistCatalog {
    override suspend fun searchArtists(term: String): List<CatalogArtist> =
        AppleCatalog.searchArtists(term).map {
            CatalogArtist(it.artistId!!, it.artistName.orEmpty(), it.primaryGenreName, it.artistLinkUrl?.substringBefore("?uo="))
        }
}
