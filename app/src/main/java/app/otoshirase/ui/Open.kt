package app.otoshirase.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.otoshirase.data.Artist
import app.otoshirase.data.MusicApps
import app.otoshirase.data.Release
import app.otoshirase.data.Store

fun releaseIntent(ctx: Context, r: Release): Intent? = musicIntent(ctx, "${r.artistName} ${r.title}", r.url)

fun artistIntent(ctx: Context, a: Artist): Intent? = musicIntent(ctx, a.name, a.url)

/**
 * よく使う音楽アプリの検索画面を開く。そのアプリが入っていない・URL を受け付けない場合は、
 * Apple Music の URL（Apple Music が無ければブラウザで開く）に戻す。
 * 通知とウィジェットは押した時点では例外を拾えないので、作る時点で開けるかを確かめる。
 */
private fun musicIntent(ctx: Context, query: String, appleUrl: String?): Intent? {
    val pkg = MusicApps.preferred(Store.state.value.appPlays)
    if (pkg != null && pkg != MusicApps.APPLE_MUSIC) {
        MusicApps.searchUrl(pkg, query)?.let { url ->
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (i.resolveActivity(ctx.packageManager) != null) return i
        }
    }
    return appleUrl?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}
