package com.arrowdrop.game

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * One rewarded ad, kept loaded so the out-of-lives card can offer a life straight away.
 *
 * The reward is only reported once the player has watched far enough for AdMob to pay out, and a
 * fresh ad is requested as soon as the last one closes. Loads are throttled so a device with no
 * connection (or no Google Play services) does not retry in a loop.
 */
class RewardedLives(context: Context) {

    private val appContext = context.applicationContext

    private var ad: RewardedAd? = null
    private var loading = false
    private var nextAttemptAt = 0L
    private var started = false

    /** True while an ad is loaded and can be shown. */
    val ready: Boolean get() = ad != null

    /** Called on the main thread whenever [ready] may have changed. */
    var onChanged: (() -> Unit)? = null

    /** Starts loading an ad, unless one is already loaded or the retry delay has not elapsed. */
    fun preload() {
        if (ad != null || loading) return
        val now = SystemClock.elapsedRealtime()
        if (now < nextAttemptAt) return
        if (!started) {
            started = true
            MobileAds.initialize(appContext)
        }
        loading = true
        RewardedAd.load(
            appContext,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    loading = false
                    ad = loaded
                    onChanged?.invoke()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    ad = null
                    nextAttemptAt = SystemClock.elapsedRealtime() + RETRY_MS
                    onChanged?.invoke()
                }
            }
        )
    }

    /**
     * Shows the loaded ad and calls [onReward] if the player earns it.
     * Returns false when no ad is ready, in which case a new load is started.
     */
    fun show(activity: Activity, onReward: () -> Unit): Boolean {
        val current = ad
        if (current == null) {
            preload()
            return false
        }
        ad = null
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload()
                onChanged?.invoke()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preload()
                onChanged?.invoke()
            }
        }
        current.show(activity) { onReward() }
        onChanged?.invoke()
        return true
    }

    companion object {
        /** Google's official test rewarded ad unit. Replace with your own before publishing. */
        const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

        private const val RETRY_MS = 30_000L
    }
}
