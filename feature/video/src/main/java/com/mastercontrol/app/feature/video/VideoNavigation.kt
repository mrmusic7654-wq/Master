package com.mastercontrol.app.feature.video

/**
 * Route contract for video details.
 *
 * The permanent video ID is the only argument: it never changes, so it is a
 * stable deep-link key (a notification or a search result can open a video
 * directly with it).
 */
object VideoNavigation {
    const val ARG_VIDEO_ID = "videoId"
    const val ROUTE = "video/{$ARG_VIDEO_ID}"

    fun createRoute(videoId: String): String = "video/$videoId"
}
